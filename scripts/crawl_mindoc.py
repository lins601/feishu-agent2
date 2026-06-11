#!/usr/bin/env python3
"""
MinDoc 文档爬取、Markdown 转换与 WISE 文件入库脚本

符合 PDR V8.1 US-05 规范：
- 公司网络环境下访问 MinDoc URL
- 从入口页解析文档链接
- 逐篇爬取文档正文
- 转换为 Markdown 文件（含 Front Matter）
- 生成 normalized_url / url_hash / md_hash
- 与文档映射表比对，判断新增/更新/跳过
- 通过 WISE File API 上传 Markdown 文件
- 轮询 parse_status

用法:
    # 仅爬取生成 Markdown 文件
    python3 scripts/crawl_mindoc.py --project MFG --entry "https://docs.cvte.com/docs/mfg/mfg-1gabr49bn55co" --output knowledge-base/mfg/

    # 爬取并上传到 WISE
    python3 scripts/crawl_mindoc.py --project MFG --entry "..." --wise-base-url "..." --wise-token "..." --wise-kb-id "..."

    # 带 Cookie 认证
    python3 scripts/crawl_mindoc.py --project MFG --entry "..." --cookie "mindoc_id=xxx"

    # 从配置表读取（飞书多维表格格式）
    python3 scripts/crawl_mindoc.py --config feishu_config.json
"""

import argparse
import base64
import hashlib
import io
import json
import logging
import os
import re
import sys
import time
import urllib.request
import urllib.parse
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path
from typing import Optional

# PIL/Pillow 用于图片压缩（减少 base64 体积，避免 WISE 解析超长行）
try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("crawl_mindoc")

# ─── 常量 ─────────────────────────────────────────────────
PDR_VERSION = "V8.1"
SOURCE = "MinDoc"
REQUEST_INTERVAL = 0.3  # 请求间隔（秒）
MAX_RETRIES = 3
DEFAULT_OUTPUT = "knowledge-base"
MAPPING_FILE = "mindoc_document_mapping.json"
PARSE_POLL_INTERVAL = 5  # parse_status 轮询间隔（秒）
PARSE_POLL_TIMEOUT = 300  # parse_status 轮询超时（秒）


# ═══════════════════════════════════════════════════════════════
# 第一部分: URL 规范化（符合 PDR 规范）
# ═══════════════════════════════════════════════════════════════

def normalize_url(source_url: str) -> str:
    """
    URL 规范化规则（PDR V8.1 §US-05）:
    1. 去除 utm_*、from、timestamp、token 等无意义 query 参数
    2. 去除 #section 锚点
    3. 去除路径末尾多余 /
    4. 域名统一小写
    5. URL Decode 后再统一编码
    """
    parsed = urllib.parse.urlparse(source_url)

    # 域名小写
    netloc = parsed.netloc.lower()

    # 去除路径末尾斜杠
    path = parsed.path.rstrip("/") or "/"

    # 过滤无意义 query 参数
    ignored_params = {
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "from", "timestamp", "token", "t", "v", "_",
    }
    query_params = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
    kept_params = []
    for key in sorted(query_params.keys()):
        if key.lower() not in ignored_params:
            for val in query_params[key]:
                kept_params.append((key, val))
    query = urllib.parse.urlencode(kept_params) if kept_params else ""

    # 重新拼接（去除 fragment）
    normalized = urllib.parse.ParseResult(
        scheme=parsed.scheme,
        netloc=netloc,
        path=path,
        params="",
        query=query,
        fragment="",
    ).geturl()

    return normalized


def compute_url_hash(normalized_url: str) -> str:
    """SHA256(normalized_url) 前 12 位"""
    return hashlib.sha256(normalized_url.encode("utf-8")).hexdigest()[:12]


def compute_md_hash(markdown_content: str) -> str:
    """SHA256(convert_markdown) 前 8 位"""
    return hashlib.sha256(markdown_content.encode("utf-8")).hexdigest()[:8]


def make_document_key(project_name: str, url_hash: str) -> str:
    """document_key = project_name + '_' + url_hash"""
    return f"{project_name}_{url_hash}"


# ═══════════════════════════════════════════════════════════════
# 第二部分: 图片下载与 Base64 编码
# ═══════════════════════════════════════════════════════════════

# 图片缓存目录
IMAGE_CACHE_DIR = None
_image_cache = {}

def _detect_mime_from_bytes(data: bytes) -> str:
    """根据文件头检测 MIME 类型，未知格式返回 None"""
    if data[:4] == b'\x89PNG':
        return "image/png"
    elif data[:2] == b'\xff\xd8':
        return "image/jpeg"
    elif data[:4] == b'GIF8':
        return "image/gif"
    elif data[:4] == b'RIFF' and len(data) > 12 and data[8:12] == b'WEBP':
        return "image/webp"
    elif b'<svg' in data[:200]:
        return "image/svg+xml"
    elif data[:2] == b'BM':
        return "image/bmp"
    return None


def _parse_content_type(header_value: str) -> str:
    """从 HTTP Content-Type 头中提取 MIME 类型（去掉 charset 等参数）"""
    if not header_value:
        return ""
    return header_value.split(";")[0].strip().lower()


# 图片压缩配置
IMAGE_MAX_WIDTH = 1920           # 最大宽度（像素）
IMAGE_MAX_HEIGHT = 1080          # 最大高度（像素）
IMAGE_JPEG_QUALITY = 85          # JPEG 压缩质量（1-100）
IMAGE_COMPRESS_THRESHOLD = 200 * 1024  # 超过 200KB 的图片尝试压缩
MAX_BASE64_LINE_LEN = 76         # base64 每行字符数（避免 WISE 解析超长行）


# 最大图片大小限制（超过此大小不转 base64，保留原始 URL）
MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024  # 5MB


def compress_image(img_data: bytes, mime: str) -> tuple[bytes, str]:
    """
    压缩图片数据，减小文件体积。
    返回 (compressed_data, new_mime)。

    规则:
    - 超过 IMAGE_COMPRESS_THRESHOLD 的图片才压缩
    - 缩放到最大 IMAGE_MAX_WIDTH x IMAGE_MAX_HEIGHT
    - 照片类图片（JPEG）使用 JPEG 压缩
    - PNG 图片保留格式但优化尺寸
    - 如果 PIL 不可用或压缩失败，返回原始数据
    """
    if not HAS_PIL:
        return img_data, mime

    if len(img_data) < IMAGE_COMPRESS_THRESHOLD:
        return img_data, mime

    # GIF 和 SVG 不压缩（可能包含动画/矢量）
    if mime in ("image/gif", "image/svg+xml"):
        return img_data, mime

    try:
        img = Image.open(io.BytesIO(img_data))

        # 计算缩放比例
        orig_w, orig_h = img.size
        if orig_w <= IMAGE_MAX_WIDTH and orig_h <= IMAGE_MAX_HEIGHT:
            # 不需要缩放，只做格式优化
            pass
        else:
            ratio = min(IMAGE_MAX_WIDTH / orig_w, IMAGE_MAX_HEIGHT / orig_h)
            new_w = int(orig_w * ratio)
            new_h = int(orig_h * ratio)
            img = img.resize((new_w, new_h), Image.LANCZOS)

        # 转换 CMYK/RGBA 为 RGB（JPEG 不支持透明通道）
        if img.mode in ("RGBA", "P", "CMYK"):
            if mime == "image/jpeg" or (mime == "image/png" and img.mode == "CMYK"):
                bg = Image.new("RGB", img.size, (255, 255, 255))
                if img.mode == "RGBA":
                    bg.paste(img, mask=img.split()[3])
                else:
                    bg.paste(img)
                img = bg

        buf = io.BytesIO()
        if mime == "image/jpeg":
            img.save(buf, format="JPEG", quality=IMAGE_JPEG_QUALITY, optimize=True)
            return buf.getvalue(), "image/jpeg"
        elif mime == "image/png":
            # PNG: 如果压缩后反而更大，转为 JPEG
            img.save(buf, format="PNG", optimize=True)
            png_data = buf.getvalue()
            if len(png_data) > IMAGE_COMPRESS_THRESHOLD * 2:
                # PNG 太大，尝试转 JPEG
                buf2 = io.BytesIO()
                if img.mode == "RGBA":
                    bg = Image.new("RGB", img.size, (255, 255, 255))
                    bg.paste(img, mask=img.split()[3])
                    bg.save(buf2, format="JPEG", quality=IMAGE_JPEG_QUALITY, optimize=True)
                else:
                    img.save(buf2, format="JPEG", quality=IMAGE_JPEG_QUALITY, optimize=True)
                jpeg_data = buf2.getvalue()
                if len(jpeg_data) < len(png_data):
                    return jpeg_data, "image/jpeg"
            return png_data, "image/png"
        else:
            # 其他格式统一转 JPEG
            if img.mode != "RGB":
                img = img.convert("RGB")
            img.save(buf, format="JPEG", quality=IMAGE_JPEG_QUALITY, optimize=True)
            return buf.getvalue(), "image/jpeg"

    except Exception as e:
        log.warning(f"图片压缩失败 ({mime}, {len(img_data)} bytes): {e}")
        return img_data, mime


def download_image_to_base64(img_url: str, cache_dir: str = None,
                              auth_cookie: str = "", page_url: str = "") -> str:
    """
    下载图片并转换为 base64 data URI。
    支持缓存（避免重复下载相同 URL 的图片）。

    参数:
        img_url: 图片 URL
        cache_dir: 磁盘缓存目录
        auth_cookie: MinDoc 认证 Cookie（图片可能和页面一样需要认证访问）
        page_url: 页面 URL，用于设置正确的 Referer 头

    返回:
        data URI 字符串如 "data:image/png;base64,iVBOR..."
        或原始 URL（下载失败 / 超过大小限制时）
    """
    global _image_cache

    # 缓存检查（内存）
    if img_url in _image_cache:
        return _image_cache[img_url]

    # 缓存检查（磁盘）
    cache_dir = cache_dir or IMAGE_CACHE_DIR
    if cache_dir:
        cache_key = hashlib.md5(img_url.encode()).hexdigest()[:16]
        cache_file = os.path.join(cache_dir, f"{cache_key}.json")
        if os.path.exists(cache_file):
            try:
                with open(cache_file, "r") as f:
                    cached = json.load(f)
                    data_uri = cached["data_uri"]
                    _image_cache[img_url] = data_uri
                    return data_uri
            except (json.JSONDecodeError, KeyError) as e:
                log.debug(f"缓存文件损坏 {cache_file}: {e}")

    try:
        headers = {
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                          "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "image/webp,image/apng,image/*,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9",
        }
        if auth_cookie:
            headers["Cookie"] = auth_cookie

        # 使用页面 URL 作为 Referer（而不是从图片 URL 截取）
        referer = page_url or (img_url.rsplit("/", 1)[0] if "/" in img_url else img_url)
        headers["Referer"] = referer

        req = urllib.request.Request(img_url, headers=headers)
        with urllib.request.urlopen(req, timeout=30) as resp:
            img_data = resp.read()

            # 检查 HTTP Content-Type
            content_type = _parse_content_type(resp.headers.get("Content-Type", ""))
            status_code = resp.status

        if not img_data:
            log.warning(f"图片下载返回空数据: {img_url}")
            _image_cache[img_url] = img_url
            return img_url

        # 大图片跳过 base64，保留原始 URL（避免生成的文件过大）
        if len(img_data) > MAX_IMAGE_SIZE_BYTES:
            log.info(f"图片过大 ({len(img_data) / 1024 / 1024:.1f}MB)，跳过 base64: {img_url}")
            _image_cache[img_url] = img_url
            return img_url

        # MIME 类型检测: 优先用 magic bytes，回退到 Content-Type 头
        detected_mime = _detect_mime_from_bytes(img_data)
        if detected_mime is None:
            # magic bytes 无法识别，使用 Content-Type
            if content_type.startswith("image/"):
                detected_mime = content_type
            else:
                # 既不是已知图片格式，Content-Type 也不是 image/*
                # 很可能是 HTML 错误页面或认证跳转
                log.warning(
                    f"图片内容非图片格式 (Content-Type={content_type}, "
                    f"前20字节={img_data[:20]}): {img_url}"
                )
                _image_cache[img_url] = img_url
                return img_url
        else:
            # magic bytes 检测到图片格式，但与 Content-Type 不一致时打日志
            if content_type and not content_type.startswith("image/") and content_type != "application/octet-stream":
                log.warning(f"Content-Type 异常 ({content_type})，但 magic bytes 为 {detected_mime}: {img_url}")

        # 图片压缩（减少 base64 体积）
        img_data, detected_mime = compress_image(img_data, detected_mime)

        b64 = base64.b64encode(img_data).decode("ascii")
        data_uri = f"data:{detected_mime};base64,{b64}"

        # 磁盘缓存
        if cache_dir:
            os.makedirs(cache_dir, exist_ok=True)
            try:
                with open(cache_file, "w") as f:
                    json.dump({
                        "url": img_url,
                        "data_uri": data_uri,
                        "mime": detected_mime,
                        "size": len(img_data),
                    }, f)
            except OSError as e:
                log.debug(f"写入缓存失败 {cache_file}: {e}")

        _image_cache[img_url] = data_uri
        return data_uri

    except urllib.error.HTTPError as e:
        log.warning(f"图片下载 HTTP 错误 {e.code}: {img_url}")
        _image_cache[img_url] = img_url
        return img_url
    except urllib.error.URLError as e:
        log.warning(f"图片下载 URL 错误: {img_url} ({e.reason})")
        _image_cache[img_url] = img_url
        return img_url
    except Exception as e:
        log.warning(f"图片下载异常: {img_url} ({e})")
        _image_cache[img_url] = img_url
        return img_url


# ═══════════════════════════════════════════════════════════════
# 第三部分: HTML 内容提取与 Markdown 转换
# ═══════════════════════════════════════════════════════════════

class HTMLToMarkdownConverter(HTMLParser):
    """将 HTML 正文转换为结构化的 Markdown 文本，图片自动转为 base64"""

    def __init__(self, image_cache_dir: str = None, base_url: str = "",
                 auth_cookie: str = ""):
        super().__init__()
        self._result = []
        self._skip_tags = {"script", "style", "head", "nav", "footer"}
        self._skip_depth = 0
        self._in_skip = False
        self._heading_tags = {"h1", "h2", "h3", "h4", "h5", "h6"}
        self._list_tags = {"ul", "ol"}
        self._in_table = False
        self._table_rows = []
        self._current_row = []
        self._current_cell = []
        self._in_cell = False
        self._in_pre = False
        self._in_a = False
        self._link_text = ""
        self._link_href = ""
        self._previous_tag = None
        self._img_alt = ""
        self._image_cache_dir = image_cache_dir
        self._base_url = base_url
        self._auth_cookie = auth_cookie

    def handle_starttag(self, tag, attrs):
        attrs_dict = dict(attrs)
        if tag in self._skip_tags:
            self._in_skip = True
            self._skip_depth = 1
            return
        if self._in_skip:
            self._skip_depth += 1
            return

        if tag == "br":
            self._result.append("\n")
        elif tag == "hr":
            self._result.append("\n---\n")
        elif tag in self._heading_tags:
            level = int(tag[1])
            self._result.append("\n" + "#" * level + " ")
        elif tag == "li":
            self._result.append("\n- ")
        elif tag == "p":
            if self._previous_tag and self._previous_tag not in ("li", "p", "h1", "h2", "h3", "h4", "h5", "h6"):
                self._result.append("\n\n")
        elif tag == "table":
            self._in_table = True
            self._table_rows = []
        elif tag == "tr":
            self._current_row = []
        elif tag in ("td", "th"):
            self._in_cell = True
            self._current_cell = []
        elif tag == "code":
            if not self._in_pre:
                self._result.append("`")
        elif tag == "pre":
            self._in_pre = True
            self._result.append("\n```\n")
        elif tag == "strong" or tag == "b":
            self._result.append("**")
        elif tag == "em" or tag == "i":
            self._result.append("*")
        elif tag == "a":
            self._in_a = True
            self._link_text = ""
            self._link_href = attrs_dict.get("href", "")
        elif tag == "img":
            alt = attrs_dict.get("alt", "")
            # 优先使用懒加载属性中的真实 URL（某些 MinDoc 主题使用 data-original/data-src）
            src = (
                attrs_dict.get("data-original")
                or attrs_dict.get("data-src")
                or attrs_dict.get("data-lazy-src")
                or attrs_dict.get("src", "")
            )
            self._img_alt = alt
            if src:
                # 相对路径拼接为绝对 URL（包括协议相对路径 //cdn.example.com/...）
                if src.startswith("//"):
                    src = "https:" + src
                elif not src.startswith(("http://", "https://", "data:")):
                    src = urllib.parse.urljoin(self._base_url, src)
                # 将图片下载并转为 base64 data URI
                data_uri = download_image_to_base64(
                    src, self._image_cache_dir,
                    self._auth_cookie, page_url=self._base_url,
                )
                self._result.append(f"\n![{alt}]({data_uri})\n")
            else:
                self._result.append(f"\n[{alt}]\n")

        self._previous_tag = tag

    def handle_endtag(self, tag):
        if self._in_skip:
            self._skip_depth -= 1
            if self._skip_depth <= 0:
                self._in_skip = False
                self._skip_depth = 0
            return

        if tag == "code" and not self._in_pre:
            self._result.append("`")
        elif tag == "pre":
            self._in_pre = False
            self._result.append("\n```\n")
        elif tag == "strong" or tag == "b":
            self._result.append("**")
        elif tag == "em" or tag == "i":
            self._result.append("*")
        elif tag == "a":
            self._in_a = False
            if self._link_href and self._link_text:
                self._result.append(f"[{self._link_text}]({self._link_href})")
            elif self._link_text:
                self._result.append(self._link_text)
        elif tag in ("td", "th"):
            self._in_cell = False
            self._current_row.append("".join(self._current_cell).strip())
        elif tag == "tr":
            self._table_rows.append(self._current_row)
        elif tag == "table":
            self._in_table = False
            for ri, row in enumerate(self._table_rows):
                self._result.append("\n| " + " | ".join(row) + " |")
                if ri == 0:
                    self._result.append("\n| " + " | ".join(["---"] * len(row)) + " |")
            self._result.append("\n")
        elif tag in self._heading_tags:
            self._result.append("\n")

    def handle_data(self, data):
        if self._in_skip:
            return
        if self._in_cell:
            self._current_cell.append(data)
        elif self._in_a:
            self._link_text += data
        else:
            self._result.append(data)


def extract_title(html: str) -> str:
    """从 HTML 中提取页面标题"""
    match = re.search(r"<h1[^>]*id=\"article-title\"[^>]*>(.*?)</h1>", html, re.DOTALL)
    if match:
        return re.sub(r"<[^>]+>", "", match.group(1)).strip()
    match = re.search(r"<title>(.*?)</title>", html)
    if match:
        return match.group(1).replace(" - Powered by MinDoc", "").strip()
    return ""


def find_sidebar_links(html: str) -> list[dict]:
    """
    从侧边栏 HTML 提取文档链接（保留层级关系）
    返回 [{url, title, doc_id, indent_level}, ...]
    """
    links = re.findall(
        r'<li[^>]*id="(\d+)"[^>]*>(.*?)<a href="([^"]+)"[^>]*title="([^"]*)"[^>]*>(.*?)</a>',
        html, re.DOTALL,
    )
    results = []
    seen = set()
    for lid, li_html, url, title, link_text in links:
        if url in seen:
            continue
        seen.add(url)
        indent = 0
        # 计算层级: 每层 ul 为一级
        indent = li_html.count("<li") - 1  # 不是最佳方式，简单近似
        doc_id = url.rstrip("/").split("/")[-1] if "/" in url else ""
        results.append({
            "url": url.strip(),
            "title": title.strip() or link_text.strip(),
            "doc_id": doc_id,
        })
    return results


def html_to_markdown(html: str, image_cache_dir: str = None,
                     base_url: str = "", auth_cookie: str = "") -> str:
    """HTML → 结构化 Markdown（图片自动转为 base64 内嵌）"""
    # 清理 MinDoc 特有元素
    html = re.sub(r'<div class="wiki-bottom">.*?</div>', "", html, flags=re.DOTALL)
    html = re.sub(r'<div class="jump-top">.*?</div>', "", html, flags=re.DOTALL)
    converter = HTMLToMarkdownConverter(image_cache_dir, base_url, auth_cookie)
    converter.feed(html)
    markdown = "".join(converter._result)
    # 清理多余空行
    markdown = re.sub(r"\n{4,}", "\n\n\n", markdown)
    return markdown.strip()


def extract_headings(html: str) -> list[dict]:
    """从 HTML 提取标题列表，用于 trigger_patterns"""
    headings = []
    for match in re.finditer(r"<(h[1-6])[^>]*>(.*?)</\1>", html, re.DOTALL):
        tag = match.group(1)
        text = re.sub(r"<[^>]+>", "", match.group(2)).strip()
        if text:
            headings.append({"level": int(tag[1]), "text": text})
    return headings


# ═══════════════════════════════════════════════════════════════
# 第三部分: MinDoc 爬取
# ═══════════════════════════════════════════════════════════════

def fetch_url(url: str, auth_type: str = "none", auth_value: str = "",
              accept_json: bool = False) -> str:
    """
    带认证和重试的 URL 抓取
    auth_type: "none" / "cookie" / "token" / "bearer"
    """
    headers = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                       "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    if accept_json:
        headers["X-Requested-With"] = "XMLHttpRequest"
        headers["Accept"] = "application/json"

    cookie = ""
    if auth_type == "cookie":
        cookie = auth_value
    elif auth_type == "token":
        sep = "&" if "?" in url else "?"
        url = f"{url}{sep}token={auth_value}"
    elif auth_type == "bearer":
        headers["Authorization"] = f"Bearer {auth_value}"

    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            req = urllib.request.Request(url, headers=headers)
            if cookie:
                req.add_header("Cookie", cookie)
            with urllib.request.urlopen(req, timeout=15) as resp:
                content = resp.read().decode("utf-8")
                if accept_json:
                    return content
                return content
        except urllib.error.HTTPError as e:
            last_error = e
            status = e.code
            body = e.read().decode("utf-8", errors="replace")[:200]
            if attempt < MAX_RETRIES:
                time.sleep(attempt * 2)
        except Exception as e:
            last_error = e
            if attempt < MAX_RETRIES:
                time.sleep(attempt * 2)
    raise last_error


class MinDocCrawler:
    """MinDoc 爬虫 - 符合 PDR V8.1 规范"""

    def __init__(self, project_name: str, output_dir: str,
                 auth_type: str = "none", auth_value: str = ""):
        self.project_name = project_name
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.auth_type = auth_type
        self.auth_value = auth_value
        self.document_mapping = self._load_mapping()

    def _load_mapping(self) -> dict:
        """加载文档映射表"""
        mapping_path = self.output_dir / MAPPING_FILE
        if mapping_path.exists():
            with open(mapping_path, "r", encoding="utf-8") as f:
                return json.load(f)
        return {}

    def _save_mapping(self):
        """保存文档映射表"""
        mapping_path = self.output_dir / MAPPING_FILE
        with open(mapping_path, "w", encoding="utf-8") as f:
            json.dump(self.document_mapping, f, ensure_ascii=False, indent=2)

    def crawl_page(self, url: str, doc_id: str = "", title: str = "") -> Optional[dict]:
        """
        爬取单篇 MinDoc 文档页面

        如果页面正文是通过 AJAX 动态加载的，先尝试 JSON API 获取，
        否则回退到 HTML 页面直接解析。
        """
        # 尝试通过 AJAX API 获取正文（JSON）
        body_html = None
        page_title = title
        page_version = 0

        # 尝试 JSON API
        try:
            json_str = fetch_url(url, self.auth_type, self.auth_value, accept_json=True)
            data = json.loads(json_str)
            if data.get("errcode") == 0 and data.get("data", {}).get("body"):
                body_html = data["data"]["body"]
                page_title = data["data"].get("doc_title", title)
                page_version = data["data"].get("version", 0)
        except Exception:
            pass

        # 回退: 直接解析 HTML 页面
        if not body_html:
            try:
                html = fetch_url(url, self.auth_type, self.auth_value)
                # 尝试从 HTML 中提取正文
                match = re.search(
                    r'<div class="article-body[^"]*"[^>]*id="page-content"[^>]*>(.*?)</div>\s*</div>',
                    html, re.DOTALL,
                )
                if match:
                    body_html = match.group(1)
                if not page_title:
                    page_title = extract_title(html)
            except Exception as e:
                return {"error": str(e), "status": "fetch_failed"}

        if not body_html or not body_html.strip():
            return {"status": "empty", "doc_id": doc_id, "title": page_title}

        # 提取标题和 heading
        headings = extract_headings(body_html)
        if not page_title:
            page_title = headings[0]["text"] if headings else ""

        # 转换为 Markdown（图片自动 base64 内嵌）
        image_cache_dir = os.path.join(self.output_dir, ".image_cache")
        markdown_body = html_to_markdown(body_html, image_cache_dir,
                                          base_url=url,
                                          auth_cookie=self.auth_value)

        # 文件大小告警（WISE 可能对大文件有解析限制）
        md_size = len(markdown_body.encode("utf-8"))
        if md_size > 5 * 1024 * 1024:
            log.warning(f"⚠️ Markdown 文件过大 ({md_size // 1024 // 1024}MB): {url}")

        # 规范化 URL
        normalized_url = normalize_url(url)
        url_hash = compute_url_hash(normalized_url)
        document_key = make_document_key(self.project_name, url_hash)

        # 生成 Markdown 文件内容（含 Front Matter）
        md_hash = compute_md_hash(markdown_body)
        crawled_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        safe_title = re.sub(r'[\\/:*?"<>|]', "_", page_title)[:80]

        front_matter = (
            f"---\n"
            f"source: {SOURCE}\n"
            f"project_name: {self.project_name}\n"
            f"source_url: {url}\n"
            f"normalized_url: {normalized_url}\n"
            f"url_hash: {url_hash}\n"
            f"doc_id: {doc_id or 'null'}\n"
            f"title: {page_title}\n"
            f"system_type: {self.project_name}\n"
            f"md_hash: {md_hash}\n"
            f"crawled_at: {crawled_at}\n"
            f"---\n\n"
        )
        full_markdown = front_matter + f"# {page_title}\n\n" + markdown_body

        # 文件名: {project_name}_{url_hash}_{safe_title}.md
        filename = f"{self.project_name}_{url_hash}_{safe_title}.md"
        filepath = self.output_dir / filename

        # 构造文档记录
        doc_record = {
            "mapping_id": None,
            "project_name": self.project_name,
            "document_key": document_key,
            "source_url": url,
            "normalized_url": normalized_url,
            "url_hash": url_hash,
            "doc_id": doc_id or None,
            "title": page_title,
            "md_hash": md_hash,
            "filename": filename,
            "filepath": str(filepath.absolute()),
            "full_markdown": full_markdown,
            "markdown_body": markdown_body,
            "wise_file_id": None,
            "old_wise_file_id": None,
            "sync_status": "pending",
            "parse_status": None,
            "last_seen_time": crawled_at,
            "last_sync_time": None,
            "error_message": None,
            "headings": headings,
            "version": page_version,
        }

        return doc_record

    def crawl_document_tree(self, entry_url: str, root_url: str = "",
                            include_path: str = "") -> list[dict]:
        """
        从入口 URL 解析文档链接，逐篇爬取
        - entry_url: 入口 URL（可包含 token）
        - root_url: 限定爬取范围的根 URL
        - include_path: 允许爬取的路径前缀
        """
        # 规范化入口 URL
        normalized_entry = normalize_url(entry_url)
        if not root_url:
            root_url = normalized_entry
        if not include_path:
            parsed = urllib.parse.urlparse(normalized_entry)
            include_path = parsed.path

        print(f"📡 入口 URL: {entry_url}")
        print(f"   根 URL:   {root_url}")
        print(f"   路径范围: {include_path}")
        print(f"   认证方式: {self.auth_type}")

        # 获取入口页面 HTML
        html = fetch_url(entry_url, self.auth_type, self.auth_value)
        sidebar_links = find_sidebar_links(html)
        print(f"   发现 {len(sidebar_links)} 个文档链接\n")

        # 过滤链接
        filtered = []
        for link in sidebar_links:
            url = link["url"]
            if root_url and not url.startswith(root_url):
                continue
            if include_path and include_path not in url:
                continue
            if link["url"] not in [f["url"] for f in filtered]:
                filtered.append(link)

        docs = []
        for i, link in enumerate(filtered, 1):
            url = link["url"]
            doc_id = link["doc_id"]
            title = link["title"]

            # 检查是否已爬取且未变化
            normalized_url = normalize_url(url)
            url_hash = compute_url_hash(normalized_url)
            document_key = make_document_key(self.project_name, url_hash)

            existing = self.document_mapping.get(document_key)
            if existing and existing.get("sync_status") in ("completed", "skipped"):
                # 需要重新计算 md_hash 判断是否更新
                pass  # 先爬取再判断

            try:
                doc = self.crawl_page(url, doc_id, title)
                if doc is None:
                    print(f"[{i:3d}/{len(filtered)}] ⬜ 跳过 (无内容)  {title}")
                    continue

                if "error" in doc:
                    print(f"[{i:3d}/{len(filtered)}] ❌ 失败: {doc['error']}  {title}")
                    continue

                if doc.get("status") == "empty":
                    print(f"[{i:3d}/{len(filtered)}] ⬜ 空文档  {title}")
                    continue

                # 与映射表比对
                existing = self.document_mapping.get(document_key)
                if existing:
                    old_hash = existing.get("md_hash", "")
                    if doc["md_hash"] == old_hash:
                        doc["sync_status"] = "skipped"
                        print(f"[{i:3d}/{len(filtered)}] ⏭ 未变化 (跳过)  {title}")
                    else:
                        doc["sync_status"] = "updated"
                        doc["old_wise_file_id"] = existing.get("wise_file_id")
                        print(f"[{i:3d}/{len(filtered)}] 🔄 内容已更新  {title}")
                else:
                    doc["sync_status"] = "new"
                    print(f"[{i:3d}/{len(filtered)}] ✅ 新增  ({(len(doc.get('filepath','')) > 0)} )  {title}")

                # 写入 Markdown 文件
                self._write_markdown_file(doc)

                # 更新映射表
                self.document_mapping[document_key] = doc
                docs.append(doc)

            except Exception as e:
                print(f"[{i:3d}/{len(filtered)}] ⚠️ 异常: {e}  {title}")

            time.sleep(REQUEST_INTERVAL)

        # 保存映射表
        self._save_mapping()
        return docs

    def _write_markdown_file(self, doc: dict):
        """写入 Markdown 文件（使用 doc 中保存的 full_markdown）"""
        filepath = self.output_dir / doc["filename"]
        content = doc.get("full_markdown", "")
        if not content:
            return
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)

    def get_system_distribution(self, docs: list[dict]) -> dict:
        """统计文档系统分布 (WMS/QMS/MES)"""
        systems = {"WMS": 0, "QMS": 0, "MES": 0, "OTHER": 0}
        for doc in docs:
            url = doc.get("source_url", "").lower()
            title = doc.get("title", "").lower()
            combined = url + " " + title
            if "wms" in combined or "仓储" in combined or "入库" in combined or "库内" in combined:
                systems["WMS"] += 1
            elif "qms" in combined or "质量" in combined or "iqc" in combined or "oqc" in combined:
                systems["QMS"] += 1
            elif "mes" in combined or "生产" in combined or "制程" in combined:
                systems["MES"] += 1
            else:
                systems["OTHER"] += 1
        return systems


# ═══════════════════════════════════════════════════════════════
# 第四部分: WISE 文件入库
# ═══════════════════════════════════════════════════════════════

class WiseFileIngestor:
    """WISE 文件入库客户端（符合 PDR V8.1 规范）"""

    def __init__(self, base_url: str, token: str, knowledge_base_id: str):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.knowledge_base_id = knowledge_base_id

    def upload_markdown_file(self, filepath: str, metadata: dict) -> dict:
        """
        上传 Markdown 文件到 WISE（multipart file upload）

        POST /api/v1/knowledge-bases/{knowledge_base_id}/knowledge/file
        """
        url = f"{self.base_url}/knowledge-bases/{self.knowledge_base_id}/knowledge/file"

        # 构造 multipart 请求
        boundary = "----WISEUpload" + hashlib.md5(str(time.time()).encode()).hexdigest()[:12]

        # 读取文件
        with open(filepath, "rb") as f:
            file_content = f.read()

        filename = os.path.basename(filepath)

        # 构建 multipart body
        body_parts = []

        # file 字段
        body_parts.append(f"--{boundary}\r\n")
        body_parts.append(
            f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            f"Content-Type: text/markdown\r\n\r\n"
        )
        file_content_str = file_content.decode("utf-8")
        body_parts.append(file_content_str)
        body_parts.append("\r\n")

        # metadata 字段
        for key, value in metadata.items():
            if value is not None:
                body_parts.append(f"--{boundary}\r\n")
                body_parts.append(
                    f'Content-Disposition: form-data; name="metadata.{key}"\r\n\r\n'
                )
                body_parts.append(str(value))
                body_parts.append("\r\n")

        body_parts.append(f"--{boundary}--\r\n")
        body = "".join(body_parts)

        headers = {
            "x-api-key": self.token,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        }

        req = urllib.request.Request(url, data=body.encode("utf-8"), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                result = json.loads(resp.read().decode("utf-8"))
                return result
        except urllib.error.HTTPError as e:
            error_body = e.read().decode("utf-8", errors="replace")[:500]
            return {"errcode": e.code, "error": error_body, "status": "upload_failed"}
        except Exception as e:
            return {"errcode": -1, "error": str(e), "status": "upload_failed"}

    def poll_parse_status(self, wise_file_id: str, timeout: int = PARSE_POLL_TIMEOUT) -> str:
        """
        轮询 WISE 文件解析状态
        返回: "completed" / "failed" / "processing" / "timeout"
        """
        url = f"{self.base_url}/knowledge-bases/{self.knowledge_base_id}/knowledge/{wise_file_id}"
        headers = {
            "x-api-key": self.token,
            "Accept": "application/json",
        }

        elapsed = 0
        while elapsed < timeout:
            try:
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req, timeout=15) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                    status = data.get("data", {}).get("parse_status", "processing")
                    if status == "completed":
                        return "completed"
                    elif status == "failed":
                        return "failed"
                    # processing — 继续轮询
            except Exception:
                pass

            time.sleep(PARSE_POLL_INTERVAL)
            elapsed += PARSE_POLL_INTERVAL

        return "timeout"


# ═══════════════════════════════════════════════════════════════
# 第五部分: 主流程
# ═══════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="MinDoc 文档爬取与 WISE 知识入库工具 (PDR V8.1)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    # 爬取配置
    parser.add_argument("--project", default="MFG", help="项目名称 (如 QMS/MES/WMS)")
    parser.add_argument("--entry", default="https://docs.cvte.com/docs/mfg/mfg-1gabr49bn55co",
                        help="MinDoc 入口 URL")
    parser.add_argument("--root-url", default="https://docs.cvte.com/docs/mfg",
                        help="爬取根 URL")
    parser.add_argument("--include", default="/docs/mfg/",
                        help="允许爬取的路径前缀")
    parser.add_argument("--output", default=DEFAULT_OUTPUT,
                        help="输出目录 (默认: knowledge-base)")

    # 认证配置
    parser.add_argument("--auth-type", default="none",
                        choices=["none", "cookie", "token", "bearer"],
                        help="认证方式")
    parser.add_argument("--auth-value", default="",
                        help="认证凭证 (Cookie/Token/Bearer 值)")
    parser.add_argument("--token", default="",
                        help="MinDoc 访问 Token (等同于 --auth-type token)")

    # WISE 配置
    parser.add_argument("--wise-base-url", help="WISE API 基础 URL")
    parser.add_argument("--wise-token", help="WISE API Token")
    parser.add_argument("--wise-kb-id", help="WISE 知识库 ID")

    # 操作模式
    parser.add_argument("--upload", action="store_true", help="爬取后上传到 WISE")
    parser.add_argument("--dry-run", action="store_true", help="仅打印计划操作，不实际执行")

    args = parser.parse_args()

    # 处理 token
    if args.token and args.auth_type == "none":
        args.auth_type = "token"
        args.auth_value = args.token

    print(f"\n{'=' * 60}")
    print(f"  MinDoc → Markdown → WISE 知识入库")
    print(f"  版本: PDR {PDR_VERSION}")
    print(f"  项目: {args.project}")
    print(f"{'=' * 60}\n")

    # 初始化爬虫
    crawler = MinDocCrawler(
        project_name=args.project,
        output_dir=os.path.join(args.output, args.project.lower()),
        auth_type=args.auth_type,
        auth_value=args.auth_value,
    )

    # 爬取文档
    docs = crawler.crawl_document_tree(
        entry_url=args.entry,
        root_url=args.root_url,
        include_path=args.include,
    )

    # 统计
    total = len(docs)
    new_docs = sum(1 for d in docs if d.get("sync_status") == "new")
    updated_docs = sum(1 for d in docs if d.get("sync_status") == "updated")
    skipped_docs = sum(1 for d in docs if d.get("sync_status") == "skipped")
    systems = crawler.get_system_distribution(docs)

    print(f"\n{'=' * 60}")
    print(f"📊 爬取统计:")
    print(f"   总文档: {total}")
    print(f"   新增: {new_docs}")
    print(f"   更新: {updated_docs}")
    print(f"   跳过: {skipped_docs}")
    print(f"   系统分布: WMS={systems['WMS']}, QMS={systems['QMS']}, "
          f"MES={systems['MES']}, 其他={systems['OTHER']}")
    print(f"   输出目录: {crawler.output_dir}")
    print(f"   映射表: {crawler.output_dir / MAPPING_FILE}")

    # WISE 上传
    if args.upload and args.wise_base_url and args.wise_token and args.wise_kb_id:
        ingestor = WiseFileIngestor(args.wise_base_url, args.wise_token, args.wise_kb_id)
        print(f"\n{'=' * 60}")
        print(f"📤 正在上传到 WISE 知识库 [{args.wise_kb_id}]...")

        uploaded = 0
        failed = 0
        for doc in docs:
            if doc.get("sync_status") == "skipped":
                continue

            if args.dry_run:
                print(f"   [模拟] 上传: {doc['filename']}")
                uploaded += 1
                continue

            # 写入 Markdown 文件（使用 crawl_page 中已生成的完整内容）
            filepath = crawler.output_dir / doc["filename"]
            full_content = doc.get("full_markdown", "")
            if full_content:
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(full_content)

            # 上传
            metadata = {
                "source": SOURCE,
                "project_name": doc["project_name"],
                "source_url": doc["source_url"],
                "normalized_url": doc["normalized_url"],
                "url_hash": doc["url_hash"],
                "doc_id": doc.get("doc_id"),
                "md_hash": doc["md_hash"],
                "title": doc["title"],
            }

            upload_result = ingestor.upload_markdown_file(str(filepath), metadata)
            if upload_result.get("errcode") == 0 or upload_result.get("status") != "upload_failed":
                wise_file_id = upload_result.get("data", {}).get("file_id", "")
                if wise_file_id:
                    doc["wise_file_id"] = wise_file_id
                    doc["sync_status"] = "uploaded"
                    doc["last_sync_time"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

                    # 轮询 parse_status
                    parse_result = ingestor.poll_parse_status(wise_file_id)
                    doc["parse_status"] = parse_result
                    if parse_result == "completed":
                        doc["sync_status"] = "completed"
                        print(f"   ✅ 解析完成: {doc['title']}")
                    elif parse_result == "failed":
                        doc["parse_status"] = "parse_failed"
                        print(f"   ⚠️ 解析失败: {doc['title']}")
                    else:
                        print(f"   ⏳ 解析超时: {doc['title']}")
                    uploaded += 1
                else:
                    doc["sync_status"] = "upload_failed"
                    doc["error_message"] = str(upload_result.get("error", "unknown"))
                    print(f"   ❌ 上传失败: {doc['title']} - {upload_result}")
                    failed += 1
            else:
                doc["sync_status"] = "upload_failed"
                doc["error_message"] = str(upload_result.get("error", "unknown"))
                print(f"   ❌ 上传失败: {doc['title']} - {upload_result.get('error', '')}")
                failed += 1

            # 更新映射表
            document_key = doc["document_key"]
            crawler.document_mapping[document_key] = doc
            time.sleep(0.5)

        # 保存更新后的映射表
        crawler._save_mapping()
        print(f"   上传: {uploaded} | 失败: {failed}")

    print(f"\n{'=' * 60}")
    print(f"  ✅ 完成!")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
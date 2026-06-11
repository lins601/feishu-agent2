#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MinDoc -> Markdown -> WISE 知识库入库脚本 V2

核心修正：
1. 不再把图片塞进 Markdown base64；图片下载为本地文件。
2. Markdown 通过 WISE File API 上传，并传 enable_multimodel=true。
3. 上传成功后使用 data.id 作为 knowledge_id。
4. 使用 POST /knowledge/:id/inject-image 逐张注入图片。
5. 使用 GET /knowledge/:id 轮询 parse_status。

依赖：仅使用 Python 标准库。
可选：如果安装 Pillow，可扩展图片压缩；本脚本默认不强依赖 Pillow。
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import html
import io
import json
import logging
import mimetypes
import os
import re
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Optional

try:
    from PIL import Image as PILImage
    HAS_PILLOW = True
except ImportError:
    HAS_PILLOW = False


# ================================
# 基础配置
# ================================

SOURCE = "MinDoc"
DEFAULT_OUTPUT = "knowledge-base"
MAPPING_FILE = "mindoc_document_mapping.json"
REQUEST_INTERVAL_SECONDS = 0.3
MAX_RETRIES = 3
PARSE_POLL_INTERVAL_SECONDS = 5
PARSE_POLL_TIMEOUT_SECONDS = 300
MAX_IMAGE_SIZE_BYTES = 20 * 1024 * 1024

IMAGE_EXT_BY_MIME = {
    "image/png": ".png",
    "image/jpeg": ".jpg",
    "image/gif": ".gif",
    "image/webp": ".webp",
    "image/bmp": ".bmp",
    "image/svg+xml": ".svg",
}

# 兼容旧版 Python SSL（macOS Python 3.9 等）
_SSL_CTX = ssl.create_default_context()
try:
    _SSL_CTX.minimum_version = ssl.TLSVersion.TLSv1_2
except AttributeError:
    pass

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("crawl_mindoc_wise")


# ================================
# 通用工具
# ================================


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def safe_filename(name: str, max_len: int = 80) -> str:
    name = re.sub(r"[\\/:*?\"<>|\r\n\t]", "_", name).strip()
    name = re.sub(r"\s+", " ", name)
    return (name or "untitled")[:max_len]


def normalize_url(source_url: str) -> str:
    """
    用于文档去重的 URL 规范化。
    注意：这里会去除 token，避免同一篇文档因为 token 不同被当作不同文档。
    """
    parsed = urllib.parse.urlparse(source_url)
    netloc = parsed.netloc.lower()
    path = urllib.parse.unquote(parsed.path).rstrip("/") or "/"

    ignored_params = {
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "from",
        "timestamp",
        "token",
        "t",
        "v",
        "_",
    }

    query_params = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    kept_params: list[tuple[str, str]] = []
    for key, value in sorted(query_params):
        if key.lower() not in ignored_params:
            kept_params.append((key, value))

    return urllib.parse.urlunparse(
        (
            parsed.scheme,
            netloc,
            urllib.parse.quote(path, safe="/%"),
            "",
            urllib.parse.urlencode(kept_params),
            "",
        )
    )


def sha256_short(text: str, length: int = 12) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:length]


def md_hash(markdown: str) -> str:
    return hashlib.sha256(markdown.encode("utf-8")).hexdigest()[:16]


def is_probably_binary_url(url: str) -> bool:
    lower = urllib.parse.urlparse(url).path.lower()
    return lower.endswith(
        (
            ".png",
            ".jpg",
            ".jpeg",
            ".gif",
            ".webp",
            ".bmp",
            ".svg",
            ".pdf",
            ".zip",
            ".rar",
            ".7z",
            ".doc",
            ".docx",
            ".xls",
            ".xlsx",
            ".ppt",
            ".pptx",
        )
    )


def detect_mime_from_bytes(data: bytes) -> Optional[str]:
    if data.startswith(b"\x89PNG"):
        return "image/png"
    if data.startswith(b"\xff\xd8"):
        return "image/jpeg"
    if data.startswith(b"GIF8"):
        return "image/gif"
    if data.startswith(b"RIFF") and len(data) > 12 and data[8:12] == b"WEBP":
        return "image/webp"
    if data.startswith(b"BM"):
        return "image/bmp"
    if b"<svg" in data[:300].lower():
        return "image/svg+xml"
    return None


def parse_content_type(value: str) -> str:
    if not value:
        return ""
    return value.split(";", 1)[0].strip().lower()


# ================================
# 认证与 HTTP
# ================================


@dataclass
class AuthConfig:
    auth_type: str = "none"  # none / cookie / token / bearer
    auth_value: str = ""

    def apply_to_url(self, url: str) -> str:
        if self.auth_type != "token" or not self.auth_value:
            return url
        parsed = urllib.parse.urlparse(url)
        query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
        if not any(k == "token" for k, _ in query):
            query.append(("token", self.auth_value))
        return urllib.parse.urlunparse(parsed._replace(query=urllib.parse.urlencode(query)))

    def headers(self) -> dict[str, str]:
        headers = {
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0 Safari/537.36"
            ),
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        }
        if self.auth_type == "cookie" and self.auth_value:
            headers["Cookie"] = self.auth_value
        elif self.auth_type == "bearer" and self.auth_value:
            headers["Authorization"] = f"Bearer {self.auth_value}"
        return headers


def _fetch_bytes_curl(
    url: str,
    headers: dict[str, str],
    *,
    timeout: int = 30,
) -> tuple[bytes, dict[str, str]]:
    """使用 curl 子进程下载，兼容旧版 Python SSL。"""
    cmd = ["curl", "-sSL", "--max-time", str(timeout), "-D", "-"]
    for key, value in headers.items():
        cmd.extend(["-H", f"{key}: {value}"])
    cmd.append(url)

    proc = subprocess.run(cmd, capture_output=True, timeout=timeout + 10)
    if proc.returncode != 0:
        raise RuntimeError(f"curl exited {proc.returncode}: {proc.stderr.decode('utf-8', errors='replace')[:300]}")

    raw = proc.stdout
    # 分离 HTTP 响应头与正文
    header_end = raw.find(b"\r\n\r\n")
    if header_end == -1:
        raise RuntimeError("curl 输出中未找到响应头分隔符")

    header_block = raw[:header_end].decode("utf-8", errors="replace")
    body = raw[header_end + 4:]

    resp_headers: dict[str, str] = {}
    for line in header_block.split("\r\n")[1:]:  # 跳过状态行
        if ":" in line:
            k, v = line.split(":", 1)
            resp_headers[k.strip()] = v.strip()

    # 提取状态码
    status_line = header_block.split("\r\n", 1)[0]
    status_code = 0
    parts = status_line.split(" ", 2)
    if len(parts) >= 2:
        try:
            status_code = int(parts[1])
        except ValueError:
            pass
    if status_code >= 400:
        raise RuntimeError(f"HTTP {status_code}: {url}; body={body[:300].decode('utf-8', errors='replace')}")

    return body, resp_headers


def fetch_bytes(
    url: str,
    auth: AuthConfig,
    *,
    timeout: int = 30,
    headers_extra: Optional[dict[str, str]] = None,
) -> tuple[bytes, dict[str, str]]:
    last_error: Optional[BaseException] = None
    final_url = auth.apply_to_url(url)

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            headers = auth.headers()
            if headers_extra:
                headers.update(headers_extra)
            return _fetch_bytes_curl(final_url, headers, timeout=timeout)
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if attempt < MAX_RETRIES:
                time.sleep(attempt * 2)

    raise last_error or RuntimeError(f"fetch failed: {url}")


def fetch_text(
    url: str,
    auth: AuthConfig,
    *,
    timeout: int = 30,
    headers_extra: Optional[dict[str, str]] = None,
) -> str:
    data, headers = fetch_bytes(url, auth, timeout=timeout, headers_extra=headers_extra)
    content_type = parse_content_type(headers.get("Content-Type", ""))
    if content_type and "charset=" in headers.get("Content-Type", "").lower():
        charset = headers.get("Content-Type", "").split("charset=", 1)[1].split(";", 1)[0].strip()
    else:
        charset = "utf-8"
    return data.decode(charset, errors="replace")


# ================================
# MinDoc 链接解析
# ================================


class AnchorExtractor(HTMLParser):
    def __init__(self, base_url: str):
        super().__init__()
        self.base_url = base_url
        self.links: list[dict[str, str]] = []
        self._current: Optional[dict[str, str]] = None
        self._text_parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, Optional[str]]]) -> None:
        if tag.lower() != "a":
            return
        attrs_dict = {k.lower(): v or "" for k, v in attrs}
        href = attrs_dict.get("href", "").strip()
        if not href or href.startswith(("javascript:", "mailto:", "tel:")):
            return
        self._current = {
            "url": urllib.parse.urljoin(self.base_url, href),
            "title": attrs_dict.get("title", ""),
        }
        self._text_parts = []

    def handle_data(self, data: str) -> None:
        if self._current is not None:
            self._text_parts.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() == "a" and self._current is not None:
            text = re.sub(r"\s+", " ", "".join(self._text_parts)).strip()
            if not self._current.get("title"):
                self._current["title"] = text
            self.links.append(self._current)
            self._current = None
            self._text_parts = []


def find_document_links(
    html_text: str,
    entry_url: str,
    *,
    root_url: str = "",
    include_path: str = "",
) -> list[dict[str, str]]:
    extractor = AnchorExtractor(entry_url)
    extractor.feed(html_text)

    entry_parsed = urllib.parse.urlparse(entry_url)
    root_norm = normalize_url(root_url) if root_url else ""
    result: list[dict[str, str]] = []
    seen: set[str] = set()

    for link in extractor.links:
        raw_url = link["url"]
        parsed = urllib.parse.urlparse(raw_url)
        if parsed.scheme not in ("http", "https"):
            continue
        if parsed.netloc.lower() != entry_parsed.netloc.lower():
            continue
        if is_probably_binary_url(raw_url):
            continue

        clean_url = urllib.parse.urlunparse(parsed._replace(fragment=""))
        norm = normalize_url(clean_url)

        if root_norm and not norm.startswith(root_norm):
            continue
        if include_path and include_path not in parsed.path:
            continue
        if norm in seen:
            continue

        seen.add(norm)
        doc_id = parsed.path.rstrip("/").split("/")[-1]
        title = html.unescape(link.get("title", "") or doc_id)
        result.append({"url": clean_url, "title": title, "doc_id": doc_id})

    if not result:
        parsed = urllib.parse.urlparse(entry_url)
        result.append({"url": entry_url, "title": parsed.path.rstrip("/").split("/")[-1], "doc_id": parsed.path.rstrip("/").split("/")[-1]})

    return result


# ================================
# HTML 正文抽取
# ================================


def extract_title(html_text: str) -> str:
    patterns = [
        r'<h1[^>]*id=["\']article-title["\'][^>]*>(.*?)</h1>',
        r'<h1[^>]*>(.*?)</h1>',
        r'<title[^>]*>(.*?)</title>',
    ]
    for pattern in patterns:
        match = re.search(pattern, html_text, flags=re.I | re.S)
        if match:
            title = re.sub(r"<[^>]+>", "", match.group(1))
            title = html.unescape(title)
            title = title.replace(" - Powered by MinDoc", "")
            title = re.sub(r"\s+", " ", title).strip()
            if title:
                return title
    return ""


def extract_div_by_attr(html_text: str, attr_regex: str) -> Optional[str]:
    """
    用简单 div 深度匹配抽取指定 div，避免正则在内部 div 处提前结束。
    attr_regex 示例：r'id=["\']page-content["\']'
    """
    start_match = re.search(r"<div\b(?=[^>]*" + attr_regex + r")[^>]*>", html_text, flags=re.I | re.S)
    if not start_match:
        return None

    pos = start_match.start()
    depth = 0
    for match in re.finditer(r"</?div\b[^>]*>", html_text[pos:], flags=re.I | re.S):
        token = match.group(0).lower()
        if token.startswith("</div"):
            depth -= 1
            if depth == 0:
                end = pos + match.end()
                return html_text[pos:end]
        else:
            depth += 1
    return html_text[pos:]


def extract_body_html(page_html: str) -> str:
    candidates = [
        r'id=["\']page-content["\']',
        r'class=["\'][^"\']*article-body[^"\']*["\']',
        r'class=["\'][^"\']*markdown-body[^"\']*["\']',
        r'class=["\'][^"\']*doc-content[^"\']*["\']',
        r'id=["\']article-content["\']',
    ]
    for attr_regex in candidates:
        body = extract_div_by_attr(page_html, attr_regex)
        if body and len(re.sub(r"<[^>]+>", "", body).strip()) > 20:
            return body

    article_match = re.search(r"<article\b[^>]*>(.*?)</article>", page_html, flags=re.I | re.S)
    if article_match:
        return article_match.group(0)

    body_match = re.search(r"<body\b[^>]*>(.*?)</body>", page_html, flags=re.I | re.S)
    if body_match:
        return body_match.group(1)

    return page_html


def try_fetch_mindoc_json_body(url: str, auth: AuthConfig) -> Optional[dict[str, Any]]:
    """
    兼容部分 MinDoc 主题：同一个文档 URL 在 X-Requested-With 下可能返回 JSON。
    如果不是 JSON，直接返回 None。
    """
    try:
        text = fetch_text(
            url,
            auth,
            timeout=30,
            headers_extra={
                "X-Requested-With": "XMLHttpRequest",
                "Accept": "application/json,text/plain,*/*",
            },
        )
        data = json.loads(text)
        if not isinstance(data, dict):
            return None
        payload = data.get("data") if isinstance(data.get("data"), dict) else data
        body = payload.get("body") or payload.get("content") or payload.get("markdown")
        if body:
            return {
                "body_html": body,
                "title": payload.get("doc_title") or payload.get("title") or "",
                "version": payload.get("version"),
            }
    except Exception:
        return None
    return None


# ================================
# 图片下载
# ================================


def download_image_to_file(
    img_url: str,
    cache_dir: Path,
    auth: AuthConfig,
    *,
    page_url: str,
) -> Optional[dict[str, Any]]:
    if img_url.startswith("data:"):
        log.warning("跳过 data URI 图片，不再写入 Markdown base64")
        return None

    headers_extra = {
        "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
        "Referer": page_url,
    }

    try:
        data, headers = fetch_bytes(img_url, auth, timeout=40, headers_extra=headers_extra)
    except Exception as exc:  # noqa: BLE001
        log.warning("图片下载失败: %s; error=%s", img_url, exc)
        return None

    if not data:
        log.warning("图片下载为空: %s", img_url)
        return None

    if len(data) > MAX_IMAGE_SIZE_BYTES:
        log.warning("图片过大，跳过注入: %.2fMB, url=%s", len(data) / 1024 / 1024, img_url)
        return None

    content_type = parse_content_type(headers.get("Content-Type", ""))
    detected = detect_mime_from_bytes(data)
    mime = detected or (content_type if content_type.startswith("image/") else "")
    if not mime:
        log.warning("下载内容不是图片: Content-Type=%s, url=%s", content_type, img_url)
        return None

    ext = IMAGE_EXT_BY_MIME.get(mime) or mimetypes.guess_extension(mime) or ".img"
    cache_dir.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(img_url.encode("utf-8")).hexdigest()[:20]
    image_path = cache_dir / f"{key}{ext}"
    meta_path = cache_dir / f"{key}.json"

    if not image_path.exists():
        image_path.write_bytes(data)

    meta = {
        "source_url": img_url,
        "local_path": str(image_path),
        "mime": mime,
        "size": len(data),
        "downloaded_at": now_str(),
    }
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    return meta


# ================================
# HTML -> Markdown
# ================================


@dataclass
class ImageRef:
    index: int
    alt: str
    source_url: str
    local_path: str
    mime: str
    size: int
    placeholder: str
    wise_url: str = ""
    inject_status: str = "pending"
    error_message: str = ""
    compressed_b64: str = ""
    compressed_mime: str = ""


def compress_image_for_base64(image_path: str, max_width: int = 800, quality: int = 70) -> tuple[str, bytes]:
    """压缩图片并返回 (mime_type, compressed_bytes)。"""
    if not HAS_PILLOW:
        raw = Path(image_path).read_bytes()
        mime = detect_mime_from_bytes(raw[:16]) or "image/png"
        return mime, raw

    img = PILImage.open(image_path)
    if img.mode in ("RGBA", "P", "LA"):
        img = img.convert("RGB")
    if img.width > max_width:
        ratio = max_width / img.width
        new_h = max(1, int(img.height * ratio))
        img = img.resize((max_width, new_h), PILImage.LANCZOS)

    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=quality, optimize=True)
    return "image/jpeg", buf.getvalue()


class HTMLToMarkdownConverter(HTMLParser):
    def __init__(self, base_url: str, image_cache_dir: Path, auth: AuthConfig):
        super().__init__(convert_charrefs=True)
        self.base_url = base_url
        self.image_cache_dir = image_cache_dir
        self.auth = auth
        self.result: list[str] = []
        self.image_refs: list[ImageRef] = []

        self.skip_tags = {"script", "style", "head", "nav", "footer", "header", "aside"}
        self.skip_depth = 0

        self.in_pre = False
        self.in_code = False
        self.in_table = False
        self.table_rows: list[list[str]] = []
        self.current_row: list[str] = []
        self.current_cell_parts: list[str] = []
        self.in_cell = False

        self.link_href = ""
        self.link_parts: list[str] = []
        self.in_link = False

        self.list_depth = 0

    def _append(self, text: str) -> None:
        if self.skip_depth > 0:
            return
        if self.in_link:
            self.link_parts.append(text)
        elif self.in_cell:
            self.current_cell_parts.append(text)
        else:
            self.result.append(text)

    def handle_starttag(self, tag: str, attrs: list[tuple[str, Optional[str]]]) -> None:
        tag = tag.lower()
        attrs_dict = {k.lower(): v or "" for k, v in attrs}

        if tag in self.skip_tags:
            self.skip_depth += 1
            return
        if self.skip_depth > 0:
            return

        if tag in ("h1", "h2", "h3", "h4", "h5", "h6"):
            level = int(tag[1])
            self._append("\n\n" + "#" * level + " ")
        elif tag == "p":
            self._append("\n\n")
        elif tag == "br":
            self._append("\n")
        elif tag == "hr":
            self._append("\n\n---\n\n")
        elif tag in ("strong", "b"):
            self._append("**")
        elif tag in ("em", "i"):
            self._append("*")
        elif tag == "blockquote":
            self._append("\n\n> ")
        elif tag in ("ul", "ol"):
            self.list_depth += 1
            self._append("\n")
        elif tag == "li":
            indent = "  " * max(0, self.list_depth - 1)
            self._append(f"\n{indent}- ")
        elif tag == "pre":
            self.in_pre = True
            self._append("\n\n```\n")
        elif tag == "code" and not self.in_pre:
            self.in_code = True
            self._append("`")
        elif tag == "table":
            self.in_table = True
            self.table_rows = []
        elif tag == "tr":
            self.current_row = []
        elif tag in ("td", "th"):
            self.in_cell = True
            self.current_cell_parts = []
        elif tag == "a":
            self.in_link = True
            self.link_href = attrs_dict.get("href", "")
            self.link_parts = []
        elif tag == "img":
            self._handle_image(attrs_dict)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()

        if tag in self.skip_tags and self.skip_depth > 0:
            self.skip_depth -= 1
            return
        if self.skip_depth > 0:
            return

        if tag in ("h1", "h2", "h3", "h4", "h5", "h6"):
            self._append("\n")
        elif tag in ("strong", "b"):
            self._append("**")
        elif tag in ("em", "i"):
            self._append("*")
        elif tag == "pre":
            self.in_pre = False
            self._append("\n```\n")
        elif tag == "code" and self.in_code:
            self.in_code = False
            self._append("`")
        elif tag in ("ul", "ol"):
            self.list_depth = max(0, self.list_depth - 1)
            self._append("\n")
        elif tag == "a" and self.in_link:
            text = "".join(self.link_parts).strip()
            href = self.link_href.strip()
            if href:
                href = urllib.parse.urljoin(self.base_url, href)
            if text and href:
                self.in_link = False
                self.link_parts = []
                self.link_href = ""
                self._append(f"[{text}]({href})")
            else:
                self.in_link = False
                self.link_parts = []
                self.link_href = ""
                self._append(text)
        elif tag in ("td", "th") and self.in_cell:
            cell = "".join(self.current_cell_parts)
            cell = re.sub(r"\s+", " ", cell).replace("|", "\\|").strip()
            self.current_row.append(cell)
            self.current_cell_parts = []
            self.in_cell = False
        elif tag == "tr":
            if self.current_row:
                self.table_rows.append(self.current_row)
            self.current_row = []
        elif tag == "table":
            self.in_table = False
            self._append("\n\n")
            if self.table_rows:
                max_cols = max(len(row) for row in self.table_rows)
                for i, row in enumerate(self.table_rows):
                    normalized = row + [""] * (max_cols - len(row))
                    self._append("| " + " | ".join(normalized) + " |\n")
                    if i == 0:
                        self._append("| " + " | ".join(["---"] * max_cols) + " |\n")
            self._append("\n")
            self.table_rows = []

    def handle_data(self, data: str) -> None:
        if self.skip_depth > 0:
            return
        if not self.in_pre:
            data = re.sub(r"[ \t\r\f\v]+", " ", data)
        self._append(data)

    def _handle_image(self, attrs: dict[str, str]) -> None:
        alt = html.unescape(attrs.get("alt", "") or attrs.get("title", "") or "图片")
        src = (
            attrs.get("data-original")
            or attrs.get("data-src")
            or attrs.get("data-lazy-src")
            or attrs.get("src")
            or ""
        ).strip()

        if not src:
            self._append(f"\n\n[{alt}]\n")
            return

        if src.startswith("//"):
            src = "https:" + src
        else:
            src = urllib.parse.urljoin(self.base_url, src)

        image_meta = download_image_to_file(src, self.image_cache_dir, self.auth, page_url=self.base_url)
        if not image_meta:
            self._append(f"\n\n![{alt}]({src})\n")
            return

        index = len(self.image_refs) + 1
        placeholder = f"__WISE_IMAGE_{index}__"
        image_ref = ImageRef(
            index=index,
            alt=alt,
            source_url=src,
            local_path=image_meta["local_path"],
            mime=image_meta["mime"],
            size=int(image_meta["size"]),
            placeholder=placeholder,
        )

        # 压缩图片，base64 存到 ImageRef 中，正文只写短引用
        try:
            cmime, compressed = compress_image_for_base64(image_meta["local_path"])
            log.info("  图片压缩: %s (%.1fKB -> %.1fKB)",
                     image_meta["mime"],
                     int(image_meta["size"]) / 1024, len(compressed) / 1024)
            image_ref.compressed_b64 = base64.b64encode(compressed).decode("ascii")
            image_ref.compressed_mime = cmime
        except Exception as exc:
            log.warning("  图片压缩失败: %s", exc)

        self.image_refs.append(image_ref)

        # 正文只写短引用，图片统一放到附录
        self._append(f"\n\n[图片{index}: {alt}]\n")


def html_to_markdown(body_html: str, base_url: str, image_cache_dir: Path, auth: AuthConfig) -> tuple[str, list[ImageRef]]:
    cleaned = re.sub(r'<div class="wiki-bottom".*?</div>', "", body_html, flags=re.I | re.S)
    cleaned = re.sub(r'<div class="jump-top".*?</div>', "", cleaned, flags=re.I | re.S)
    converter = HTMLToMarkdownConverter(base_url, image_cache_dir, auth)
    converter.feed(cleaned)
    markdown = "".join(converter.result)
    markdown = html.unescape(markdown)
    markdown = re.sub(r"\n{4,}", "\n\n\n", markdown)
    markdown = re.sub(r"[ \t]+\n", "\n", markdown)
    return markdown.strip(), converter.image_refs


# ================================
# MinDoc 爬虫
# ================================


@dataclass
class CrawledDoc:
    project_name: str
    title: str
    source_url: str
    normalized_url: str
    url_hash: str
    document_key: str
    doc_id: str
    markdown_body: str
    full_markdown: str
    md_hash: str
    filename: str
    filepath: str
    image_refs: list[ImageRef] = field(default_factory=list)
    sync_status: str = "new"
    wise_knowledge_id: str = ""
    parse_status: str = ""
    error_message: str = ""

    def to_mapping_dict(self) -> dict[str, Any]:
        return {
            "project_name": self.project_name,
            "title": self.title,
            "source_url": self.source_url,
            "normalized_url": self.normalized_url,
            "url_hash": self.url_hash,
            "document_key": self.document_key,
            "doc_id": self.doc_id,
            "md_hash": self.md_hash,
            "filename": self.filename,
            "filepath": self.filepath,
            "image_refs": [ref.__dict__ for ref in self.image_refs],
            "sync_status": self.sync_status,
            "wise_knowledge_id": self.wise_knowledge_id,
            "parse_status": self.parse_status,
            "error_message": self.error_message,
            "last_seen_time": now_str(),
        }


class MinDocCrawler:
    def __init__(self, project_name: str, output_dir: Path, auth: AuthConfig):
        self.project_name = project_name
        self.output_dir = output_dir
        self.image_cache_dir = output_dir / ".image_cache"
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.image_cache_dir.mkdir(parents=True, exist_ok=True)
        self.auth = auth
        self.mapping_path = output_dir / MAPPING_FILE
        self.mapping = self._load_mapping()

    def _load_mapping(self) -> dict[str, Any]:
        if not self.mapping_path.exists():
            return {}
        try:
            return json.loads(self.mapping_path.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001
            log.warning("映射表读取失败，将重新创建: %s", exc)
            return {}

    def save_mapping(self) -> None:
        self.mapping_path.write_text(json.dumps(self.mapping, ensure_ascii=False, indent=2), encoding="utf-8")

    def crawl_tree(
        self,
        entry_url: str,
        *,
        root_url: str = "",
        include_path: str = "",
        max_pages: int = 0,
    ) -> list[CrawledDoc]:
        print(f"📡 入口 URL: {entry_url}")
        print(f"   root_url: {root_url or '(未限制)'}")
        print(f"   include:  {include_path or '(未限制)'}")
        print(f"   auth:     {self.auth.auth_type}")

        entry_html = fetch_text(entry_url, self.auth)
        links = find_document_links(entry_html, entry_url, root_url=root_url, include_path=include_path)
        if max_pages > 0:
            links = links[:max_pages]
        print(f"   发现文档链接: {len(links)}\n")

        docs: list[CrawledDoc] = []
        for index, link in enumerate(links, start=1):
            try:
                doc = self.crawl_page(link["url"], title_hint=link.get("title", ""), doc_id=link.get("doc_id", ""))
                if doc is None:
                    print(f"[{index:3d}/{len(links)}] ⬜ 空文档: {link.get('title', '')}")
                    continue

                old = self.mapping.get(doc.document_key)
                if old and old.get("md_hash") == doc.md_hash:
                    doc.sync_status = "skipped"
                    doc.wise_knowledge_id = old.get("wise_knowledge_id", "") or old.get("wise_file_id", "")
                    doc.parse_status = old.get("parse_status", "")
                    print(f"[{index:3d}/{len(links)}] ⏭ 未变化: {doc.title} / 图片 {len(doc.image_refs)}")
                elif old:
                    doc.sync_status = "updated"
                    doc.wise_knowledge_id = old.get("wise_knowledge_id", "") or old.get("wise_file_id", "")
                    print(f"[{index:3d}/{len(links)}] 🔄 更新: {doc.title} / 图片 {len(doc.image_refs)}")
                else:
                    doc.sync_status = "new"
                    print(f"[{index:3d}/{len(links)}] ✅ 新增: {doc.title} / 图片 {len(doc.image_refs)}")

                self.write_markdown(doc)
                self.mapping[doc.document_key] = doc.to_mapping_dict()
                docs.append(doc)
                self.save_mapping()
            except Exception as exc:  # noqa: BLE001
                print(f"[{index:3d}/{len(links)}] ❌ 失败: {link.get('url')} / {exc}")

            time.sleep(REQUEST_INTERVAL_SECONDS)

        return docs

    def crawl_page(self, url: str, *, title_hint: str = "", doc_id: str = "") -> Optional[CrawledDoc]:
        body_html = ""
        title = title_hint
        version = ""

        json_body = try_fetch_mindoc_json_body(url, self.auth)
        if json_body:
            body_html = json_body.get("body_html", "")
            title = json_body.get("title") or title
            version = str(json_body.get("version") or "")
        else:
            page_html = fetch_text(url, self.auth)
            body_html = extract_body_html(page_html)
            title = title or extract_title(page_html)

        if not body_html or not re.sub(r"<[^>]+>", "", body_html).strip():
            return None

        markdown_body, image_refs = html_to_markdown(body_html, url, self.image_cache_dir, self.auth)
        if not title:
            first_heading = re.search(r"^#\s+(.+)$", markdown_body, flags=re.M)
            title = first_heading.group(1).strip() if first_heading else doc_id or "未命名文档"

        normalized = normalize_url(url)
        url_hash = sha256_short(normalized)
        document_key = f"{self.project_name}_{url_hash}"
        markdown_hash = md_hash(markdown_body)
        crawled_at = now_str()

        front_matter = (
            "---\n"
            f"source: {SOURCE}\n"
            f"project_name: {self.project_name}\n"
            f"source_url: {url}\n"
            f"normalized_url: {normalized}\n"
            f"url_hash: {url_hash}\n"
            f"document_key: {document_key}\n"
            f"doc_id: {doc_id or 'null'}\n"
            f"title: {title}\n"
            f"md_hash: {markdown_hash}\n"
            f"version: {version or 'null'}\n"
            f"image_count: {len(image_refs)}\n"
            f"crawled_at: {crawled_at}\n"
            "---\n\n"
        )

        # 组装图片附录：每张图片一个独立分段
        image_appendix = ""
        images_with_b64 = [r for r in image_refs if r.compressed_b64]
        if images_with_b64:
            image_appendix = "\n\n---\n\n# 文档图片附录\n\n"
            for ref in images_with_b64:
                data_uri = f"data:{ref.compressed_mime};base64,{ref.compressed_b64}"
                image_appendix += f"\n---\n\n## 图片{ref.index}: {ref.alt}\n\n"
                image_appendix += f"![{ref.alt}]({data_uri})\n\n"

        full_markdown = front_matter + f"# {title}\n\n" + markdown_body + image_appendix + "\n"
        filename = f"{self.project_name}_{url_hash}_{safe_filename(title)}.md"
        filepath = str((self.output_dir / filename).absolute())

        return CrawledDoc(
            project_name=self.project_name,
            title=title,
            source_url=url,
            normalized_url=normalized,
            url_hash=url_hash,
            document_key=document_key,
            doc_id=doc_id,
            markdown_body=markdown_body,
            full_markdown=full_markdown,
            md_hash=markdown_hash,
            filename=filename,
            filepath=filepath,
            image_refs=image_refs,
        )

    def write_markdown(self, doc: CrawledDoc) -> None:
        Path(doc.filepath).write_text(doc.full_markdown, encoding="utf-8")

    def rewrite_markdown_with_wise_image_urls(self, doc: CrawledDoc) -> str:
        content = doc.full_markdown
        for ref in doc.image_refs:
            if ref.wise_url:
                content = content.replace(ref.placeholder, ref.wise_url)
        rewritten_path = Path(doc.filepath).with_suffix(".wise.md")
        rewritten_path.write_text(content, encoding="utf-8")
        return content


# ================================
# WISE API
# ================================


class WiseClient:
    def __init__(self, base_url: str, api_key: str, knowledge_base_id: str):
        self.base_url = base_url.rstrip("/")
        if self.base_url.endswith("/api/v1"):
            self.api_base = self.base_url
        else:
            self.api_base = self.base_url.rstrip("/")
        self.api_key = api_key
        self.knowledge_base_id = knowledge_base_id

    def _api_url(self, path: str) -> str:
        return self.api_base + path

    def _headers_json(self) -> dict[str, str]:
        return {
            "X-API-Key": self.api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    def _headers_multipart(self, boundary: str) -> dict[str, str]:
        return {
            "X-API-Key": self.api_key,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        }

    @staticmethod
    def _add_form_field(body: bytearray, boundary: str, name: str, value: str) -> None:
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(str(value).encode("utf-8"))
        body.extend(b"\r\n")

    @staticmethod
    def _add_file_field(
        body: bytearray,
        boundary: str,
        name: str,
        filepath: str,
        content_type: str,
        filename: Optional[str] = None,
    ) -> None:
        filename = filename or os.path.basename(filepath)
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(
            (
                f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                f"Content-Type: {content_type}\r\n\r\n"
            ).encode("utf-8")
        )
        body.extend(Path(filepath).read_bytes())
        body.extend(b"\r\n")

    @staticmethod
    def _curl_request(
        url: str,
        *,
        method: str = "GET",
        headers: Optional[dict[str, str]] = None,
        body: Optional[bytes] = None,
        timeout: int = 120,
    ) -> dict[str, Any]:
        """使用 curl 子进程发送 HTTP 请求，兼容旧版 Python SSL。"""
        cmd = ["curl", "-sSL", "--max-time", str(timeout), "-X", method]
        if headers:
            for key, value in headers.items():
                cmd.extend(["-H", f"{key}: {value}"])

        tmp_file = None
        if body:
            tmp_file = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
            tmp_file.write(body)
            tmp_file.close()
            cmd.extend(["--data-binary", f"@{tmp_file.name}"])

        cmd.append(url)

        try:
            proc = subprocess.run(cmd, capture_output=True, timeout=timeout + 30)
            raw = proc.stdout.decode("utf-8", errors="replace")
            if proc.returncode != 0:
                err = proc.stderr.decode("utf-8", errors="replace")[:2000]
                return {"success": False, "status": "curl_error", "errcode": proc.returncode, "error": err}
            if not raw.strip():
                return {"success": True}
            return json.loads(raw)
        except subprocess.TimeoutExpired:
            return {"success": False, "status": "timeout", "errcode": -1, "error": f"curl timeout after {timeout}s"}
        except json.JSONDecodeError as exc:
            return {"success": False, "status": "json_error", "errcode": -1, "error": f"{exc}; raw={raw[:500]}"}
        except Exception as exc:  # noqa: BLE001
            return {"success": False, "status": "request_error", "errcode": -1, "error": str(exc)}
        finally:
            if tmp_file:
                try:
                    os.unlink(tmp_file.name)
                except OSError:
                    pass

    def upload_markdown_file(self, filepath: str, metadata: dict[str, Any], *, enable_multimodel: bool = True) -> dict[str, Any]:
        """
        POST /knowledge-bases/:id/knowledge/file
        表单字段：file、metadata、enable_multimodel、fileName。
        """
        url = self._api_url(f"/knowledge-bases/{self.knowledge_base_id}/knowledge/file")
        boundary = "----WISEFile" + hashlib.md5(str(time.time()).encode()).hexdigest()[:16]
        body = bytearray()

        self._add_file_field(
            body,
            boundary,
            "file",
            filepath,
            "text/markdown; charset=utf-8",
            filename=os.path.basename(filepath),
        )
        self._add_form_field(body, boundary, "metadata", json.dumps(metadata, ensure_ascii=False))
        self._add_form_field(body, boundary, "enable_multimodel", "true" if enable_multimodel else "false")
        self._add_form_field(body, boundary, "fileName", os.path.basename(filepath))
        body.extend(f"--{boundary}--\r\n".encode("utf-8"))

        return self._curl_request(url, method="POST", headers=self._headers_multipart(boundary), body=bytes(body), timeout=300)

    def inject_image(self, knowledge_id: str, image_path: str, mime: str = "") -> dict[str, Any]:
        """
        POST /knowledge/:id/inject-image
        """
        url = self._api_url(f"/knowledge/{knowledge_id}/inject-image")
        boundary = "----WISEImage" + hashlib.md5(str(time.time()).encode()).hexdigest()[:16]
        body = bytearray()
        content_type = mime or mimetypes.guess_type(image_path)[0] or "application/octet-stream"
        self._add_file_field(body, boundary, "file", image_path, content_type)
        body.extend(f"--{boundary}--\r\n".encode("utf-8"))

        return self._curl_request(url, method="POST", headers=self._headers_multipart(boundary), body=bytes(body), timeout=180)

    def inject_content(self, knowledge_id: str, content: str) -> dict[str, Any]:
        """
        POST /knowledge/:id/inject-content
        """
        url = self._api_url(f"/knowledge/{knowledge_id}/inject-content")
        payload = json.dumps({"content": content}, ensure_ascii=False).encode("utf-8")
        return self._curl_request(url, method="POST", headers=self._headers_json(), body=payload, timeout=180)

    def get_knowledge_detail(self, knowledge_id: str) -> dict[str, Any]:
        url = self._api_url(f"/knowledge/{knowledge_id}")
        return self._curl_request(url, method="GET", headers=self._headers_json(), timeout=60)

    def poll_parse_status(self, knowledge_id: str, timeout_seconds: int = PARSE_POLL_TIMEOUT_SECONDS) -> str:
        elapsed = 0
        last_status = "processing"
        while elapsed < timeout_seconds:
            detail = self.get_knowledge_detail(knowledge_id)
            if detail.get("success") is False:
                log.warning("轮询失败: knowledge_id=%s, error=%s", knowledge_id, detail.get("error"))
            else:
                last_status = str(detail.get("data", {}).get("parse_status") or "processing")
                if last_status in {"completed", "failed"}:
                    return last_status
            time.sleep(PARSE_POLL_INTERVAL_SECONDS)
            elapsed += PARSE_POLL_INTERVAL_SECONDS
        return "timeout"


# ================================
# 上传编排
# ================================


def upload_doc_to_wise(
    doc: CrawledDoc,
    client: WiseClient,
    *,
    inject_images: bool = True,
    inject_content_after_images: bool = False,
    poll: bool = True,
) -> CrawledDoc:
    metadata = {
        "source": str(SOURCE),
        "project_name": str(doc.project_name),
        "source_url": str(doc.source_url),
        "normalized_url": str(doc.normalized_url),
        "url_hash": str(doc.url_hash),
        "document_key": str(doc.document_key),
        "doc_id": str(doc.doc_id or ""),
        "title": str(doc.title),
        "md_hash": str(doc.md_hash),
        "image_count": str(len(doc.image_refs)),
    }

    upload_result = client.upload_markdown_file(doc.filepath, metadata, enable_multimodel=True)
    if not upload_result.get("success", False):
        doc.error_message = str(upload_result.get("error") or upload_result)
        doc.sync_status = "upload_failed"
        return doc

    knowledge_id = str(upload_result.get("data", {}).get("id") or upload_result.get("data", {}).get("file_id") or "")
    if not knowledge_id:
        doc.error_message = f"WISE 上传成功但响应中没有 data.id: {upload_result}"
        doc.sync_status = "upload_failed"
        return doc

    doc.wise_knowledge_id = knowledge_id
    doc.sync_status = "uploaded"

    if inject_images and doc.image_refs:
        for ref in doc.image_refs:
            if not ref.local_path or not Path(ref.local_path).exists():
                ref.inject_status = "failed"
                ref.error_message = "local image file not found"
                continue
            result = client.inject_image(knowledge_id, ref.local_path, mime=ref.mime)
            if result.get("success"):
                ref.inject_status = "success"
                ref.wise_url = str(result.get("data", {}).get("url") or "")
            else:
                ref.inject_status = "failed"
                ref.error_message = str(result.get("error") or result)

    if inject_content_after_images and any(ref.wise_url for ref in doc.image_refs):
        rewritten_content = doc.full_markdown
        for ref in doc.image_refs:
            if ref.wise_url:
                rewritten_content = rewritten_content.replace(ref.placeholder, ref.wise_url)
        inject_result = client.inject_content(knowledge_id, rewritten_content)
        if not inject_result.get("success"):
            log.warning("图片 URL 回写内容失败: %s / %s", doc.title, inject_result)

    if poll:
        doc.parse_status = client.poll_parse_status(knowledge_id)

    return doc


# ================================
# CLI
# ================================


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="MinDoc -> Markdown -> WISE 知识库入库脚本 V2",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument("--project", default="MFG", help="项目名，例如 MFG/WMS/QMS/MES")
    parser.add_argument("--entry", required=True, help="MinDoc 入口 URL")
    parser.add_argument("--root-url", default="", help="限制爬取根 URL，例如 https://docs.cvte.com/docs/mfg")
    parser.add_argument("--include", default="", help="限制路径包含内容，例如 /docs/mfg/")
    parser.add_argument("--output", default=DEFAULT_OUTPUT, help="输出目录")
    parser.add_argument("--max-pages", type=int, default=0, help="最多爬取页数，0 表示不限制")

    parser.add_argument("--auth-type", default="none", choices=["none", "cookie", "token", "bearer"], help="MinDoc 认证方式")
    parser.add_argument("--auth-value", default="", help="认证值：Cookie / token / bearer token")
    parser.add_argument("--cookie", default="", help="快捷传入 Cookie，等价于 --auth-type cookie --auth-value xxx")
    parser.add_argument("--token", default="", help="快捷传入 URL token，等价于 --auth-type token --auth-value xxx")

    parser.add_argument("--upload", action="store_true", help="爬取后上传到 WISE")
    parser.add_argument("--wise-base-url", default="", help="WISE API 基础地址，例如 http://localhost:8080/api/v1")
    parser.add_argument("--wise-token", default="", help="WISE X-API-Key")
    parser.add_argument("--wise-kb-id", default="", help="WISE 知识库 ID，例如 kb-00000001")
    parser.add_argument("--force-upload", action="store_true", help="即使 md_hash 未变化也重新上传")
    parser.add_argument("--no-inject-images", action="store_true", help="不调用 inject-image 上传图片")
    parser.add_argument("--inject-content-after-images", action="store_true", help="图片注入后，把占位符替换为 WISE 图片 URL 并调用 inject-content 重新注入内容")
    parser.add_argument("--no-poll", action="store_true", help="上传后不轮询 parse_status")
    parser.add_argument("--dry-run", action="store_true", help="只爬取生成 Markdown，不上传")

    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if args.cookie:
        args.auth_type = "cookie"
        args.auth_value = args.cookie
    elif args.token:
        args.auth_type = "token"
        args.auth_value = args.token

    output_dir = Path(args.output) / args.project.lower()
    auth = AuthConfig(args.auth_type, args.auth_value)
    crawler = MinDocCrawler(args.project, output_dir, auth)

    print("\n" + "=" * 72)
    print("MinDoc -> Markdown -> WISE 知识库入库 V2")
    print(f"项目: {args.project}")
    print(f"输出目录: {output_dir}")
    print("=" * 72 + "\n")

    docs = crawler.crawl_tree(
        args.entry,
        root_url=args.root_url,
        include_path=args.include,
        max_pages=args.max_pages,
    )

    total = len(docs)
    new_count = sum(1 for d in docs if d.sync_status == "new")
    updated_count = sum(1 for d in docs if d.sync_status == "updated")
    skipped_count = sum(1 for d in docs if d.sync_status == "skipped")
    image_count = sum(len(d.image_refs) for d in docs)

    print("\n" + "=" * 72)
    print("爬取完成")
    print(f"总文档: {total}, 新增: {new_count}, 更新: {updated_count}, 未变化: {skipped_count}, 图片: {image_count}")
    print(f"Markdown 输出: {output_dir}")
    print(f"映射表: {crawler.mapping_path}")
    print("=" * 72)

    if not args.upload or args.dry_run:
        return 0

    missing = [name for name, value in {
        "--wise-base-url": args.wise_base_url,
        "--wise-token": args.wise_token,
        "--wise-kb-id": args.wise_kb_id,
    }.items() if not value]
    if missing:
        print(f"❌ 缺少 WISE 参数: {', '.join(missing)}", file=sys.stderr)
        return 2

    client = WiseClient(args.wise_base_url, args.wise_token, args.wise_kb_id)

    print("\n" + "=" * 72)
    print(f"开始上传到 WISE: {args.wise_kb_id}")
    print("=" * 72)

    uploaded = 0
    failed = 0
    injected_success = 0
    injected_failed = 0

    for idx, doc in enumerate(docs, start=1):
        should_upload = args.force_upload or doc.sync_status in {"new", "updated"} or not doc.wise_knowledge_id
        if not should_upload:
            print(f"[{idx:3d}/{len(docs)}] ⏭ 跳过上传: {doc.title}")
            continue

        print(f"[{idx:3d}/{len(docs)}] 📤 上传: {doc.title}")
        doc = upload_doc_to_wise(
            doc,
            client,
            inject_images=not args.no_inject_images,
            inject_content_after_images=args.inject_content_after_images,
            poll=not args.no_poll,
        )

        if doc.sync_status == "upload_failed":
            failed += 1
            print(f"      ❌ 失败: {doc.error_message[:300]}")
        else:
            uploaded += 1
            ok_images = sum(1 for r in doc.image_refs if r.inject_status == "success")
            bad_images = sum(1 for r in doc.image_refs if r.inject_status == "failed")
            injected_success += ok_images
            injected_failed += bad_images
            print(f"      ✅ knowledge_id={doc.wise_knowledge_id}, parse_status={doc.parse_status or 'not_polled'}, 图片成功={ok_images}, 图片失败={bad_images}")

        crawler.mapping[doc.document_key] = doc.to_mapping_dict()
        crawler.save_mapping()
        time.sleep(REQUEST_INTERVAL_SECONDS)

    print("\n" + "=" * 72)
    print("WISE 上传完成")
    print(f"上传成功文档: {uploaded}")
    print(f"上传失败文档: {failed}")
    print(f"图片注入成功: {injected_success}")
    print(f"图片注入失败: {injected_failed}")
    print("=" * 72)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

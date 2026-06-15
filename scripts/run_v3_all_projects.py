#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
V3 批量爬取 + 上传 + 性能对比记录
自动遍历第2页 10 个项目，逐个爬取并上传到 WISE，记录各阶段耗时用于 V2 vs V3 对比。
"""

import json
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# ================================
# 配置
# ================================

WISE_BASE_URL = "https://wise.cvte.com/api/v1"
WISE_TOKEN = "sk-3eDlHuWOjExJ0upYVl6ISJkEV5F72AgY_K8MaSpcz_xFw_e-"
WISE_KB_ID = "2fe60de0-3c43-4edb-aeff-7990519e3459"

SCRIPT = str(Path(__file__).parent / "crawl_mindoc_wise_v3.py")
OUTPUT_BASE = str(Path(__file__).parent.parent / "knowledge-base")

# 第2页的 10 个项目
PROJECTS = [
    ("csb", "CSB", "https://docs.cvte.com/docs/csb"),
    ("plateform_dev_rule", "开发规范文档", "https://docs.cvte.com/docs/plateform_dev_rule"),
    ("unify_todo", "统一待办", "https://docs.cvte.com/docs/unify_todo"),
    ("tzv16", "天舟云帮助文档_2023", "https://docs.cvte.com/docs/tzv16"),
    ("portal", "【门户】帮助文档", "https://docs.cvte.com/docs/portal"),
    ("fee", "天舟云-前端工程化", "https://docs.cvte.com/docs/fee"),
    ("khronos", "通用业务配置-柯罗诺斯", "https://docs.cvte.com/docs/khronos"),
    ("event_bus", "消息总线", "https://docs.cvte.com/docs/event-bus"),
    ("statemachine", "状态机引擎", "https://docs.cvte.com/docs/statemachine"),
    ("lowcode", "旧天舟云帮助文档", "https://docs.cvte.com/docs/lowcode"),
]

# ================================
# 性能记录
# ================================

PERF_LOG = Path(__file__).parent / "perf_comparison_v3.json"

results = {
    "script": "V3",
    "run_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    "wise_kb": WISE_KB_ID,
    "projects": [],
    "summary": {"total_projects": 0, "total_docs": 0, "total_crawl_s": 0, "total_upload_s": 0, "total_elapsed_s": 0},
}


def run_project(dir_name: str, project_name: str, entry_url: str) -> dict:
    print(f"\n{'='*72}")
    print(f"📦 开始处理项目: {project_name} ({dir_name})")
    print(f"   URL: {entry_url}")
    print(f"{'='*72}\n")

    output_dir = f"{OUTPUT_BASE}/{dir_name}"
    total_start = time.time()

    # Phase 1: 爬取
    crawl_start = time.time()
    cmd = [
        sys.executable, SCRIPT,
        "--project", project_name,
        "--entry", entry_url,
        "--output", OUTPUT_BASE,
        "--concurrency", "10",
        "--delay", "0.05",
        "--timeout", "30",
        "--resume",
    ]
    print(f">>> 执行: {' '.join(cmd)}")
    crawl_result = subprocess.run(cmd, capture_output=False)
    crawl_elapsed = time.time() - crawl_start

    if crawl_result.returncode != 0:
        print(f"❌ 爬取阶段失败 (returncode={crawl_result.returncode})")
        return {
            "project": project_name,
            "entry": entry_url,
            "crawl_seconds": round(crawl_elapsed, 1),
            "upload_seconds": 0,
            "total_seconds": round(time.time() - total_start, 1),
            "status": "crawl_failed",
        }

    # Phase 2: 上传到 WISE
    upload_start = time.time()
    upload_cmd = [
        sys.executable, SCRIPT,
        "--project", project_name,
        "--entry", entry_url,
        "--output", OUTPUT_BASE,
        "--concurrency", "10",
        "--delay", "0.05",
        "--timeout", "30",
        "--resume",
        "--upload",
        "--wise-base-url", WISE_BASE_URL,
        "--wise-token", WISE_TOKEN,
        "--wise-kb-id", WISE_KB_ID,
        "--upload-concurrency", "5",
    ]
    print(f">>> 上传: {' '.join(upload_cmd)}")
    upload_result = subprocess.run(upload_cmd, capture_output=False)
    upload_elapsed = time.time() - upload_start
    total_elapsed = time.time() - total_start

    status = "success" if upload_result.returncode == 0 else "upload_failed"

    return {
        "project": project_name,
        "dir": dir_name,
        "entry": entry_url,
        "crawl_seconds": round(crawl_elapsed, 1),
        "upload_seconds": round(upload_elapsed, 1),
        "total_seconds": round(total_elapsed, 1),
        "status": status,
    }


def main():
    print("\n" + "=" * 72)
    print("MinDoc V3 批量爬取 + WISE 上传 — 性能对比记录")
    print(f"时间: {results['run_at']}")
    print(f"WISE 知识库: {WISE_KB_ID}")
    print(f"项目数量: {len(PROJECTS)}")
    print("=" * 72)

    total_docs = 0
    total_crawl = 0.0
    total_upload = 0.0
    total_elapsed = 0.0

    for i, (dir_name, project_name, entry_url) in enumerate(PROJECTS, 1):
        print(f"\n[{i}/{len(PROJECTS)}] 开始第 {i} 个项目: {project_name}")
        proj_result = run_project(dir_name, project_name, entry_url)
        results["projects"].append(proj_result)
        total_crawl += proj_result["crawl_seconds"]
        total_upload += proj_result["upload_seconds"]
        total_elapsed += proj_result["total_seconds"]
        print(f"[{i}/{len(PROJECTS)}] ✅ {project_name}: 爬取 {proj_result['crawl_seconds']}s + 上传 {proj_result['upload_seconds']}s = {proj_result['total_seconds']}s")

    results["summary"] = {
        "total_projects": len(PROJECTS),
        "total_docs": total_docs,
        "total_crawl_s": round(total_crawl, 1),
        "total_upload_s": round(total_upload, 1),
        "total_elapsed_s": round(total_elapsed, 1),
    }

    # 保存性能日志
    PERF_LOG.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n性能记录已保存: {PERF_LOG}")

    print("\n" + "=" * 72)
    print("🏁 全部完成！")
    print(f"   项目数:     {len(PROJECTS)}")
    print(f"   爬取总耗时: {results['summary']['total_crawl_s']}s")
    print(f"   上传总耗时: {results['summary']['total_upload_s']}s")
    print(f"   总耗时:     {results['summary']['total_elapsed_s']}s")
    print("=" * 72)


if __name__ == "__main__":
    main()
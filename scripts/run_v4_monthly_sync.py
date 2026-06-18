#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
V4 每月定时增量同步入口脚本。

运行一次会遍历所有 MinDoc 项目：
1. 爬取文档并计算 md_hash
2. md_hash 未变化：跳过，不动 WISE 原知识
3. md_hash 变化：上传新版本，解析完成后删除 WISE 旧知识
4. 新文档：直接上传到 WISE

定时能力建议交给系统 cron / launchd / CI 定时任务，而不是让 Python 常驻后台。
"""

import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# ================================
# 配置：敏感信息通过环境变量传入，不要硬编码到代码里
# ================================

WISE_BASE_URL = os.getenv("WISE_BASE_URL", "https://wise.cvte.com/api/v1")
WISE_TOKEN = os.getenv("WISE_TOKEN", "")
WISE_KB_ID = os.getenv("WISE_KB_ID", "")
MINDOC_COOKIE = os.getenv("MINDOC_COOKIE", "")

SCRIPT = str(Path(__file__).parent / "crawl_mindoc_wise_v4_incremental_delete.py")
OUTPUT_BASE = os.getenv("KNOWLEDGE_OUTPUT_BASE", str(Path(__file__).parent.parent / "knowledge-base"))

PROJECTS = [
    # ---- 第1页 ----
    ("dap", "行政餐饮运维知识库", "https://docs.cvte.com/docs/dap"),
    ("wmp", "wmp运维文档", "https://docs.cvte.com/docs/wmp"),
    ("cplm-doc", "研发运维知识库", "https://docs.cvte.com/docs/cplm-doc"),
    ("dp_ai_ops", "平台统一问答运维助手", "https://docs.cvte.com/docs/dp_ai_ops"),
    ("taskDeck", "任务同步平台", "https://docs.cvte.com/docs/taskDeck"),
    ("dp-i18n", "多语言集成", "https://docs.cvte.com/docs/dp-i18n"),
    ("tzc_wf_integrate", "天舟云集成流程", "https://docs.cvte.com/docs/tzc_wf_integrate"),
    ("tz-mobile", "移动端tz-mobile", "https://docs.cvte.com/docs/tz-mobile"),
    ("cos", "cosp对接文档", "https://docs.cvte.com/docs/cos"),
    ("aiops", "AIOPS项目文档", "https://docs.cvte.com/docs/aiops"),
    ("hw_lcp_deps_help", "华外天舟云私有化部署运维手册", "https://docs.cvte.com/docs/hw_lcp_deps_help"),
    ("tzc_qa", "天舟云咨询问答", "https://docs.cvte.com/docs/tzc_qa"),
    ("mes-01", "MES产品手册", "https://docs.cvte.com/docs/mes-01"),
    ("tzdoc_v2", "天舟云运维文档", "https://docs.cvte.com/docs/tzdoc_v2"),
    ("tzserver_v2", "天舟云帮助文档Plus", "https://docs.cvte.com/docs/tzserver_v2"),
    ("tzc_open_platform", "天舟云开放平台", "https://docs.cvte.com/docs/tzc_open_platform"),
    ("eus-operation-manual", "万象操作手册", "https://docs.cvte.com/docs/eus-operation-manual"),
    ("oa", "OA", "https://docs.cvte.com/docs/oa"),
    # ---- 第2页 ----
    ("csb", "CSB", "https://docs.cvte.com/docs/csb"),
    ("plateform_dev_rule", "开发规范文档", "https://docs.cvte.com/docs/plateform_dev_rule"),
    ("unify_todo", "统一待办", "https://docs.cvte.com/docs/unify_todo"),
    ("tzv16", "天舟云帮助文档_2023", "https://docs.cvte.com/docs/tzv16"),
    ("portal", "门户帮助文档", "https://docs.cvte.com/docs/portal"),
    ("fee", "天舟云-前端工程化", "https://docs.cvte.com/docs/fee"),
    ("khronos", "通用业务配置-柯罗诺斯", "https://docs.cvte.com/docs/khronos"),
    ("event_bus", "消息总线", "https://docs.cvte.com/docs/event-bus"),
    ("statemachine", "状态机引擎", "https://docs.cvte.com/docs/statemachine"),
    ("lowcode", "旧天舟云帮助文档", "https://docs.cvte.com/docs/lowcode"),
]

# ================================
# 同步记录
# ================================

SYNC_LOG = Path(__file__).parent / "monthly_sync_log_v4.json"

results = {
    "script": "V4-monthly-sync",
    "run_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    "wise_kb": WISE_KB_ID,
    "projects": [],
    "summary": {"total_projects": 0, "success": 0, "failed": 0, "total_elapsed_s": 0},
}


def mask_secret(value: str, left: int = 4, right: int = 4) -> str:
    if not value:
        return ""
    if len(value) <= left + right:
        return "*" * len(value)
    return value[:left] + "***" + value[-right:]


def validate_config() -> None:
    missing = []
    if not WISE_TOKEN:
        missing.append("WISE_TOKEN")
    if not WISE_KB_ID:
        missing.append("WISE_KB_ID")
    if missing:
        raise SystemExit(f"缺少环境变量: {', '.join(missing)}。请先 export 后再运行。")


def run_project(dir_name: str, project_name: str, entry_url: str) -> dict:
    print(f"\n{'='*72}")
    print(f"📦 开始增量同步项目: {project_name} ({dir_name})")
    print(f"   URL: {entry_url}")
    print(f"{'='*72}\n")

    start = time.time()
    cmd = [
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
        "--delete-old-on-update",
    ]
    if MINDOC_COOKIE:
        cmd.extend(["--cookie", MINDOC_COOKIE])

    printable_cmd = [mask_secret(x) if x == WISE_TOKEN else x for x in cmd]
    print(f">>> 执行: {' '.join(printable_cmd)}")

    result = subprocess.run(cmd, capture_output=False)
    elapsed = time.time() - start
    status = "success" if result.returncode == 0 else "failed"

    return {
        "project": project_name,
        "dir": dir_name,
        "entry": entry_url,
        "seconds": round(elapsed, 1),
        "status": status,
        "returncode": result.returncode,
    }


def main() -> int:
    validate_config()

    print("\n" + "=" * 72)
    print("MinDoc -> WISE V4 每月定时增量同步")
    print(f"时间: {results['run_at']}")
    print(f"WISE 知识库: {WISE_KB_ID}")
    print(f"项目数量: {len(PROJECTS)}")
    print("=" * 72)

    total_elapsed = 0.0
    success = 0
    failed = 0

    for i, (dir_name, project_name, entry_url) in enumerate(PROJECTS, 1):
        print(f"\n[{i}/{len(PROJECTS)}] 开始第 {i} 个项目: {project_name}")
        proj_result = run_project(dir_name, project_name, entry_url)
        results["projects"].append(proj_result)
        total_elapsed += proj_result["seconds"]
        if proj_result["status"] == "success":
            success += 1
        else:
            failed += 1
        print(f"[{i}/{len(PROJECTS)}] {proj_result['status']} {project_name}: {proj_result['seconds']}s")

    results["summary"] = {
        "total_projects": len(PROJECTS),
        "success": success,
        "failed": failed,
        "total_elapsed_s": round(total_elapsed, 1),
    }

    SYNC_LOG.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n同步记录已保存: {SYNC_LOG}")

    print("\n" + "=" * 72)
    print("🏁 本次增量同步完成")
    print(f"   项目数: {len(PROJECTS)}")
    print(f"   成功:   {success}")
    print(f"   失败:   {failed}")
    print(f"   总耗时: {round(total_elapsed, 1)}s")
    print("=" * 72)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())

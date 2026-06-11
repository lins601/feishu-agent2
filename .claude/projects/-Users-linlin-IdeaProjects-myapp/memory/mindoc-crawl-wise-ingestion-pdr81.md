---
name: mindoc-crawl-wise-ingestion-pdr81
description: MinDoc爬虫 + WISE文件入库的实现状态，按PDR V8.1规范已完成的组件和待处理事项
metadata:
  type: project
  date: 2026-06-10
---

# MinDoc 爬虫与 WISE 文件入库实现状态

## ✅ 已完成

### 知识文件生成（2026-06-10）
- ✅ **50篇 MinDoc 文档已爬取并转换为 Markdown**
- ✅ **全部上传到 WISE 知识库**（知识库ID: `2fe60de0-3c43-4edb-aeff-7990519e3459`）
- ✅ **系统概述知识文件** — 包含完整 MFG MinDoc 目录结构
- ✅ 所有 Markdown 文件符合 PDR V8.1 Front Matter 规范

### 爬虫脚本 (`scripts/crawl_mindoc.py`)
- ✅ Cookie + AJAX 认证方式可获取 MinDoc 页面正文
- ✅ URL 规范化（去除 token/timestamp/utm 参数、锚点、尾部斜杠、域名小写）
- ✅ url_hash = SHA256(normalized_url)[:12]
- ✅ md_hash = SHA256(markdown_content)[:8]
- ✅ document_key = project_name + "_" + url_hash
- ✅ 从入口页解析侧边栏文档链接
- ✅ Markdown Front Matter 生成
- ✅ WISE File API 上传（multipart form-data）
- ✅ 文档映射表管理（JSON 文件）

### Java 服务
- ✅ `MindocDocumentMapping.java` — 文档映射表实体
- ✅ `WiseFileIngestionResult.java` — 文件入库结果 DTO
- ✅ `WiseFileIngestionService.java` — 文件上传 + parse_status 轮询服务

## 📊 WISE 知识库状态

| 项目 | 状态 |
|------|------|
| 知识库 ID | `2fe60de0-3c43-4edb-aeff-7990519e3459` |
| 知识库名称 | `wise`（需重命名为制造文档知识库） |
| 已上传文件 | 50 篇 Markdown 文档 |
| 解析状态 | 30 pending + 20 processing（WISE 正在解析中） |
| 飞书机器人 WISE Agent ID | `3c49c79e-6586-4bba-bde3-b11f142a52ec` |

### 知识库建议
按照 PDR V8.1 建议，需要建立两个知识库：
1. **制造文档知识库**（当前 `wise` 库） — 存放 50 篇 SOP/操作手册/异常处理文档
2. **制造 FAQ 知识库** — 存放高频问答和标准问答

### 文档分类分布
- **QMS（质量管理）**: 约 20+ 篇（含物料质量、制程质量、质量保证、市场质量、FAQ）
- **WMS（仓储管理）**: 约 15 篇（入库管理、库内管理 FAQ）
- **MES（生产管理）**: 较少（当前 SOP 主要为目录页）
- **通用/跨系统**: 系统概述、场景解决方案等

## 当前状态
- ✅ MinDoc 爬取 → 完成
- ✅ Markdown 转换 → 完成
- ✅ WISE 上传 → 完成
- ⏳ WISE 解析 → 进行中（30 pending + 20 processing）
- ❌ FAQ 知识库 → 未建立
- ❌ 飞书多维表格 → 未配置

## PDR 待实现（后续阶段）
- [ ] 等待 WISE 解析完成，验证问答命中
- [ ] 建立制造 FAQ 知识库
- [ ] 配置飞书多维表格（qa_record / feedback_record / miss_question / sync_record）
- [ ] 定时任务调度（Spring Scheduler）
- [ ] 同步失败告警通知
- [ ] 知识下线流程

**Why:** PDR V8.1 项目核心知识同步模块完成状态。50篇文档已完成从爬取到入库的全部流程。
**How to apply:** 等待 WISE 解析完成后，可配置飞书机器人通过 WiseAgentService 查询知识库进行问答。

Related memories: [[cvte-mfg-mindoc-knowledge-base]]
# 制造知识助手 PDR V8.0（最终答辩精简版）

## 1. 项目概述
### 项目背景
制造企业内部 WMS、QMS、MES 等系统问题频繁出现，知识分散在 MinDoc 中，员工依赖人工答疑，效率低。

### 项目目标
构建飞书制造知识助手，实现：
- 飞书群聊问答
- WISE知识检索
- MinDoc自动同步
- FAQ沉淀
- 用户反馈闭环

### 核心价值
- 降低重复答疑成本
- 提升问题处理效率
- 加速新员工学习
- 建立知识运营闭环

---

## 2. 方案选型

### 为什么选择 WISE
不自建RAG。

| 方案 | 优势 | 劣势 |
|------|------|------|
| 自建RAG | 灵活 | 周期长、复杂 |
| WISE | 成熟稳定 | 定制性较弱 |

最终选择：WISE。

### 为什么选择 MinDoc
采用：

MinDoc → Markdown → WISE File API

优势：
- 自动同步
- 支持增量更新
- 易维护

### 为什么选择飞书机器人
- 零学习成本
- 用户已在飞书办公
- 群聊即可使用

---

## 3. 业务分析

### WMS
仓储管理：入库、出库、库存、盘点。

### QMS
质量管理：IQC、IPQC、OQC、8D、CAPA。

### MES
制造执行：工单、排产、报工、完工入库。

### 跨系统场景
- IQC退货（QMS+WMS）
- 工单缺料（MES+WMS）
- 成品入库（MES+QMS+WMS）

---

## 4. 总体架构

飞书机器人
↓
Spring Boot
↓
WISE
↓
MinDoc

核心职责：
- 飞书：消息入口
- Spring Boot：Agent编排
- WISE：知识检索问答
- MinDoc：知识来源

---

## 5. 核心设计

### Session设计

session_key：

chatId_userId

规则：
- 群聊隔离
- 用户隔离
- 30分钟超时
- 追问复用Session

### qa_task异步任务

状态：

PENDING
↓
RUNNING
↓
SUCCESS / FAILED / TIMEOUT

特点：
- 一问一任务
- 一问一回复
- message_id绑定

### 群聊并发设计

示例：

张三：IQC提交失败
李四：工单无法报工
王五：库存异常

系统生成：

task001
task002
task003

独立执行。

### MinDoc同步设计

MinDoc
↓
目录树获取
↓
正文抓取
↓
Markdown转换
↓
Hash比较
↓
WISE上传
↓
问答可用

### FAQ沉淀

未命中问题
↓
人工确认
↓
FAQ生成
↓
WISE入库

---

## 6. 数据设计

### conversation_session

| 字段 | 说明 |
|------|------|
| session_key | 会话键 |
| wise_session_id | WISE会话 |
| last_active_time | 最后活跃时间 |

### qa_task

| 字段 | 说明 |
|------|------|
| task_id | 任务ID |
| message_id | 消息ID |
| question | 用户问题 |
| status | 任务状态 |

### mindoc_document_mapping

| 字段 | 说明 |
|------|------|
| doc_id | 文档ID |
| md_hash | 内容Hash |
| wise_file_id | WISE文件ID |

---

## 7. 异常处理

### 问答超时

30秒：处理中提示

120秒：降级回复

180秒：任务超时

### 同步异常

- 重试3次
- 记录日志
- 飞书告警

### WISE异常

返回统一兜底话术。

---

## 8. 项目亮点

### 亮点1
制造领域专属知识助手

### 亮点2
MinDoc自动同步

### 亮点3
知识驱动，拒绝AI幻觉

### 亮点4
群聊并发隔离

### 亮点5
qa_task异步机制

### 亮点6
知识闭环运营

### 亮点7
一个月内可交付

---

## 9. Demo验收方案

### 场景1
IQC提交失败

### 场景2
工单无法报工

### 场景3
FAQ命中

### 场景4
未命中处理

### 场景5
用户反馈

### 场景6
MinDoc同步

验收标准：

- 能问
- 能查
- 能答
- 能反馈
- 能同步

---

## 10. 项目价值总结

本项目不是建设新的知识库平台。

而是在公司现有WISE能力基础上，建设制造知识统一入口。

形成：

MinDoc → WISE → 飞书机器人

实现知识生产、知识使用、知识运营闭环。

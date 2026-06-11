# 制造知识助手 · 产品设计说明书（PDR）V8.1 最终答辩修正版

> 基于 WISE 线上知识库 + MinDoc 文档爬虫 + Agent + 飞书机器人的制造知识问答系统  
> 版本：v8.1 最终答辩修正版  
> 日期：2026-06-10  
> 周期：1 个月  
> 交付形式：飞书机器人 Demo + 技术方案 + 知识库样例 + 运营记录表 + 演示脚本

---

## 一、产品概述

### 1.1 一句话说明

制造知识助手是一个面向 WMS、QMS、MES 制造业务系统的飞书机器人。

产线操作员、新员工、老员工、IT 开发人员在飞书群里 @机器人提问后，系统通过 WISE 线上知识库进行检索和问答，在 3 分钟内返回包含“问题原因、处理步骤、关联系统、注意事项、知识来源”的结构化答案。

---

### 1.2 项目背景

公司内部存在多套制造相关系统：

- WMS：仓储管理系统
- QMS：质量管理系统
- MES：制造执行系统

产线每天会出现大量系统操作问题，例如：

- IQC 检验单为什么提交不了？
- WMS 入库单状态为什么异常？
- MES 工单为什么无法报工？
- 不合格品怎么退货？
- 8D 报告为什么无法关闭？
- 库存批次为什么查不到？
- 工单发不出去怎么办？
- 成品入库为什么失败？

当前主要依赖人工解答：

- 老员工在飞书群被频繁 @，重复回答同类问题
- 新员工不知道去哪查文档，也不清楚业务流程和状态机
- SOP 文档分散在 MinDoc 的多个空间中
- IT 排查问题缺少业务上下文，沟通成本高
- 高频问题反复出现，但缺少自动沉淀机制

因此需要建设一个飞书制造知识助手，通过定时爬虫将 MinDoc 在线文档转成 Markdown 文件，并以文件形式自动上传到 WISE 知识库，让用户通过自然语言提问即可获得标准答案。

---

### 1.3 类比理解

```text
4S 店维修车间        →   电子制造工厂
发动机/底盘/电气      →   WMS/QMS/MES
故障码 P0171         →   “IQC 检验单提交失败”
维修手册              →   WISE 知识库文档
老师傅翻手册 10 分钟   →   AI 助手 3 分钟返回标准步骤
```

本项目要做的不是重新写一本维修手册，而是让员工在飞书里问一句，就能自动查到最相关的维修手册内容，并整理成可执行步骤。

---

### 1.4 产品定位

本项目定位为：

```text
基于 WISE 线上知识库能力的制造知识助手 Agent。
```

本项目不是重新开发一个知识库平台，而是在公司已有 WISE 线上知识库能力之上，构建一个面向制造场景的 Agent 问答入口。

项目重点建设：

- 飞书机器人交互
- WISE 知识库接入
- MinDoc 在线文档爬取、Markdown 转换与 WISE 文件入库
- 文件 / Markdown / FAQ 知识沉淀
- Agent 问答流程编排
- 用户反馈闭环
- 未命中问题沉淀
- 知识质量审核
- 异常兜底与运营监控

项目不重点建设：

- 本地 PostgreSQL 知识库
- 本地 pgvector 向量库
- 本地 Redis 热点缓存（不缓存知识正文、向量结果或问答答案；Redis 仅用于短期运行态任务控制）
- 自研 Embedding 生成
- 自研 Chunk 分块
- 自研 RAG 检索框架
- 自研知识管理后台
- 自研组织权限体系
- 通用大规模爬虫系统（本项目只做受控 MinDoc 定时爬取）

核心原则：

```text
平台能力优先，线上知识库优先，机器人只做入口和编排。
```

#### 运行态存储边界

本项目不使用 Redis / Caffeine 缓存知识正文、向量结果或问答答案。

Redis 仅用于短期运行态控制：

- `message_id` 幂等去重；
- `qa_task` 短期任务状态；
- 异步任务锁；
- 任务超时控制；
- `session_key` 与 `wise_session_id` 的短期映射。

Caffeine 仅作为可选的本进程短期配置缓存，例如飞书 access_token、WISE token、标签字典等，不承载知识内容和问答结果。知识正文、检索、向量化、问答能力仍全部由 WISE 负责。


---

## 二、目标用户与核心价值

### 2.1 目标用户

| 角色 | 痛点 | 期望 |
|---|---|---|
| 产线操作员 | 遇到系统报错不会处理，等待人工回复时间长 | 在飞书群 @机器人，快速拿到操作步骤 |
| 新入职员工 | 不熟悉 WMS/QMS/MES 业务流程和状态机 | 用自然语言提问即可知道问题卡点 |
| 老员工 / 导师 | 高频问题反复被问，打断工作 | 高频问题沉淀进知识库，减少重复答疑 |
| IT / 开发 | 排查问题时缺少业务上下文 | 获取关联系统、状态、流程、知识来源 |
| 知识管理员 | 不知道哪些知识缺失、过期或质量差 | 查看未命中、差评、高频问题，持续补充知识 |

---

### 2.2 核心价值

#### 对产线操作员

- 不需要翻文档
- 不需要等待老员工回复
- 直接在飞书群获得处理步骤
- 遇到问题能先自助处理，减少停线等待

#### 对新员工

- 快速理解 WMS/QMS/MES 流程
- 通过问答学习业务规则
- 降低培训成本
- 减少因流程不熟造成的误操作

#### 对老员工 / 导师

- 减少重复问题打扰
- 高频问题自动沉淀
- 只处理机器人无法回答的问题
- 将经验沉淀为可复用知识

#### 对 IT / 开发

- 获得更完整的问题上下文
- 快速定位是 WMS、QMS 还是 MES 问题
- 减少无效沟通
- 提高问题排查效率

#### 对知识管理员

- 通过未命中问题发现知识盲区
- 通过用户反馈发现低质量知识
- 通过 MinDoc 定时爬虫与 Markdown 文件入库降低维护成本
- 形成持续运营的数据闭环

---

## 三、项目目标与设计原则

### 3.1 核心目标

在飞书群中 @ 机器人，用自然语言提问，系统能够在 3 分钟内返回带步骤的结构化答案。

标准问题示例：

```text
@制造知识助手 IQC 检验单提交失败怎么办？
```

机器人返回：

```text
📋【问题】
IQC 检验单提交失败

🔎【可能原因】
1. 检验结果未填写完整
2. 当前状态不允许提交
3. 关联来料单状态异常

🛠【处理步骤】
1. 进入 QMS → IQC 检验单页面
2. 检查检验结果是否填写完整
3. 检查单据状态是否为“待提交”
4. 检查关联来料单是否已关闭
5. 重新提交

🔗【关联系统】
QMS / WMS

📚【知识来源】
WISE：IQC 检验单操作规范

💬【反馈】
回复“有用”或“没用”，帮助我们优化知识库。
```

---

### 3.2 分阶段目标

| 阶段 | 目标 | 优先级 |
|---|---|---|
| P0 | 飞书机器人主链路可用，能接收消息、调用知识库、返回答案 | 必须完成 |
| P0 | 准确性优先，无知识依据时不编造答案 | 必须完成 |
| P1 | 支持 MinDoc 定时同步与自动入库，减少逐条人工维护 | 重点完成 |
| P1 | 支持高频 FAQ 沉淀和相似问法命中 | 重点完成 |
| P1 | 支持用户反馈和未命中问题闭环 | 重点完成 |
| P2 | 支持知识质量监控、评估、周报/月报 | 可扩展完成 |
| P2 | 支持 MCP / Agent Tools 扩展业务系统查询 | 后续扩展 |

---

### 3.3 权衡目标

| 目标 | 优先级 | 说明 |
|---|---|---|
| 准确率 | P0 | 答错可能影响产线操作，宁可不答不能瞎答 |
| 可用性 | P0 | 用户发消息后必须收到回复，不能石沉大海 |
| 响应速度 | P1 | 3 分钟内必须有结果，理想状态 30 秒内返回 |
| 覆盖度 | P1 | WMS/QMS/MES 高频问题都需要逐步收录 |
| 知识保鲜 | P1 | 系统升级后旧答案必须及时标记或下线 |
| 可维护性 | P1 | 知识更新主要来自 MinDoc，通过定时同步到 WISE 完成，机器人不保存知识正文，只保存文档映射表 |

---

### 3.4 设计原则

| 原则 | 说明 |
|---|---|
| 平台优先 | WISE 已具备知识管理、解析、分块、检索、问答能力，优先复用 |
| 线上优先 | 不依赖本地知识库、本地向量库，不在本地缓存知识正文、向量结果或问答答案 |
| 轻服务 | Spring Boot 只做飞书消息接入、API 编排、格式化回复和运营记录 |
| 准确优先 | 无知识依据不生成具体操作步骤，避免误导产线 |
| 可追踪 | 每次问答、反馈、未命中、知识来源都能追踪 |
| 可维护 | 知识更新通过 MinDoc 源文档维护，并定时同步到 WISE，机器人不保存知识正文副本，只保存文档映射表（source_url、normalized_url、url_hash、md_hash、wise_file_id，doc_id 可选）与知识引用 |
| 可扩展 | 后续可扩展 MCP、Agent Tools、Evaluation、业务系统查询 |

---

## 四、业务范围分析

### 4.1 WMS · 仓储管理系统

WMS 管理物料从供应商到仓库再到产线的全过程，核心关注库存、批次、库位、入库、出库和盘点。

#### 核心流程

```text
收货通知
  ↓
到货扫描
  ↓
质检判定
  ↓
上架确认
  ↓
库存更新
```


#### 常见问题

- 入库单提交不了
- 库存数量对不上
- 扫码扫不出批次
- 上架任务异常
- 出库单无法发料
- 批次库存查询失败
- 盘点差异无法确认

---

### 4.2 QMS · 品质管理系统

QMS 管理来料、制程、出货和异常质量闭环，核心关注检验、判定、不合格处理、8D、CAPA。

#### 核心流程

```text
来料登记
  ↓
抽样检验
  ↓
检验判定
  ↓
合格入库 / 不合格退货 / 让步接收
```



#### 常见问题

- IQC 检验单提交失败
- OQC 判定异常
- 不合格品不知道怎么退货
- 8D 超期未关闭
- CAPA 流程卡住
- 检验结果无法保存
- 抽样数量异常

---

### 4.3 MES · 制造执行系统

MES 管理工单、排产、生产执行、报工和完工入库，核心关注工单状态、齐套、过站、报工、完工。

#### 核心流程

```text
计划下达
  ↓
工单创建
  ↓
BOM 展开
  ↓
物料齐套
  ↓
工单发放
  ↓
生产执行
  ↓
报工
  ↓
完工入库
```



#### 常见问题

- 工单发不出去
- 工单无法报工
- SMT 贴错料
- 工单状态异常
- 完工入库失败
- 工单关闭失败
- 物料齐套检查失败

---

### 4.4 跨系统问题

制造问题经常跨系统发生，机器人回答时必须提示关联系统和前后依赖关系。

| 场景 | 涉及系统 | 回答要求 |
|---|---|---|
| IQC 不合格退货 | QMS + WMS | 说明 QMS 判定和 WMS 退料执行关系 |
| 报工发现不良 | MES + QMS | 说明 MES 报工与 QMS 异常单关系 |
| 批次追溯 | WMS + QMS | 说明库存批次和检验记录对应关系 |
| 成品入库 | MES + QMS + WMS | 说明完工、OQC、入库的前后依赖 |
| 工单缺料 | MES + WMS | 说明工单齐套和仓库发料关系 |

跨系统业务链路示例：

```text
供应商
  ↓
WMS 入库
  ↓
QMS IQC 检验
  ↓
WMS 发料
  ↓
MES 领料生产
  ↓
QMS IPQC 巡检
  ↓
QMS OQC 检验
  ↓
WMS 成品入库 / QMS 8D 异常处理
```

---

## 五、用户故事与验收标准

### US-01 智能问答

用户在飞书群或私聊中 @制造知识助手并提出问题。

#### 示例

```text
@制造知识助手 IQC 检验单为什么提交不了？
```

#### 系统返回

```text
【问题】
IQC 检验单提交失败

【可能原因】
1. 检验结果未填写完整
2. 当前状态不允许提交
3. 关联来料单状态异常

【处理步骤】
1. 检查检验结果是否填写完整
2. 检查单据状态是否为“待提交”
3. 检查关联来料单是否已关闭
4. 重新提交

【关联系统】
QMS / WMS

【知识来源】
WISE：IQC 检验单操作规范
```

#### 验收标准

- 能接收飞书群聊 @ 消息
- 能提取用户问题
- 能返回结构化答案
- 响应时间不超过 3 分钟

---

### US-02 相似问法命中

用户使用不同表达方式描述同一个问题时，机器人仍能命中同一条知识。

#### 示例

```text
IQC 检验单提交失败
IQC 单子交不上去
IQC 报错提交不了
IQC 交不上怎么办
IQC 提交按钮点了没反应
```

#### 实现方式

通过 WISE FAQ 能力维护：

```text
实现方式：

系统优先调用 WISE FAQ 能力进行标准问 / 相似问匹配。

如果 FAQ 命中，则直接返回 FAQ 标准答案。

如果 FAQ 未命中，则继续调用 WISE 知识库检索能力，从 MinDoc 同步入库的 Markdown 文档中检索相关知识片段，并由 Agent 生成结构化答案。

如果 FAQ 和知识库均未命中，则不编造答案，记录到未命中问题表，后续由管理员补充 FAQ 或文档知识。
```

#### 验收标准

- 至少支持 10 条高频问题的相似问法
- 相似问能命中标准问


---

### US-03 未命中处理

当知识库没有返回明确答案时，系统不编造答案。

#### 机器人回复

```text
当前知识库中暂未收录该问题。

为避免误导产线操作，本次不直接给出处理步骤。
已记录您的问题，并提交管理员补充。
```

#### 系统动作

- 写入飞书多维表格「未命中问题表」
- 记录问题、用户、群聊、时间
- 统计出现次数
- 后续进入知识补充流程

#### 验收标准

- 无知识依据时不生成操作步骤
- 未命中问题可被管理员查看
- 同一问题重复出现时累计次数

---

### US-04 用户反馈

用户查看机器人答案后，可以反馈答案是否有帮助。

#### 支持反馈方式

```text
有用
没用
👍
👎
```

#### 系统动作

- 识别用户反馈
- 写入飞书多维表格「反馈记录表」
- 多次负反馈触发知识审核任务

#### 验收标准

- 正向反馈可记录
- 负向反馈可记录
- 反馈能关联问题和知识来源
- 负反馈达到阈值时能生成待审核任务

---

### US-05 MinDoc 文档爬取、Markdown 转换与 WISE 文件入库

#### 用户故事

作为知识管理员，我不希望逐条手工复制 MinDoc 页面内容或手工上传文件，也不希望把 MinDoc 页面直接以 URL 形式导入 WISE。

我希望系统能够在公司网络环境下定时访问企业员工日常使用的 MinDoc 内网 URL，将 MinDoc 页面正文解析并转换为标准 Markdown 文件，然后通过 WISE 文件入库接口上传到线上 WISE 知识库。系统需要在文档映射表（`mindoc_document_mapping`）中保留原始访问 URL、规范化 URL、URL Hash、标题、Markdown 内容 Hash、WISE 文件 ID 等信息。MinDoc `doc_id` 如果能从页面、接口或 URL 中解析到，则作为辅助字段保存；如果拿不到，不影响同步流程。

#### 设计说明

本项目的 US-05 不再使用“URL 自动发现 + URL 入库”的方式，也不把 MinDoc URL 直接交给 WISE 解析。实际实现方式是：**系统在公司网络中访问 MinDoc URL，抓取页面正文，转成 Markdown 文件，再通过 WISE File API 以文件形式上传**。

考虑到企业员工通常是通过公司内网 URL 访问 MinDoc，且爬虫不一定能稳定获取 MinDoc `doc_id`，因此本项目不强依赖 `doc_id`。文档唯一标识改为：

```text
document_key = project_name + normalized_url
url_hash = SHA256(normalized_url)
```

其中：

- `source_url`：管理员配置或爬虫发现的原始访问 URL；
- `normalized_url`：去除无意义参数、锚点、末尾斜杠后的规范化 URL；
- `url_hash`：对 `normalized_url` 计算 Hash 后得到的稳定标识；
- `doc_id`：可选字段，能获取则保存，获取不到则为空；
- `md_hash`：对转换后的 Markdown 正文计算 Hash，用于判断内容是否变化。

US-05 的准确实现方式是：

```text
MinDoc 企业内网 URL
        ↓
定时任务读取飞书多维表格配置
        ↓
在公司网络环境下访问 source_url
        ↓
获取页面正文，或从入口页解析同域 / 同路径下的文档链接
        ↓
逐篇抓取文档正文
        ↓
生成 normalized_url 和 url_hash
        ↓
转换为 Markdown 文件
        ↓
在 Markdown Front Matter 中写入 source_url、normalized_url、url_hash、doc_id（可选）、md_hash
        ↓
通过 document_key / url_hash 查文档映射表
        ↓
md_hash 变化或新 URL：通过 WISE File API 以文件形式上传
        ↓
md_hash 未变化：跳过
        ↓
WISE 解析 Markdown 文件
        ↓
知识进入 WISE 检索与问答链路
```

#### MinDoc 同步配置表

MinDoc 同步配置存放在飞书多维表格中。知识管理员只需维护项目名、入口 URL、同步范围和同步频率即可。

不需要复杂的知识源配置表，也不需要 Sitemap、Wiki、Confluence 等外部系统支持。

MinDoc 同步配置表：

| 字段 | 说明 |
|---|---|
| config_id | 配置 ID |
| project_name | 项目名称，例如 QMS / MES / WMS |
| source_url | MinDoc 入口 URL 或指定文档 URL，通常是公司内网可访问地址 |
| root_url | 同步根 URL，可与 source_url 相同；用于限定爬取范围 |
| include_path | 允许爬取的 URL 路径前缀，例如 `/docs/qms/` |
| exclude_path | 排除路径，例如登录页、搜索页、评论页、附件下载页 |
| auth_type | 访问方式，例如 Cookie、Token、内网免登录、人工导出 |
| sync_cron | 同步频率 |
| last_sync_time | 上次同步时间 |
| status | ENABLE / DISABLE |

示例配置：

| 项目 | source_url | root_url / include_path |
|---|---|---|
| QMS | `http://mindoc.company.local/docs/qms` | `/docs/qms/` |
| MES | `http://mindoc.company.local/docs/mes` | `/docs/mes/` |
| WMS | `http://mindoc.company.local/docs/wms` | `/docs/wms/` |

#### URL 规范化规则

为了避免同一篇文档因为 URL 参数不同而被重复入库，系统在入库前需要生成 `normalized_url`。

| 处理项 | 规则 |
|---|---|
| 协议与域名 | 保留协议、域名和端口 |
| 路径 | 保留路径，去除末尾多余 `/` |
| Query 参数 | 去除 `utm_*`、`from`、`timestamp`、`token` 等无意义参数 |
| 锚点 | 去除 `#section`，避免同页不同锚点被识别为不同文档 |
| 大小写 | 域名统一小写，路径按系统实际情况处理 |
| 编码 | URL Decode 后再统一编码 |

示例：

```text
source_url:
http://mindoc.company.local/docs/qms/iqc-submit?from=feishu#step2

normalized_url:
http://mindoc.company.local/docs/qms/iqc-submit

url_hash:
SHA256(normalized_url)
```

#### 爬取与转换流程

```text
定时任务触发
        ↓
读取飞书多维表格 MinDoc 同步配置
        ↓
在公司网络环境下访问 source_url
        ↓
携带 Cookie / Token / 内网访问凭证
        ↓
解析入口页中的文档链接，限定在 root_url / include_path 范围内
        ↓
逐篇获取文档正文
        ↓
提取标题、正文、更新时间、可选 doc_id
        ↓
生成 normalized_url、url_hash、document_key
        ↓
转换为 Markdown
        ↓
计算 md_hash
        ↓
与文档映射表对比 document_key / url_hash / md_hash
        ↓
md_hash 相同：跳过
        ↓
md_hash 不同：重新上传 WISE，更新 wise_file_id
        ↓
新 URL：上传 WISE，新增映射记录
        ↓
连续多次访问不到：标记 expired
        ↓
记录同步状态
```

#### Markdown 文件格式

每一篇 MinDoc 文档转换为一个 Markdown 文件。文件头部保留源文档元数据，便于追踪、更新和排查。

```markdown
---
source: MinDoc
project_name: QMS
source_url: http://mindoc.company.local/docs/qms/iqc-submit
normalized_url: http://mindoc.company.local/docs/qms/iqc-submit
url_hash: 8c9f2a...
doc_id: null
title: IQC 检验单提交失败处理规范
system_type: QMS
md_hash: A8F3D...
crawled_at: 2026-06-10 02:00:00
---

# IQC 检验单提交失败处理规范

正文内容……
```

说明：

- `doc_id` 不是必填项；
- 如果页面中能解析到 MinDoc 文档 ID，则写入真实 `doc_id`；
- 如果无法获取，则写入 `null` 或不写该字段；
- 追踪和更新主要依赖 `normalized_url / url_hash / md_hash`。

#### WISE 入库方式

系统不调用 WISE Markdown 文件入库接口，而是调用 WISE 文件入库能力，将生成的 `.md` 文件作为文件上传。

```text
POST /api/v1/knowledge-bases/{knowledge_base_id}/knowledge/file
```

请求方式为 multipart file upload，文件类型为 Markdown。

上传成功后，系统记录 WISE 返回的 `wise_file_id`，写入文档映射表（`mindoc_document_mapping`）。

#### 文档映射表

系统维护一张文档映射表，用于追踪每篇 MinDoc 页面与 WISE 文件之间的对应关系，并判断文档是否需要更新。

```text
mindoc_document_mapping
```

| 字段 | 说明 |
|---|---|
| mapping_id | 映射记录 ID，主键 |
| project_name | MinDoc 空间或项目名称 |
| document_key | 文档唯一键，建议为 `project_name + url_hash` |
| source_url | 原始 MinDoc URL，用于追溯来源 |
| normalized_url | 规范化后的 URL，用于稳定匹配 |
| url_hash | normalized_url 的 Hash |
| doc_id | MinDoc 文档 ID，可为空 |
| title | 文档标题 |
| md_hash | 转换后 Markdown 内容的 Hash |
| wise_file_id | 当前生效的 WISE 文件 ID |
| old_wise_file_id | 上一个版本的 WISE 文件 ID，用于回滚或下线 |
| sync_status | 同步状态 |
| parse_status | WISE 解析状态 |
| last_seen_time | 最近一次在 MinDoc 中成功访问到的时间 |
| last_sync_time | 最近同步时间 |
| error_message | 失败原因 |

示例：

```text
project_name=QMS
document_key=QMS_8c9f2a...
source_url=http://mindoc.company.local/docs/qms/iqc-submit
normalized_url=http://mindoc.company.local/docs/qms/iqc-submit
url_hash=8c9f2a...
doc_id=null
md_hash=A8F3D...
wise_file_id=wise_file_abc123
```

#### 更新判断逻辑

每次同步时：

```text
获取最新页面
     ↓
生成 normalized_url 和 url_hash
     ↓
转 Markdown
     ↓
计算 md_hash
     ↓
通过 document_key / url_hash 查映射表
```

- `document_key / url_hash` 不存在 → 新文档，上传并新增记录；
- `document_key / url_hash` 存在，且 `md_hash` 相同 → 内容未变化，跳过；
- `document_key / url_hash` 存在，但 `md_hash` 变化 → 重新上传 WISE，更新 `wise_file_id`；
- `source_url` 能访问但 `title` 变化 → 更新标题；
- URL 连续多次访问不到 → 标记 `expired`，进入人工确认或下线流程。

#### 更新判断规则

系统通过文档映射表（`mindoc_document_mapping`）来判断新增、更新、跳过和失效。判断方式为 **project_name + normalized_url + md_hash**。其中 `project_name + normalized_url` 用于识别同一篇文档，`md_hash` 用于判断正文是否变化，`doc_id` 只作为可选辅助字段。

| 判断维度 | 用途 |
|---|---|
| project_name + normalized_url | 文档唯一匹配依据，适合企业内网 URL 访问场景 |
| url_hash | normalized_url 的 Hash，便于作为表字段和文件名使用 |
| doc_id | 可选辅助字段，能获取则保存，不能获取不影响同步 |
| title | 文档标题，辅助排查文档重命名 |
| md_hash | Markdown 内容 Hash，判断正文是否变化的核心依据 |
| wise_file_id | 关联 WISE 中的文件 ID |
| last_seen_time | 判断源文档是否仍然存在 |

#### 自动判断规则

| 文档状态 | 判断方式 | 系统动作 |
|---|---|---|
| 新增文档 | `document_key / url_hash` 不存在于映射表 | 爬取、转 Markdown、上传 WISE、新增映射记录 |
| 内容更新 | `document_key / url_hash` 存在，但 `md_hash` 变化 | 重新生成 Markdown，上传 WISE，更新 wise_file_id |
| 内容未变化 | `md_hash` 一致 | 跳过，不重复上传 |
| 标题变化 | `document_key / url_hash` 相同但 title 变化 | 更新映射记录中的 title |
| URL 变化 | 旧 URL 访问不到，新 URL 内容相同或标题高度相似 | 记录为疑似迁移，交由管理员确认是否合并 |
| 源文档删除 | 连续多次访问不到 | 标记 expired，并通知管理员 |
| 权限失败 | 返回 401 / 403 或跳转登录页 | 标记 auth_failed |
| 转换失败 | HTML 无法解析或 Markdown 为空 | 标记 convert_failed |
| WISE 上传失败 | File API 返回错误 | 重试，仍失败则告警 |
| WISE 解析失败 | parse_status = failed | 记录原因，进入人工兜底 |

#### WISE 旧文件处理机制

当 MinDoc 源文档内容变化导致 `md_hash` 变化时，系统不能只上传新文件而保留旧文件继续生效，否则 WISE 检索可能命中过期知识。

处理规则如下：

```text
发现 md_hash 变化
        ↓
生成新的 Markdown 文件
        ↓
上传 WISE，得到 new_wise_file_id
        ↓
轮询 new_wise_file_id 的 parse_status
        ↓
completed：映射表切换到 new_wise_file_id，并将 old_wise_file_id 标记 expired / disabled
failed：保留 old_wise_file_id 继续生效，记录错误并通知管理员
```

如果 WISE 提供删除或下线接口，系统在新文件解析成功后调用下线接口处理旧文件；如果暂不支持自动下线，则在映射表中标记 `old_wise_file_id` 为 `pending_offline`，由知识管理员在 WISE 后台人工下线。这样可以避免新版本解析失败时知识不可用，也能避免旧知识长期污染检索结果。

#### 验收标准

- 系统支持在飞书多维表格中配置 MinDoc 内网入口 URL；
- 系统可以在公司网络环境下访问 MinDoc 页面；
- 系统可以从入口 URL 解析同域 / 同路径下的文档链接，或同步管理员指定的 URL 列表；
- 系统可以将 MinDoc 页面内容转换为 Markdown 文件；
- 系统可以在 Markdown 文件中保留原始 MinDoc URL 和 normalized_url；
- 系统不强依赖 MinDoc doc_id，doc_id 获取不到时仍可同步；
- 系统可以通过 normalized_url / url_hash + md_hash 判断新增和更新；
- 系统可以通过 WISE File API 以文件形式上传 Markdown；
- 系统不使用 WISE Markdown 文件入库接口；
- 系统不依赖 Wiki、Confluence、Sitemap 或 URL 自动发现；
- 同一篇 MinDoc 页面 hash 未变化时不会重复入库；
- 源文档更新后（hash 变化），系统可以重新生成 Markdown 并更新 WISE 知识；
- 源文档删除或失效后，系统可以标记为 expired；
- 入库成功后的 Markdown 知识可以被飞书机器人问答命中；
- 同步失败时可以记录失败原因并通知管理员。

#### 边界说明

MinDoc 文档同步是“企业内网 URL 访问 + 定时爬取 + 文件入库”自动化，不是“直接 URL 入库”，也不是通用互联网爬虫。

系统可以自动完成：

```text
读取飞书配置表 → 访问 MinDoc 内网 URL → 解析文档链接 → 抓取正文 → 转 Markdown → 生成 url_hash 和 md_hash → 上传 WISE → 更新映射表
```

但以下情况仍需要人工兜底：

- 爬虫服务不在公司网络内，无法访问 MinDoc 内网地址；
- MinDoc 登录态过期；
- MinDoc 页面必须通过浏览器动态渲染，普通 HTTP 请求拿不到正文；
- MinDoc 页面结构变化导致解析失败；
- 文档 URL 大规模变更，无法自动判断新旧文档对应关系；
- 文档正文主要由图片、表格或流程图构成，自动转换效果较差；
- 文档本身内容过期或缺少关键步骤；
- WISE 文件解析失败且重试后仍无法恢复。

### US-06 FAQ 自动沉淀

未命中问题、高频问题、负反馈问题经过管理员确认后，可生成 FAQ 并写入 WISE FAQ 知识库。

#### 流程

```text
未命中 / 差评 / 高频问题
  ↓
飞书多维表格待处理
  ↓
AI 辅助整理标准问、相似问、答案
  ↓
管理员确认
  ↓
调用 FAQ API 写入 WISE
  ↓
后续相似问可以命中
```

#### 验收标准

- 可从未命中问题生成 FAQ 草稿
- 管理员确认后写入知识库
- 后续相似问可以命中

---

### US-07 知识质量监控

当同一知识连续收到负反馈时，系统标记为待审核。

#### 触发条件

```text
同一知识来源连续 3 次收到“没用”
```

#### 系统动作

- 创建知识审核任务
- 通知知识管理员
- 管理员检查后更新 WISE 知识

#### 验收标准

- 可统计负反馈次数
- 可生成待审核任务
- 可在飞书多维表格中跟踪处理状态

---

### US-08 知识维护

知识管理员定期维护 WISE 中的知识内容。

#### 支持维护方式

- MinDoc 重新爬取并生成 Markdown 文件
- MinDoc 重新爬取与 Markdown 文件重新解析
- FAQ 修改
- 标签调整
- 过期知识下线
- 手工 Markdown 知识补充

#### 验收标准

- 知识内容统一在 WISE 管理
- 知识更新后机器人可以使用最新内容

---

## 六、整体架构设计

### 6.1 架构设计原则

本项目采用线上优先、平台优先、轻服务架构。

```text
飞书机器人负责入口
Spring Boot 负责编排
WISE 负责知识库与问答
飞书多维表格负责运营记录
```

知识内容统一维护在 WISE 知识库。
机器人不维护本地知识库，不进行本地向量化，不建设向量数据库。

---
## 6.2 总体架构图

本项目采用“飞书机器人入口 + Spring Boot 编排服务 + WISE 线上知识库 + 飞书多维表格运营闭环”的架构。

系统不自建本地知识库、不自建向量库、不自研 Embedding、不自研 RAG。
知识解析、切分、向量化、检索、问答能力统一由 WISE 平台负责。

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                              外部系统                                    │
│                                                                         │
│  ┌──────────────────┐  ┌────────────────────┐  ┌───────────────────┐  │
│  │ 飞书开放平台       │  │ WISE 知识库       │  │ MinDoc 在线文档源   │  │
│  │ · WebSocket       │  │ · Knowledge API     │  │ · MinDoc 文档       │  │
│  │ · 事件回调         │  │ · Chat API          │  │ · 文档目录树        │  │
│  │ · 消息推送         │  │ · Search API        │  │ · 文档正文          │  │
│  │ · 富文本回复       │  │ · FAQ API           │  │ · Markdown 转换源   │  │
│  │ · 多维表格         │  │ · Agent Tools       │  │ · SOP / FAQ 文档    │  │
│  └────────┬─────────┘  └──────────┬─────────┘  └─────────┬─────────┘  │
└───────────┼───────────────────────┼──────────────────────┼────────────┘
            │                       │                      │
            │ 飞书消息 / 反馈        │ 问答 / 检索 / 入库     │ MinDoc 文档 / Markdown
            ▼                       ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              制造知识助手 Manufacturing Agent Service                    │
│              Spring Boot 3.x + Java 17 + Docker                         │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ 消息层 FeishuMessageService                                        │  │
│  │ 技术：Feishu Java SDK / WebSocket / Event Callback / @Async         │  │
│  │ 职责：接收消息、@机器人判断、文本提取、消息去重、反馈识别、           │  │
│  │      为每个提问创建 qa_task、按 message_id 定向回复、超时兜底        │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ 会话层 SessionService                                              │  │
│  │ 技术：WISE Session API / conversation_session 缓存表                  │  │
│  │ 职责：session_key=chatId_userId 隔离、追问检测（特征词+30分钟）、     │  │
│  │      追问复用 session、新问题不带上下文、30 分钟超时覆盖旧 session    │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ 理解层 QuestionUnderstandingAgent                                  │  │
│  │ 技术：规则关键词 + LLM 理解 / LangChain4j                           │  │
│  │ 职责：识别 WMS/QMS/MES/跨系统、提取关键词、口语转标准检索词           │  │
│  │ 示例：“IQC 单子交不上去” → “IQC 检验单提交失败”                    │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ 检索层 WiseSearchService / RetrievalOrchestrator                    │  │
│  │ 技术：WISE Search API + Agent Tools                       │  │
│  │ 职责：knowledge_search、read_chunks、expand_context、find_documents │  │
│  │      按标签过滤 WMS/QMS/MES，返回 Top-N 知识片段和 references        │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ 生成层 AnswerGenerationAgent / RichTextBuilder                      │  │
│  │ 技术：LLM 结构化生成 + 飞书 post 富文本                              │  │
│  │ 职责：生成原因、步骤、注意事项、关联系统、知识来源、反馈提示          │  │
│  │ 规则：无 references 不输出具体操作步骤，低可信自动进入兜底回复        │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ 知识同步层 KnowledgeSyncService                                     │  │
│  │ 技术：Spring Scheduler / WISE File API / MinDoc API                    │  │
│  │ 职责：定时读取飞书配置表、爬取 MinDoc 目录树、转 Markdown、            │  │
│  │      计算 hash 判断新增/更新/跳过、文件入库、轮询 parse_status          │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ 运营记录层 LarkBitableService                                       │  │
│  │ 技术：飞书多维表格 API                                               │  │
│  │ 职责：记录问答、反馈、未命中、知识审核、MinDoc 同步状态                │  │
│  │ 表格：qa_record / feedback_record / miss_question /                   │  │
│  │      knowledge_review / sync_record                                   │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ 兜底监控层 FallbackMonitorService                                   │  │
│  │ 技术：超时控制 / 重试机制 / 飞书告警群 / 降级话术模板                 │  │
│  │ 职责：API 超时重试、入库失败告警、parse_status 超时告警、系统兜底回复 │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
            │                       │
            │ 结构化答案             │ REST API / SSE / Agent Tools
            ▼                       ▼
┌──────────────────────────────┐   ┌──────────────────────────────────────┐
│          飞书用户侧            │   │        WISE 知识平台         │
│ · 富文本答案                  │   │ · File / Manual / FAQ 入库             │
│ · 有用 / 没用反馈             │   │ · Chunk 切分                           │
│ · 未命中提示                  │   │ · Embedding 生成                       │
│ · 系统兜底回复                │   │ · 向量检索 / 关键词检索 / Rerank        │
└──────────────────────────────┘   │ · FAQ 命中                             │
                                   │ · Chat / Agent 问答                    │
                                   │ · parse_status 状态跟踪                │
                                   └──────────────────────────────────────┘
```

---

## 6.3 各模块职责说明

| 模块             | 技术选型                                                  | 核心职责                    |
| -------------- | ----------------------------------------------------- | ----------------------- |
| 飞书开放平台         | 飞书机器人、WebSocket、事件回调、消息 API、多维表格 API                  | 接收用户消息、发送机器人回复、承载运营记录表  |
| WISE | Knowledge API、File API、Search API、Chat API、FAQ API、Agent Tools | Markdown 文件入库、文档解析、检索、问答、FAQ 命中 |
| MinDoc 在线文档源 | MinDoc 页面 / 文档目录 / 文档正文 | 提供制造相关原始文档，由爬虫转为 Markdown |
| 消息层            | Feishu Java SDK、@Async                                | 接收飞书消息、去重、识别反馈、创建 qa_task、按 message_id 定向回复 |
| 会话层            | Session API、conversation_session 缓存表、追问检测 | 管理多轮对话，追问复用 session，新问题不带上下文，30 分钟超时覆盖 |
| 理解层            | 规则关键词 + LLM / LangChain4j                             | 识别问题所属系统和意图，生成标准检索词     |
| 检索层            | Search API、Agent Tools                                | 调用知识库检索，获取知识片段和引用来源     |
| 生成层            | LLM、飞书 post 富文本                                       | 生成结构化答案，包含原因、步骤、注意事项、来源 |
| 知识同步层 | Spring Scheduler、MinDoc API、Markdown Converter、WISE File API | 读取飞书配置表、爬取 MinDoc 目录树、转 Markdown、hash 比对、文件入库、轮询解析状态 |
| 运营记录层          | 飞书多维表格 API                                            | 记录问答(qa_record)、反馈(feedback_record)、未命中(miss_question)、审核(knowledge_review)、同步(sync_record)   |
| 兜底监控层          | 超时控制、重试、飞书告警                                          | 保证异常情况下用户仍能收到回复         |

---

## 6.4 核心数据流

### 6.4.1 用户问答流

```text
用户 @机器人提问
  ↓
飞书开放平台推送消息
  ↓
消息层提取文本并去重
  ↓
创建 qa_task（PENDING），记录 message_id
  ↓
立即回复原消息："已收到，正在查询知识库…"
  ↓
异步执行（task → RUNNING）
  ↓
追问判断：追问 → 复用 session / 新问题 → 不带上下文
  ↓
理解层识别 WMS / QMS / MES 意图
  ↓
检索层调用 WISE Search / Agent Tools
  ↓
生成层基于 references 生成结构化答案
  ↓
回复原 message_id（定向回复）
  ↓
更新 qa_task 为 SUCCESS
  ↓
运营记录层写入问答记录

qa_task 是系统为每一条用户提问创建的问答任务记录，用于跟踪问题处理状态、绑定飞书原始 message_id、支持异步执行、超时控制和失败重试。

message_id 是飞书为每条用户消息生成的唯一标识。系统通过 message_id 进行消息去重，并在问答完成后按 message_id 定向回复原消息，避免群聊多人并发提问时回复错位。

异步执行是指系统收到用户问题后，不在飞书事件回调线程中直接完成全部问答流程，而是先创建 qa_task 并立即回复“已收到，正在查询知识库…”，随后由后台线程或任务队列执行 WISE 检索、Agent 生成和最终回复。这样可以避免接口超时，提高用户体验，并支持多个问题并发处理。
```

### 6.4.2 MinDoc 文档爬取与文件入库流

```text
定时任务触发
  ↓
读取飞书多维表格 MinDoc 同步配置
  ↓
获取 MinDoc 目录树
  ↓
递归遍历所有文档
  ↓
逐篇爬取文档正文
  ↓
转换为 Markdown 文件
  ↓
计算 md_hash
  ↓
与文档映射表比对 hash
  ↓
hash 相同：跳过
hash 不同：重新上传 WISE，更新 wise_file_id
document_key / url_hash 不存在：上传 WISE，新增映射记录
  ↓
调用 WISE File API 上传 Markdown 文件
  ↓
返回 wise_file_id
  ↓
轮询 parse_status
  ↓
completed：知识可被问答检索
failed：记录失败原因并通知管理员
```

### 6.4.3 反馈治理流

```text
用户回复“有用 / 没用”
  ↓
消息层识别为反馈
  ↓
运营记录层写入反馈表
  ↓
负反馈累计达到阈值
  ↓
生成知识审核任务
  ↓
知识管理员修正 WISE 知识
```

---

## 6.5 架构边界说明

| 本项目负责                | WISE 负责      | 本项目不负责       |
| -------------------- | ---------------------- | ------------ |
| 飞书机器人接入              | 知识库管理                  | 自建知识库        |
| 消息解析与回复              | Markdown 文件解析 | 自建向量库        |
| Agent 流程编排           | Chunk 切分               | 自研 Embedding |
| 调用 Chat / Search API | Embedding 生成           | 自研 RAG 检索框架  |
| 定时爬取 MinDoc 文档           | 向量检索 / 关键词检索           | 大规模爬虫系统      |
| 调用 WISE File API 上传 Markdown          | Rerank                 | 复杂权限系统       |
| 记录问答和反馈              | Chat / Agent 问答        | 保存知识正文副本     |
| 未命中和差评闭环             | FAQ 命中                 | 替代正式审批流程     |

                          




---

## 七、技术选型

| 模块 | 技术 | 说明 |
|---|---|---|
| 服务端语言 | Java 17 | 与企业后端技术栈兼容 |
| 服务框架 | Spring Boot 3.x | 用于飞书事件处理和 API 编排 |
| Agent 框架 | LangChain4j / 自定义编排 | 可用于问题理解、工具调用、回答生成 |
| 大模型 | DeepSeek / OpenAI / 公司内置模型 | 问题理解、答案生成、FAQ 草稿生成 |
| IM 入口 | 飞书机器人 | 用户提问入口，支持群聊和私聊 |
| 飞书连接 | WebSocket 长连接 / 事件回调 | SDK 自带重连，便于消息接收 |
| 知识平台 | WISE | 统一知识库、Markdown 文件入库、检索、问答 |
| 问答接口 | `/chat/{session_id}` | 主链路，支持 RAG / Agent |
| 会话接口 | `/sessions` | 管理多轮对话 |
| 知识入库 | `/knowledge/file`、`/knowledge/manual` | MinDoc 转 Markdown 后以文件形式入库，支持手工知识 |
| FAQ 管理 | `/faq/entry`、`/faq/entries`、`/faq/search` | 高频问答沉淀 |
| 标签管理 | `/tags` | WMS/QMS/MES 分类 |
| 运营记录 | 飞书多维表格 | 问答、反馈、未命中、审核任务 |
| 运行态任务存储 | Redis | message_id 幂等、qa_task 短期状态、任务锁、超时控制、session 短期映射 |
| 本地配置缓存 | Caffeine（可选） | 仅缓存 token、标签字典等短期配置，不缓存知识正文和问答答案 |
| 部署方式 | Docker / 公司线上环境 | 逻辑无状态；运行态状态由 Redis 承载，运营记录由飞书多维表格承载 |
| 扩展能力 | MCP / Skills / Agent Tools | 后续接入业务系统查询 |

---

## 八、核心模块设计

### 8.1 FeishuMessageService

#### 职责

- 接收飞书消息
- 判断是否 @ 机器人
- 提取纯文本问题
- 消息去重
- 区分提问与反馈
- 为每个提问创建独立 qa_task
- 异步执行问答流程
- 按 message_id 定向回复飞书消息

#### 输入

```text
飞书消息事件

包含：

- message_id
- chat_id
- user_id
- content
- create_time

消息来源：

飞书开放平台 WebSocket 长连接推送。
```

#### 输出

```text
飞书富文本回复（引用原消息 message_id）
```

#### 核心原则

```text
异步回答不按用户维度回复，而按 message_id / qa_task_id 回复。
```

每条用户提问生成一个独立 qa_task，异步任务完成后只回复自己对应的 message_id，确保多个问题并发时不会乱序、不会互相覆盖。

#### 异常与兜底

| 异常 | 策略 |
|---|---|
| 消息重复 | 通过 message_id 幂等去重 |
| 非文本消息 | 回复”暂仅支持文字提问，请用文字描述您的问题” |
| 空消息 | 忽略，不记录未命中 |
| 纯表情 | 如果是 👍/👎 则识别为反馈，否则忽略 |
| 消息过长 | 截断前 500 字并提示 |
| 快速连续提问 | 每条问题独立创建 qa_task，并行处理，各自回复原消息 |
| 飞书发送失败 | 重试 3 次 |
| 飞书 Token 失效 | 刷新 Token 后重试 |
| 飞书限流 | 指数退避重试 |
| WebSocket 断连 | SDK 重连 + 告警 |

#### 问答任务表 (qa_task)

`qa_task` 是每条用户提问对应的运行态任务记录，用于异步处理、状态追踪、超时控制和定向回复。

Demo 阶段建议将 `qa_task` 存放在 Redis 中，设置 TTL，例如 24 小时或 72 小时。长期审计、统计和运营分析不依赖 `qa_task`，而是写入飞书多维表格 `qa_record`。

| 数据类型 | 存储位置 | 用途 | 生命周期 |
|---|---|---|---|
| qa_task 运行态 | Redis Hash | 异步执行、状态流转、绑定 message_id、超时控制 | 短期 TTL |
| qa_record 审计态 | 飞书多维表格 | 问答记录、反馈关联、未命中统计、运营分析 | 长期保留 |

Redis Hash 字段建议如下：

| 字段 | 说明 |
|---|---|
| task_id | 问答任务 ID，主键 |
| message_id | 飞书原始消息 ID，用于定向回复 |
| bot_reply_message_id | 机器人答案消息 ID，用于反馈反查 |
| chat_id | 群聊 ID |
| user_id | 用户 ID |
| session_key | chat_id + user_id 组合键 |
| wise_session_id | WISE 会话 ID |
| question | 用户问题 |
| is_follow_up | 是否追问（true / false） |
| parent_task_id | 追问关联的上一条任务 ID |
| status | PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT / CANCELLED |
| answer | 机器人答案摘要 |
| created_at | 创建时间 |
| updated_at | 更新时间 |

任务状态流转：

```text
PENDING → RUNNING → SUCCESS / FAILED / TIMEOUT / CANCELLED
```

示例——同一用户连续发送 3 个问题：

```text
task_001：IQC 提交失败       → message_001 → RUNNING
task_002：工单无法报工       → message_002 → RUNNING
task_003：成品入库失败       → message_003 → RUNNING
```

三个任务独立执行、独立回复原消息，即使 `task_002` 先完成也不会覆盖 `task_001` 的回复。

---

### 8.2 WiseSessionService

#### 职责

- 为飞书用户创建 session
- 维护 session_key 与 wise_session_id 的映射
- 判断用户新消息是"追问"还是"新问题"
- 追问时复用 session 上下文，新问题时不带历史上下文
- 会话超时后重新创建（覆盖旧记录，不无限增长）

#### Session Key 设计

Session 隔离维度为 `chatId + "_" + userId`，确保同一群聊中不同用户的对话完全隔离。

```text
sessionKey = chatId + "_" + userId
```

示例：

```text
群 chat_123 中：

张三 → chat_123_user_001 → session_1
李四 → chat_123_user_002 → session_2
王五 → chat_123_user_003 → session_3
```

虽然三人在同一个群 `chat_123`，但 session 完全隔离，不会出现 A 说了"IQC 提交失败"后 B 说"那怎么办"导致上下文混乱的问题。

#### 追问判断

用户新消息到达时，系统判断是追问还是新问题：

判断条件（需同时满足）：

```text
同一 session_key（chat_id + user_id）
30 分钟内
问题包含追问特征词
```

追问特征词：

```text
那、这个、上面、刚才、继续、还有、如果这样、第二步、
上一个、前面说的、接着、然后呢
```

判断结果：

| 判断 | is_follow_up | session 处理 | 上下文 |
|---|---|---|---|
| 追问 | true | 复用 wise_session_id | 带最近一轮上下文 |
| 新问题 | false | 新建独立 task | 不带历史上下文 |
| 超过 30 分钟 | false | 新建 session + 新建 task | 不带历史上下文 |

这样即使同一用户在 30 分钟内连续提问，只要不是追问，就不会带入上一条问题的上下文，避免污染。

#### 会话策略

| 场景 | session 维度 |
|---|---|
| 私聊 | user_id |
| 群聊 | chat_id + user_id |
| 追问（30 分钟内 + 追问特征） | 复用 session，带上下文 |
| 新问题（非追问） | 新建独立 task，不带上下文 |
| 超过 30 分钟无交互 | 创建新 session，覆盖旧记录 |
| 用户明确换话题 | 新建 session |
| 群聊多人同时提问 | 使用 chat_id + user_id 隔离 |

#### Session 缓存表

```text
conversation_session
```

| 字段 | 说明 |
|---|---|
| session_key | chat_id + user_id 组合键 |
| wise_session_id | WISE 会话 ID |
| last_active_time | 最后活跃时间 |

数据库中始终只有**一个用户一条会话记录**，不会无限增长。

#### 超时处理流程

```text
收到消息
      ↓
查 conversation_session
      ↓
30 分钟内 + 追问特征 → 复用 wise_session_id，is_follow_up=true
      ↓
30 分钟内 + 非追问 → 新建 task，不带上下文，is_follow_up=false
      ↓
超过 30 分钟 → 调用 WISE Create Session 创建新 session
      ↓
覆盖旧 wise_session_id
```

#### 异常与兜底

| 异常 | 策略 |
|---|---|
| session_key 不存在 | 创建新 session 并写入缓存表 |
| 创建 session 失败 | 返回系统繁忙 |
| 会话上下文污染 | 超时重建 |
| 多人群聊上下文混乱 | 使用 chat_id + user_id 隔离 |
| session 过期 | 自动创建新 session 后覆盖旧记录 |

---

### 8.3 QuestionUnderstandingAgent

#### 职责

将用户口语化的提问转化为结构化检索请求。

#### 示例

用户输入：

```text
IQC 单子交不上去怎么办？
```

Agent 输出：

```json
{
  "intent": "QMS",
  "keywords": ["IQC", "检验单", "提交失败"],
  "search_query": "IQC 检验单提交失败处理方法",
  "tags": ["QMS", "IQC", "异常处理"],
  "is_cross_system": false
}
```

#### 设计要点

| 项目 | 方案 |
|---|---|
| 意图识别 | LLM 理解 + 关键词辅助 |
| 系统识别 | WMS / QMS / MES / 跨系统 |
| 口语化处理 | 将“交不上去”转换为“提交失败” |
| 上下文管理 | 最近 3 轮完整对话 + 历史摘要压缩 |
| 低可信处理 | 如果意图不明确，则扩大检索范围 |

---

### 8.4 WiseSearchService

#### 职责

- 调用 WISE Chat API
- 调用 Agent Tools
- 传入 query、knowledge_base_ids、agent_id、channel
- 解析 SSE 流式响应
- 提取 answer
- 提取 references
- 判断 fallback / error / no reference

#### 主接口示例

```http
POST /api/v1/chat/{session_id}
```

#### 请求示例

```json
{
  "query": "IQC 检验单为什么提交不了？",
  "knowledge_base_ids": ["kb_manufacturing_doc", "kb_manufacturing_faq"],
  "agent_id": "manufacturing-assistant",
  "channel": "feishu",
  "enable_memory": true
}
```

#### 响应处理

| response_type | 处理方式 |
|---|---|
| references | 保存知识引用 |
| answer | 拼接答案 |
| error | 进入兜底流程 |
| thinking | 可忽略，不展示给用户 |
| tool_call | 记录日志 |
| tool_result | 记录日志 |
| done=true | 结束流式响应 |

#### WISE Agent Tools

| Tool | 用途 | 调用时机 |
|---|---|---|
| knowledge_search | 语义检索，返回最相关知识片段 | 每次问答必调 |
| read_chunks | 读取指定知识完整 Chunk 内容 | 检索结果需要更多细节时 |
| expand_context | 扩展上下文，获取关联知识 | 跨系统问题、复杂问题 |
| find_documents | 按文档名/ID 精确定位 | 用户明确提到某文档时 |
| list_tags | 列出知识标签，辅助分类过滤 | 意图识别后缩小检索范围 |

#### 异常与兜底

| 异常 | 策略 |
|---|---|
| Chat API 超时 | 返回“正在查询中”，后台继续等待 |
| SSE 中断 | 使用已收到内容，并标记“回答可能不完整” |
| response_type=error | 调用 Knowledge Search 兜底 |
| answer 为空 | 记录未命中 |
| references 为空 | 标记低可信，不输出具体步骤 |
| WISE 不可达 | 返回系统繁忙并告警 |
| 检索分数过低 | 返回候选列表或未命中提示 |

---

### 8.5 AnswerGenerationAgent

#### 职责

将 WISE 检索结果组装为飞书可读的结构化答案。

#### 输入

```text
用户原始问题
问题理解结果
WISE 检索结果 Top-5
知识引用 references
```

#### 输出

```text
飞书富文本 post 消息
```

#### 置信度策略

| 置信度 | 策略 |
|---|---|
| ≥ 0.8 | 直接生成完整答案 |
| 0.6 ~ 0.8 | 生成答案，但顶部标注“仅供参考，请结合现场流程确认” |
| < 0.6 | 不生成具体步骤，返回候选方向或提示补充信息 |
| 无结果 | 不生成答案，走未命中流程 |
| references 为空 | 不输出操作步骤，只输出低可信兜底 |


#### 置信度来源

置信度不是单独由大模型主观判断，而是由检索结果和引用情况综合计算。

| 因素 | 说明 |
|---|---|
| FAQ 命中情况 | FAQ 精确命中可直接给高分 |
| WISE 检索分数 | Top1 文档片段的相似度分数 |
| Top1 / Top2 分数差 | 差距越大，说明答案越明确 |
| references 数量 | 是否存在可追溯知识来源 |
| 标签一致性 | 是否命中 WMS / QMS / MES 对应标签 |
| 步骤完整性 | 是否能从知识片段中提取明确处理步骤 |

Demo 阶段可以简化为：

| 场景 | confidence |
|---|---|
| FAQ 精确命中 | 0.9 |
| 文档检索 Top1 分数高且有 references | 0.8 |
| 有 references 但分数一般 | 0.6 |
| 无 references | 0 |

#### 回答模板

```text
📋【问题】
{识别后的标准问题}

🔎【可能原因】
1. {原因1}
2. {原因2}
3. {原因3}

🛠【处理步骤】
1. {步骤1}
2. {步骤2}
3. {步骤3}

🔗【关联系统】
{WMS / QMS / MES / 跨系统}

📌【注意事项】
{风险提示、权限提示、审批提示}

📚【知识来源】
{文档标题 / FAQ 标题 / 知识库名称}

💬【反馈】
回复“有用”或“没用”，帮助我们优化知识库。
```

---

### 8.6 WiseKnowledgeSyncService

#### 职责

WiseKnowledgeSyncService 负责在公司网络环境下访问 MinDoc 内网 URL，将在线 MinDoc 页面爬取为 Markdown 文件，并以文件形式上传到 WISE 知识库。

核心职责包括：

- 读取飞书多维表格 MinDoc 同步配置；
- 在公司网络环境下访问 MinDoc 内网 URL；
- 携带 Cookie / Token / 内网访问凭证访问页面；
- 从入口页解析文档链接，限定在 root_url / include_path 范围内；
- 爬取 MinDoc 文档正文；
- 清洗页面中的导航、菜单、评论、页脚等无关内容；
- 将 HTML / 页面正文转换为 Markdown；
- 生成 normalized_url、url_hash、document_key；
- 在 Markdown Front Matter 中写入 source_url、normalized_url、url_hash、doc_id（可选）、title、md_hash 等元数据；
- 生成 normalized_url / url_hash，计算 md_hash，并与文档映射表比对，判断新增、更新、跳过或失效；
- 调用 WISE File API 上传 Markdown 文件；
- 轮询 WISE parse_status；
- 记录同步结果；
- 失败时告警并进入人工兜底。

#### MinDoc 同步配置

MinDoc 同步配置存放在飞书多维表格中，字段如下：

| 字段 | 说明 |
|---|---|
| config_id | 配置 ID |
| project_name | 项目名称，例如 QMS / MES / WMS |
| source_url | MinDoc 入口 URL 或指定文档 URL，通常是公司内网可访问地址 |
| root_url | 同步根 URL，可与 source_url 相同；用于限定爬取范围 |
| include_path | 允许爬取的 URL 路径前缀 |
| exclude_path | 排除路径，例如登录页、搜索页、评论页、附件下载页 |
| auth_type | 访问方式，例如 Cookie、Token、内网免登录、人工导出 |
| sync_cron | 同步频率 |
| last_sync_time | 上次同步时间 |
| status | ENABLE / DISABLE |

#### 文档唯一标识策略

由于企业员工通常通过公司内网 URL 访问 MinDoc，且爬虫不一定能稳定获取 MinDoc `doc_id`，因此系统不强依赖 `doc_id`。文档唯一标识采用：

```text
document_key = project_name + url_hash
url_hash = SHA256(normalized_url)
```

说明：

- `project_name` 用于区分 QMS / MES / WMS 等不同 MinDoc 空间；
- `source_url` 是管理员配置或爬虫发现的原始访问 URL；
- `normalized_url` 是去掉无意义参数、锚点和末尾斜杠后的规范化 URL；
- `url_hash` 是 normalized_url 的 Hash，适合作为文件名和映射表字段；
- `doc_id` 是可选辅助字段，能获取则保存，不能获取不影响同步；
- `md_hash` 用于判断正文内容是否变化；
- 文档标题变化不代表正文变化，但需要同步更新映射表中的 `title`。

#### 文件生成规则

```text
{project_name}_{url_hash}_{safe_title}.md
```

示例：

```text
QMS_8c9f2a_IQC检验单提交失败处理规范.md
```

#### WISE 文件入库接口

系统上传 Markdown 文件到 WISE：

```http
POST /api/v1/knowledge-bases/{kb_id}/knowledge/file
```

上传内容：

```text
file = 生成的 Markdown 文件
metadata.source = MinDoc
metadata.project_name = 项目名称
metadata.source_url = 原始 MinDoc URL
metadata.normalized_url = 规范化 URL
metadata.url_hash = normalized_url 的 Hash
metadata.doc_id = MinDoc 文档 ID（可选）
metadata.md_hash = Markdown 内容 hash
```

#### 文档映射表 (mindoc_document_mapping)

| 字段 | 说明 |
|---|---|
| mapping_id | 映射记录 ID，主键 |
| project_name | MinDoc 空间或项目名称 |
| document_key | 文档唯一键，建议为 `project_name + url_hash` |
| source_url | 原始 MinDoc URL |
| normalized_url | 规范化后的 URL |
| url_hash | normalized_url 的 Hash |
| doc_id | MinDoc 文档 ID，可为空 |
| title | 文档标题 |
| md_hash | Markdown 内容 Hash |
| wise_file_id | 当前生效的 WISE 文件 ID |
| old_wise_file_id | 上一个版本的 WISE 文件 ID |
| sync_status | 同步状态 |
| parse_status | WISE 解析状态 |
| last_seen_time | 最近一次成功访问到源页面的时间 |
| last_sync_time | 最近同步时间 |
| error_message | 失败原因 |

#### 异常与兜底

| 异常 | 策略 |
|---|---|
| 爬虫服务无法访问公司内网 | 标记 `network_unreachable`，提示部署到公司网络或配置代理 |
| MinDoc 无法访问 | 标记 `source_unavailable`，通知管理员 |
| 登录态失效 | 标记 `auth_failed`，提示更新凭证 |
| 文档链接解析失败 | 重试 3 次，仍失败则告警 |
| 单篇文档爬取失败 | 记录失败，不阻塞其他文档 |
| 页面需要动态渲染 | 降级使用浏览器渲染爬取或人工导出 Markdown |
| 页面结构变化 | 标记 `parse_failed`，进入人工修复 |
| Markdown 转换为空 | 标记 `convert_failed`，不上传 WISE |
| md_hash 未变化 | 标记 `skipped`，不重复上传 |
| WISE File API 上传失败 | 重试，仍失败则标记 `upload_failed` |
| WISE 解析失败 | 标记 `parse_failed`，通知管理员 |
| 源文档删除或 URL 失效 | 标记 `expired`，进入知识下线流程 |

### 8.7 WiseFAQService

#### 职责

- 创建单条 FAQ
- 批量导入 FAQ
- 添加相似问法
- 搜索 FAQ
- 停用低质量 FAQ

#### FAQ 结构

```json
{
  "standard_question": "IQC 检验单为什么提交不了？",
  "similar_questions": [
    "IQC 单子交不上去",
    "IQC 提交失败",
    "IQC 报错提交不了"
  ],
  "negative_questions": [
    "如何修改用户名"
  ],
  "answers": [
    "请检查检验结果、单据状态和关联来料单状态。"
  ],
  "tag_id": "tag_qms_iqc",
  "is_enabled": true
}
```

#### 异常与兜底

| 异常 | 策略 |
|---|---|
| 标准问重复 | 合并到已有 FAQ |
| 相似问重复 | 去重后追加 |
| 答案为空 | 不允许入库 |
| tag_id 不存在 | 先创建标签或使用默认标签 |
| 批量导入失败 | 查询任务进度，失败项人工修正 |
| FAQ 多次被点没用 | 停用或进入待审核 |

---

### 8.8 LarkBitableService

#### 职责

- 写入问答记录
- 写入用户反馈
- 写入未命中问题
- 写入知识审核任务
- 记录 MinDoc 同步状态
- 更新处理状态
- 生成周报/月报数据

#### 表关联关系

飞书多维表格共 5 张表，关联关系如下：

```text
问答记录表 (qa_record)
      │
      ├──→ 用户反馈表 (feedback_record)    通过 qa_id 关联
      │
      └──→ 未命中问题表 (miss_question)    未命中时写入

知识库 (WISE)
      │
      └──→ 知识审核任务表 (knowledge_review)  负反馈触发审核

MinDoc 文档
      │
      └──→ MinDoc 同步记录表 (sync_record)  记录同步状态
```

#### 问答记录表 (qa_record)

| 字段 | 说明 |
|---|---|
| qa_id | 问答记录主键 |
| message_id | 飞书消息 ID |
| chat_id | 群聊 ID |
| user_id | 用户 ID |
| question | 原始问题 |
| answer_summary | 答案摘要 |
| session_id | WISE 会话 ID |
| knowledge_refs | 知识来源 |
| is_fallback | 是否兜底 |
| confidence | 置信度 |
| response_time_ms | 响应耗时 |
| created_at | 时间 |

#### 用户反馈表 (feedback_record)

| 字段 | 说明 |
|---|---|
| feedback_id | 反馈记录主键 |
| qa_id | 关联问答记录表主键 |
| user_id | 反馈用户 |
| question | 对应问题 |
| answer_summary | 对应答案 |
| knowledge_refs | 知识来源 |
| feedback | 有用 / 没用 |
| created_at | 时间 |

#### 未命中问题表 (miss_question)

| 字段 | 说明 |
|---|---|
| question | 未命中问题 |
| normalized_question | 标准化后的问题 |
| count | 出现次数 |
| status | 待补充 / 已补充 / 忽略 |
| user_id | 首次提问用户 |
| chat_id | 群聊 |
| owner | 负责人 |
| created_at | 首次出现 |
| updated_at | 最近出现 |

#### 知识审核任务表 (knowledge_review)

| 字段 | 说明 |
|---|---|
| task_id | 审核任务 ID |
| knowledge_id | 关联 WISE 知识 ID |
| trigger_reason | 触发原因 |
| question | 关联问题 |
| knowledge_refs | 关联知识 |
| negative_count | 负反馈次数 |
| status | 待审核 / 已处理 / 忽略 |
| owner | 处理人 |
| created_at | 创建时间 |

#### MinDoc 同步记录表 (sync_record)

| 字段 | 说明 |
|---|---|
| doc_id | MinDoc 文档 ID |
| url | 原始 MinDoc URL |
| title | 文档标题 |
| md_hash | Markdown 内容 Hash |
| wise_file_id | WISE 文件 ID |
| sync_status | 同步状态 |
| parse_status | WISE 解析状态 |
| update_time | 最近更新时间 |
| error_message | 失败原因 |

#### 异常与兜底

| 异常 | 策略 |
|---|---|
| 表格写入失败 | 重试 3 次 |
| 字段不存在 | 告警管理员 |
| 权限不足 | 告警管理员检查权限 |
| 未命中问题重复 | 更新 count |
| 反馈无法关联 qa_id | 回复”未找到可反馈的答案” |
| 表格不可用 | 不影响用户回复，只记录错误日志 |

---

## 九、知识库建设方案

### 9.1 知识库划分

建议在 WISE 中建立两个核心知识库：

```text
制造文档知识库 Document KB
制造 FAQ 知识库 FAQ KB
```

#### Document KB 存放

- SOP 文档
- 操作手册
- 系统流程文档
- 异常处理文档
- 业务规则说明
- 图片说明文档
- 系统状态机说明
- 审批流程说明

#### FAQ KB 存放

- 高频问题
- 标准问答
- 相似问法
- 简短操作指引
- 常见异常处理
- 未命中转化的问题
- 差评修正后的标准答案

---

### 9.2 知识来源

#### 第一层：MinDoc 在线文档

```text
MinDoc 在线 SOP
MinDoc 操作手册
MinDoc 异常处理文档
MinDoc QMS 规范
MinDoc MES 操作手册
MinDoc WMS 操作手册
```

这些文档不会以 MinDoc URL 形式直接导入 WISE，而是先由爬虫抓取正文，转换为 Markdown 文件，再通过 WISE File API 上传。

#### 第二层：业务沉淀

```text
历史 FAQ
飞书群问答
高频异常处理记录
8D 案例
IT 工单记录
老员工经验总结
```

#### 第三层：用户反馈生成

```text
未命中问题
高频重复问题
差评问题
低可信问题
```

---

### 9.3 标签体系

#### 一级标签

```text
WMS
QMS
MES
跨系统
```

#### 二级标签

```text
入库
出库
库存
IQC
IPQC
OQC
8D
CAPA
工单
排产
报工
完工入库
异常处理
```

#### 三级标签

```text
提交失败
状态异常
审批卡住
权限不足
数据不存在
批次异常
流程超期
接口失败
字段缺失
```

---

### 9.4 MinDoc 文档爬取、Markdown 转换与文件入库流程

本项目不采用“把 MinDoc URL 直接交给 WISE 入库”的方式，而是采用“在公司网络环境下访问 MinDoc 内网 URL + 抓取正文 + 转换 Markdown + 以文件形式上传 WISE”的方式。

#### 知识来源

第一阶段知识来源统一为 MinDoc。

```text
MinDoc 企业内网 URL
  ↓
爬虫定时访问并抓取正文
  ↓
Markdown 文件
  ↓
WISE 文件入库
  ↓
飞书机器人问答
```

#### 爬取流程

```text
读取飞书多维表格 MinDoc 同步配置
        ↓
在公司网络环境下访问 source_url
        ↓
根据 root_url / include_path 限定爬取范围
        ↓
解析入口页中的文档链接，或读取管理员配置的 URL 列表
        ↓
逐篇抓取文档正文
        ↓
提取标题、正文、目录层级、更新时间、可选 doc_id
        ↓
生成 normalized_url、url_hash、document_key
        ↓
转换为 Markdown
        ↓
生成本地 md 文件
```

#### Markdown 转换要求

每篇文档需要转换为独立 Markdown 文件，并在文件头部保留元数据。

```markdown
---
source: MinDoc
project_name: QMS
source_url: http://mindoc.company.local/docs/qms/iqc-submit
normalized_url: http://mindoc.company.local/docs/qms/iqc-submit
url_hash: 8c9f2a...
doc_id: null
title: IQC 检验单提交失败处理规范
system_type: QMS
md_hash: A8F3D...
crawled_at: 2026-06-10 02:00:00
---

# IQC 检验单提交失败处理规范

正文内容……
```

这样做的目的：

- 让 WISE 以文件方式解析 Markdown；
- 保留 source_url 和 normalized_url，方便追溯来源；
- 使用 url_hash 作为稳定映射字段，避免强依赖 MinDoc doc_id；
- doc_id 能获取则保留，不能获取不影响同步；
- 通过 md_hash 判断正文是否需要更新；
- 避免重复上传同一篇文档。

#### 文件入库流程

```text
生成 Markdown 文件
        ↓
计算 md_hash
        ↓
生成 document_key = project_name + url_hash
        ↓
查文档映射表，按 document_key / url_hash 匹配
        ↓
document_key 不存在：上传 WISE File API，新增映射记录
        ↓
document_key 存在但 md_hash 变化：重新上传 WISE，更新 wise_file_id
        ↓
document_key 存在且 md_hash 未变化：跳过
        ↓
记录 wise_file_id
        ↓
轮询 parse_status
        ↓
completed：可被机器人检索
        ↓
failed：记录失败原因并告警
```

#### 同步状态设计

| 状态 | 说明 |
|---|---|
| discovered | 已从入口 URL 中发现文档链接 |
| crawling | 正在爬取正文 |
| converted | 已转换为 Markdown |
| uploading | 正在上传 WISE |
| processing | WISE 正在解析 |
| completed | 解析完成，可检索 |
| skipped | 内容无变化，跳过 |
| failed | 同步失败 |
| network_unreachable | 爬虫服务无法访问公司内网 MinDoc |
| auth_failed | MinDoc 权限失败或登录态失效 |
| convert_failed | Markdown 转换失败 |
| upload_failed | WISE 文件上传失败 |
| parse_failed | WISE 解析失败 |
| expired | 源文档删除或 URL 失效 |

#### 关键说明

- 系统不把 MinDoc URL 直接丢给 WISE 做 URL 入库；
- 系统先在公司网络环境下访问 MinDoc URL，抓取正文，再转换成 Markdown 文件；
- 系统通过 WISE File API 上传 Markdown 文件；
- doc_id 不是强依赖字段，获取不到时为空；
- normalized_url / url_hash 是文档匹配的主要依据；
- md_hash 是判断正文是否变化的核心依据；
- source_url 作为元数据保存，用于追溯来源；
- 系统通过文档映射表（`mindoc_document_mapping`）批量管理同步状态；
- 如果源文档删除或 URL 失效，系统不立即物理删除 WISE 知识，而是先标记 expired，由管理员确认后下线；
- 如果企业 MinDoc 只能在内网访问，爬虫服务必须部署在公司网络内，或通过公司允许的代理 / VPN 访问。

### 9.5 文件入库流程

适用于 PDF、Word、Excel、Markdown、TXT 等文件。

```text
管理员上传文件
        ↓
调用 /knowledge/file
        ↓
WISE 解析文件
        ↓
生成 Chunk
        ↓
建立向量索引
        ↓
问答可用
```

---

### 9.6 Markdown 手工知识入库流程

适用于从飞书群、高频问题、异常处理经验中沉淀出来的轻量知识。

```text
未命中问题
        ↓
AI 辅助整理 Markdown
        ↓
管理员确认
        ↓
调用 /knowledge/manual
        ↓
WISE 解析
        ↓
问答可用
```

---

### 9.7 FAQ 入库流程

```text
高频问题
        ↓
生成 standard_question
        ↓
补充 similar_questions
        ↓
补充 answers
        ↓
设置 tag_id
        ↓
调用 /faq/entry 或 /faq/entries
        ↓
FAQ 可被命中
```

---

### 9.8 知识更新流程

#### MinDoc 文档更新

```text
MinDoc 源文档内容更新
        ↓
定时爬虫通过 normalized_url / url_hash 定位原文档，并发现 md_hash 变化
        ↓
重新生成 Markdown 文件并上传 WISE File API
        ↓
得到 new_wise_file_id
        ↓
轮询 parse_status
        ↓
completed：更新映射表中的 wise_file_id 和 md_hash，并下线 old_wise_file_id
        ↓
failed：保留 old_wise_file_id 继续生效，记录失败原因并告警
        ↓
新内容生效
```

#### FAQ 更新

```text
用户负反馈
        ↓
管理员审核
        ↓
修改 FAQ answers / similar_questions
        ↓
重新启用
```

#### 文档解析失败更新

```text
parse_status = failed
        ↓
调用 /knowledge/{id}/reparse
        ↓
仍失败
        ↓
人工转 Markdown 入库
```

---

## 十、Agent 问答流程设计

### 10.1 标准问答流程

```text
用户在飞书群 @机器人提问
        ↓
飞书 WebSocket / 事件回调接收消息
        ↓
消息去重（基于 message_id）
        ↓
提取文本
        ↓
判断是否反馈
        ↓
不是反馈 → 创建 qa_task（PENDING）
        ↓
立即回复原消息：”已收到，正在查询知识库…”
        ↓
异步执行 Agent 工作流（task → RUNNING）
        ↓
判断追问 / 新问题
        ↓
追问 → 复用 session，带上下文
新问题 → 不带上下文
        ↓
问题理解 Agent 分析意图
        ↓
调用 WISE Chat API 或 Agent Tools
        ↓
解析 references + answer
        ↓
回答生成 Agent 判断可信度
        ↓
组装飞书富文本
        ↓
回复原 message_id（定向回复，不发送普通消息）
        ↓
更新 qa_task 状态为 SUCCESS
        ↓
写入问答记录表
```

核心原则：**异步回答不按用户维度回复，而按 message_id / qa_task_id 回复。**

---



### 10.2 FAQ 优先命中流程

用户问题进入 Agent 问答流程后，系统优先尝试命中 WISE FAQ。FAQ 适合处理高频、标准、答案相对固定的问题；文档检索适合处理步骤较长、上下文较复杂的问题。

```text
用户提问
        ↓
问题标准化
        ↓
调用 WISE FAQ Search
        ↓
FAQ 命中
        ↓
返回 FAQ 标准答案，并带上知识来源
```

如果 FAQ 未命中：

```text
FAQ 未命中
        ↓
调用 WISE Chat / Knowledge Search
        ↓
有 references → 生成结构化答案
        ↓
无 references → 不生成具体操作步骤，记录未命中
```

这样可以保证高频问题响应更稳定，也可以避免所有问题都直接进入长文档检索。

---

### 10.3 Chat API 失败兜底流程

```text
Chat API 失败
        ↓
调用 Knowledge Search
        ↓
有高相关知识
        ↓
返回原文摘要 + 明确标注“检索结果”
```

如果 Knowledge Search 也失败：

```text
无结果
        ↓
记录未命中
        ↓
回复暂未收录
```

---

### 10.4 多轮对话与追问处理流程

#### 追问场景

```text
A：IQC 提交失败怎么办？
机器人：……
A：那如果关联来料单关闭不了怎么办？
```

系统判断为追问，复用 session，带上下文。

#### 新问题场景

```text
A：IQC 提交失败怎么办？
A：MES 工单无法报工怎么办？
```

系统判断为新问题，新建独立 task，不带历史上下文。

#### 流程

```text
用户发送新消息
  ↓
查找该 session_key 最近一条 qa_task
  ↓
判断是否在 30 分钟内
  ↓
是 → 检查是否包含追问特征词
  │
  ├── 是追问 → is_follow_up=true
  │            复用 wise_session_id
  │            带最近一轮上下文
  │            新建 qa_task，设置 parent_task_id
  │
  └── 新问题 → is_follow_up=false
               新建独立 qa_task
               不带历史上下文
  ↓
否（超过 30 分钟）→ 新建 session + 新建 task
  ↓
异步执行 Agent 工作流
  ↓
按 message_id 定向回复
```

多轮上下文规则：

| 场景 | 处理 |
|---|---|
| 30 分钟内同一用户追问 | 复用 session，带上下文（is_follow_up=true） |
| 30 分钟内同一用户新问题 | 新建独立 task，不带上下文（is_follow_up=false） |
| 超过 30 分钟 | 新建 session + 新建 task |
| 群聊多人交叉提问 | 按 chat_id + user_id 完全隔离 |
| 用户说”换个问题” | 新建 session，新建 task |
| 上下文过长 | 摘要压缩后保留 |

---

### 10.5 跨系统问题流程

```text
用户提问涉及多个系统
        ↓
问题理解 Agent 识别跨系统意图
        ↓
WISE 分标签检索
        ├── QMS 标签
        ├── WMS 标签
        └── MES 标签
        ↓
expand_context 扩展关联知识
        ↓
回答生成 Agent 合并多系统步骤
        ↓
回复中标注跨系统关联提醒
```

示例：

```text
用户：IQC 不合格怎么退货？

机器人回答需要同时说明：
1. QMS 中如何完成不合格判定
2. 是否需要审批或让步接收
3. WMS 中如何生成退货 / 退料动作
4. 两个系统状态如何对应
```

---

### 10.6 用户反馈流程

```text
机器人回复答案（引用原 message_id）
        ↓
用户回复“有用 / 没用 / 👍 / 👎”
        ↓
系统识别为反馈
        ↓
优先判断用户是否引用了机器人答案
        ↓
有引用：通过 bot_reply_message_id / parent_message_id 反查 qa_id
        ↓
无引用：查找同一 chat_id + user_id 最近 5 分钟内唯一一条 SUCCESS qa_task
        ↓
如果存在多条候选：提示用户引用具体答案后再反馈
        ↓
成功关联 qa_id 后写入 feedback_record
        ↓
若为“没用”
        ↓
累计该知识负反馈次数
        ↓
达到阈值生成审核任务
```

反馈关联优先级：

| 优先级 | 关联方式 | 说明 |
|---|---|---|
| 1 | 用户引用机器人答案 | 最准确，直接通过被引用消息反查 qa_id |
| 2 | parent_message_id | 如果飞书消息提供父消息 ID，则通过父消息反查 |
| 3 | 最近 5 分钟唯一 SUCCESS 任务 | 只允许唯一候选，避免连续提问后错绑 |
| 4 | 多候选或无候选 | 回复“请引用具体答案后反馈，避免关联错误” |

---

### 10.7 未命中闭环流程

```text
问题未命中
        ↓
写入未命中问题表
        ↓
统计出现次数
        ↓
周度巡检 Top10
        ↓
AI 辅助生成 FAQ 草稿
        ↓
管理员确认
        ↓
写入 WISE FAQ
        ↓
后续相似问可命中
```

---

## 十一、飞书机器人设计

### 11.1 消息接收

| 项目 | 方案 |
|---|---|
| 连接方式 | 飞书 WebSocket 长连接  |
| 重连机制 | SDK 自带重连 + 心跳检测 |
| 消息去重 | 基于 message_id 幂等去重 |
| 触发条件 | 群聊需 @ 机器人，私聊直接触发 |
| 消息类型 | 第一阶段仅支持文字 |

---

### 11.2 回复格式

飞书富文本消息，建议使用 post 类型。

标准回复结构：

```text
📋 IQC 检验单提交失败

原因分析：
  1. 检验结果未填写完整
  2. 当前状态不允许提交
  3. 关联来料单状态异常

处理步骤：
  1. 进入 QMS → IQC 检验单页面
  2. 检查检验结果是否完整
  3. 检查单据状态是否为“待提交”
  4. 检查关联来料单是否已关闭
  5. 重新提交

注意事项：
  · 如果涉及不合格退货，需要同步确认 WMS 退料状态

关联系统：
  · QMS / WMS

知识来源：
  · IQC 操作规范 V3 | WISE 知识库

反馈：
  · 回复“有用”或“没用”
```

---

### 11.3 消息类型处理

| 消息类型 | 处理方式 |
|---|---|
| 文本消息 | 正常处理 |
| 图片 | 回复“暂仅支持文字提问，请用文字描述您的问题” |
| 语音 | 回复“暂仅支持文字提问” |
| 文件 | 回复“请先将文件内容导入知识库后再提问” |
| 空消息 | 忽略不回复 |
| 纯表情 | 👍/👎 作为反馈，其余忽略 |
| 超长文本 | 截取前 500 字处理，并提示用户 |
| 快速连续提问 | 每条问题独立创建 qa_task，并行处理，各自回复原消息 |

---

### 11.4 异步任务与超时处理

#### 核心原则

```text
异步回答不按用户维度回复，而按 message_id / qa_task_id 回复。
```

每个问题独立建任务，回复时引用原消息 `message_id`，确保多问题并发时不会乱序、不会互相覆盖。

#### Redis 使用边界

Redis 只保存短期运行态数据，不保存知识正文、向量结果或问答答案缓存。长期运营记录写入飞书多维表格。

| Redis Key | 作用 | TTL 建议 |
|---|---|---|
| `feishu:message:{message_id}` | 消息幂等去重，防止飞书重复推送导致重复回答 | 24 - 72 小时 |
| `qa_task:{task_id}` | 保存任务详情，包括 `message_id`、`question`、`status` 等 | 24 - 72 小时 |
| `qa_task:lock:{task_id}` | 防止同一个任务被多个线程重复执行 | 5 - 10 分钟 |
| `session:{session_key}` | 保存 `session_key` 与 `wise_session_id` 的短期映射 | 30 分钟以上 |

#### 完整处理流程

```text
用户发送消息
  ↓
提取 message_id、chat_id、user_id、question
  ↓
Redis SETNX 判断 message_id 是否已处理
  ↓
未处理：生成 task_id，并写入 qa_task:{task_id}
  ↓
立即引用原消息回复“已收到，正在查询知识库…”
  ↓
将 task_id 提交给异步线程池
  ↓
异步线程根据 task_id 从 Redis 读取任务
  ↓
任务状态 PENDING → RUNNING
  ↓
调用 WISE FAQ / Chat / Knowledge Search 生成答案
  ↓
从 qa_task:{task_id} 取出原始 message_id
  ↓
引用原 message_id 回复飞书消息
  ↓
更新任务状态为 SUCCESS / FAILED / TIMEOUT / CANCELLED
  ↓
写入飞书多维表格 qa_record
```

设计要点：

- `message_id` 用于消息去重和定位原消息；
- `task_id` 用于异步任务处理；
- 每条用户消息都会生成一个独立 `task_id`；
- 异步线程只处理自己的 `task_id`；
- 回复时始终读取该任务绑定的 `message_id`，并引用原消息回复。

因此，即使同一用户连续发送多个问题，系统也会生成多个独立任务。任务可以并发执行，完成顺序可以不同，但每个任务最终都会回复自己绑定的原始 `message_id`，不会出现回复混乱或互相覆盖。

#### 多问题并发处理

同一用户连续发送多个问题时，系统为每个问题创建独立 qa_task：

```text
用户连续发送 3 个问题
      ↓
系统分别创建 3 个 qa_task
      ↓
task_001 → message_001
task_002 → message_002
task_003 → message_003
      ↓
异步并发执行
      ↓
task_002 先完成 → 回复 message_002
      ↓
task_001 后完成 → 回复 message_001
      ↓
task_003 超时 → 回复 message_003 超时兜底
```

即使后一个问题先处理完，也不会覆盖前一个问题的回复，因为每个任务只回复自己对应的 `message_id`。

#### 分层超时策略

| 时间节点 | 系统动作 |
|---|---|
| 5 秒内 | 正常等待，不额外回复 |
| 超过 5 秒 | 回复原消息：“已收到，正在查询知识库，请稍等。” |
| 超过 30 秒 | 回复原消息：“当前问题检索耗时较长，系统仍在处理中。” |
| 超过 3 分钟 | qa_task 标记 TIMEOUT，回复原消息：“本次查询超时，已记录该问题。建议稍后重试或联系知识管理员。” |

#### TIMEOUT 后置完成处理

后台线程在生成答案前必须再次检查 `qa_task` 状态：

| 当前状态 | 处理方式 |
|---|---|
| RUNNING | 可以发送正式答案，并更新为 SUCCESS |
| TIMEOUT | 不再发送正式答案，只记录执行结果和日志 |
| CANCELLED | 不再发送答案 |
| FAILED | 不再发送答案，保留失败原因 |

这样可以避免用户先收到超时话术，后续又收到一条正式答案，造成重复打扰和理解混乱。

#### 超时话术

5 秒：

```text
已收到，正在查询知识库，请稍等。
```

30 秒：

```text
当前问题检索耗时较长，系统仍在处理中。
```

3 分钟（TIMEOUT）：

```text
本次查询超时，已记录该问题。
建议稍后重试或联系知识管理员。
```

#### 任务取消

除非用户明确说“不用回答上一个了”，否则不取消旧任务。默认所有任务并行执行。

| 用户行为 | 系统处理 |
|---|---|
| 连续提问（无取消意图） | 每条独立建 task，并行处理 |
| 明确说“不用回答上一个了” | 将对应 qa_task 标记 CANCELLED |
| 明确说“换个问题” | 旧 task 标记 CANCELLED，新建 session + 新 task |

---

## 十二、知识治理机制

### 12.1 差评闭环

```text
用户点 👎 / 回复“没用”
        ↓
累计 3 次差评
        ↓
知识状态 → pending_review
        ↓
飞书通知管理员
        ↓
管理员审核
        ├── 答案错误 → 修正知识
        ├── 表述不清 → 优化表述
        └── 流程变更 → 更新内容
        ↓
修正后重新上线
        ↓
状态恢复 active
```

---

### 12.2 未命中闭环

```text
WISE 检索无结果
        ↓
记录到未命中问题表
        ↓
每周生成未命中 Top10 周报
        ↓
管理员评审
        ├── 高频问题 → 补充知识到 WISE
        ├── 低频问题 → 标记观察
        └── 非业务范围 → 标记忽略
        ↓
AI 辅助生成知识草稿
        ↓
管理员审核确认
        ↓
补充到知识库
        ↓
立即生效
```

---

### 12.3 知识保鲜闭环

```text
知识管理员定期巡检
        ↓
90 天未更新知识 → 联系业务专家确认有效性
        ↓
系统升级 / 流程变更 → 预判受影响知识
        ↓
确认过期 → 标记 expired → 不参与检索
        ↓
确认有效 → 更新 reviewed_at 时间戳
```

---

### 12.4 知识状态流转

```text
                    新增/确认有效
                         │
                         ▼
                   ┌──────────┐
        ┌─────────│  active   │─────────┐
        │         └──────────┘         │
        │               │               │
  差评触发/过期    巡检确认有效     巡检确认过期
        │               │               │
        ▼               ▼               ▼
  ┌──────────────┐  (保持 active)  ┌──────────┐
  │pending_review│                │ expired   │
  └──────┬───────┘                └──────────┘
         │                              │
    ┌────┴────┐                    确认误标
    │         │                         │
 修正后     确认有效                     │
 恢复active  恢复active                 │
    │         │                         │
    ▼         ▼                         ▼
 ┌──────────┐                     ┌──────────┐
 │  active  │                     │  active  │
 └──────────┘                     └──────────┘
```

状态说明：

| 状态 | 说明 |
|---|---|
| active | 有效，可被检索返回 |
| pending_review | 待审核，仍可被检索但需提示风险 |
| expired | 过期，不参与检索返回 |
| disabled | 人工停用，不参与检索 |

---

### 12.5 定期巡检 SOP

#### 周度巡检

| 检查项 | 操作 | 产出 |
|---|---|---|
| 差评条目审核 | 查看 pending_review 知识条目 | 确认差评原因并修正 |
| 未命中 Top10 | 查看本周未命中问题按频次排序 | 高频未命中补充知识 |
| 热点问题复查 | 检查高频问题对应知识 | 确认答案是否仍准确 |

#### 月度巡检

| 检查项 | 操作 | 产出 |
|---|---|---|
| 知识覆盖度分析 | 统计 WMS/QMS/MES 知识条数 vs 未命中数 | 识别知识盲区 |
| 过期知识排查 | 检查 90 天未更新知识 | 联系业务专家确认 |
| 反馈闭环率 | 统计差评条目中已修正比例 | 目标 ≥ 90% |

#### 季度巡检

| 检查项 | 操作 | 产出 |
|---|---|---|
| 全量知识审计 | 遍历 active 状态知识 | 过期下线，错误修正 |
| 系统升级影响评估 | 与 IT 确认 WMS/QMS/MES 升级计划 | 预判受影响知识 |
| 用户满意度趋势 | 统计月度有用率变化 | 定位下降原因 |

---

### 12.6 周报模板

```text
📊 制造知识助手周报 · 第 XX 周（MM/DD - MM/DD）

本周问答统计：
  · 总提问：XX 次
  · 命中：XX 次（XX%）
  · 未命中：XX 次
  · 用户反馈：有用 XX 次 / 没用 XX 次

巡检动作：
  · 审核差评条目：X 条
  · 新增知识：X 条
  · 标记过期：X 条

待处理：
  · pending_review 条目：X 条
  · 高频未命中问题 Top3：
    1. [问题描述] — 出现 X 次
    2. [问题描述] — 出现 X 次
    3. [问题描述] — 出现 X 次
```

---

## 十三、异常处理与兜底策略

核心原则：

```text
用户发消息后必须收到回复。
任何环节出问题都不能让用户“石沉大海”。
降级顺序：精准回答 → 模糊回答 → 候选列表 → 兜底话术 → 人工转接。
```

---

### 13.1 WISE 服务异常

| 场景 | 处理策略 |
|---|---|
| knowledge_search 超时 | 重试 3 次，失败后返回系统繁忙 |
| Chat API 不可达 | 调用 Knowledge Search 兜底 |
| WISE 返回错误 | 记录错误码，告警管理员 |
| API 限流 | 指数退避重试 |
| 鉴权失败 | 刷新 token 或提示管理员检查配置 |
| 所有知识服务不可用 | 返回系统繁忙，不生成答案 |

兜底话术：

```text
当前知识库服务暂时不可用，无法查询标准答案。
为避免误导产线操作，本次不直接给出处理步骤。
已通知技术人员处理，请稍后重试。
```

---

### 13.2 检索无结果

| 场景 | 处理策略 |
|---|---|
| 完全无匹配 | 记录未命中，不生成答案 |
| 分数过低 | 返回低可信提示 |
| 系统分类不匹配 | 扩大检索范围后重试 |
| 用户描述过短 | 提示用户补充系统、单据、报错信息 |

兜底话术：

```text
暂未收录该问题。

已记录您的问题并提交管理员补充。
建议补充以下信息后再次提问：
1. 涉及系统：WMS / QMS / MES
2. 单据类型：如 IQC 检验单、工单、入库单
3. 具体报错提示
```

---

### 13.3 飞书机器人异常

| 场景 | 处理策略 |
|---|---|
| WebSocket 断开 | 自动重连 |
| 飞书接口超时 | 重试 3 次 |
| 消息发送失败 | 记录失败并告警 |
| 群聊权限不足 | 提示管理员检查机器人权限 |
| 用户消息格式异常 | 返回格式提示 |

---

### 13.4 Agent 生成异常

| 场景 | 处理策略 |
|---|---|
| LLM 超时 | 重试，失败后返回检索摘要 |
| LLM 生成空答案 | 记录未命中 |
| LLM 幻觉风险 | 必须依赖 references，无引用不输出步骤 |
| 输出格式混乱 | 使用模板重新格式化 |
| 回答过长 | 自动摘要压缩 |

---

### 13.5 MinDoc 同步与文件入库异常

| 场景 | 处理策略 |
|---|---|
| MinDoc 站点不可访问 | 标记 `source_unavailable`，通知管理员 |
| MinDoc 登录态过期 | 标记 `auth_failed`，提示更新 Cookie / Token |
| 文档目录树获取失败 | 重试 3 次，仍失败则告警 |
| 单篇文档爬取失败 | 记录失败原因，不影响其他文档同步 |
| 页面结构变化 | 标记 `parse_failed`，进入人工修复 |
| 正文提取为空 | 不上传 WISE，标记 `convert_failed` |
| Markdown 转换失败 | 保留原始 HTML 快照，通知管理员 |
| md_hash 未变化 | 标记 `skipped`，避免重复上传 |
| WISE File API 上传失败 | 重试 3 次，仍失败则标记 `upload_failed` |
| WISE 解析失败 | 记录 parse_status 和失败原因，进入人工兜底 |
| 源文档删除 | 标记 `expired`，等待管理员确认是否下线 |

### 13.6 飞书多维表格异常

| 场景 | 处理策略 |
|---|---|
| 表格写入失败 | 重试 3 次 |
| 字段不存在 | 告警管理员修复表结构 |
| 权限不足 | 提示检查应用权限 |
| 写入超时 | 异步补偿写入 |
| 表格不可用 | 不影响用户问答，只记录本地日志 |

---

### 13.7 全链路兜底话术

| 兜底级别 | 触发条件 | 回复话术 |
|---|---|---|
| L1 降级回复 | 检索降级但仍能返回结果 | “当前系统部分功能受限，以下为检索结果摘要，仅供参考。” |
| L2 无结果 | 检索无匹配 | “暂未收录该问题，已记录并提交专家审核。” |
| L3 系统繁忙 | WISE 不可用 | “系统繁忙，请稍后重试，已通知技术人员处理。” |
| L4 最终兜底 | 所有组件都不可用 | “抱歉，当前系统繁忙。问题已记录并通知管理员处理，请稍后重试。” |

---

## 十四、运维监控体系

### 14.1 监控指标

| 指标 | 说明 | 告警阈值 |
|---|---|---|
| 问答次数 | 每日/每周总提问量 | — |
| 命中率 | 成功返回知识结果比例 | < 70% 告警 |
| 未命中率 | 无结果返回比例 | > 30% 告警 |
| 用户满意度 | 有用反馈 / 总反馈 | < 75% 告警 |
| 平均响应时间 | 从提问到回复耗时 | > 30 秒告警 |
| 3 分钟响应达成率 | 3 分钟内有回复比例 | < 99% 告警 |
| 知识增长量 | 每月新增知识条目数 | — |
| 差评数量 | 负反馈总数 | 单日 > 10 告警 |
| WISE API 成功率 | WISE 调用成功比例 | < 90% 告警 |
| 飞书连接状态 | WebSocket 是否在线 | 断连即告警 |

---

### 14.2 自动告警规则

| 告警项 | 条件 | 通知对象 |
|---|---|---|
| WISE API 连续失败 | 连续 5 次失败 | IT / 开发 |
| 飞书机器人断连 | 超过 1 分钟 | IT / 开发 |
| 未命中率过高 | 当日 > 30% | 知识管理员 |
| 差评过多 | 单日 > 10 次 | 知识管理员 |
| 线上文档入库失败 | 任意失败 | 知识管理员 |
| parse_status 长时间 processing | 超过 30 分钟 | 知识管理员 / IT |
| 多维表格写入失败 | 连续 3 次失败 | IT / 开发 |

---

## 十五、权限与安全设计

### 15.1 飞书权限

机器人需要具备：

- 接收群聊消息权限
- 发送群聊消息权限
- 读取用户基础信息权限
- 读取群聊信息权限
- 写入飞书多维表格权限

---

### 15.2 知识库权限

- WISE API Token 需要存储在服务端配置中
- 不在日志中打印完整 Token
- 不向用户暴露原始 API 响应
- 知识库按业务范围控制可访问内容
- 第一阶段默认只接入制造助手专用知识库

---

### 15.3 数据安全

- 记录问答日志时避免保存敏感字段
- 不记录用户隐私信息
- 不返回超出权限范围的知识
- 对操作类答案标注风险提示
- 机器人回答不能替代正式审批流程

---

## 十六、验收标准

### 16.1 功能验收

| 功能 | 验收标准 |
|---|---|
| 飞书消息接收 | 群聊 @ 机器人后能收到消息 |
| 私聊问答 | 私聊机器人能直接提问 |
| 问题理解 | 能识别 WMS/QMS/MES/跨系统意图 |
| 知识检索 | 能调用 WISE 返回相关知识 |
| 结构化回复 | 返回原因、步骤、注意事项、关联系统、知识来源 |
| 相似问法 | 不同表达能命中同一知识 |
| 未命中处理 | 无答案时不编造，记录未命中 |
| 用户反馈 | 有用/没用能写入反馈表 |
| 差评审核 | 同一知识 3 次负反馈后生成审核任务 |
| 线上文档自动同步与入库 | 可从飞书多维表格配置 MinDoc 空间，定时爬取目录树，通过 hash 判断新增/更新/跳过，自动上传 WISE 并被问答命中 |
| FAQ 入库 | 高频问题可生成 FAQ 并被命中 |
| 异常兜底 | 模拟服务异常时用户仍能收到回复 |

---

### 16.2 性能验收

| 指标 | 目标值            |
|---|----------------|
| 首次响应时间 | ≤ 5 秒返回“正在查询中” |
| 正式答案响应时间 | ≤ 3 分钟         |
| 理想平均响应时间 | ≤ 30 秒         |
| 知识命中率 | ≥ 80%          |
| 用户满意度 | ≥ 85%          |
| 未命中率 | ≤ 20%          |
| 系统可用性 | ≥ 90%          |
| 飞书消息处理成功率 | ≥ 90%          |

---

### 16.3 Demo 演示场景

| # | 演示场景 | 操作 | 预期效果 |
|---|---|---|---|
| D1 | QMS 智能问答 | 问“IQC 检验单提交失败怎么办” | 返回结构化答案 |
| D2 | 相似问法命中 | 问“IQC 单子交不上去” | 命中同一知识 |
| D3 | MES 问答 | 问“工单无法报工怎么办” | 返回 MES 操作步骤 |
| D4 | WMS 问答 | 问“入库单状态异常怎么办” | 返回 WMS 排查步骤 |
| D5 | 跨系统问答 | 问“IQC 不合格怎么退货” | 同时说明 QMS + WMS 流程 |
| D6 | 未命中问题 | 问一个不存在的问题 | 回复暂未收录并写入未命中表 |
| D7 | 用户反馈 | 回复“没用” | 写入反馈表，负反馈次数增加 |
| D8 | MinDoc 文档自动同步与入库 | 在飞书多维表格配置 MinDoc 空间，定时任务自动爬取目录树、转 Markdown、hash 比对后上传 WISE | parse_status 完成后可问答命中 |
| D9 | FAQ 沉淀 | 从未命中生成 FAQ | 再次提问可命中新增 FAQ |
| D10 | WISE 异常 | 模拟 WISE 不可用 | 用户收到系统繁忙兜底回复 |
| D11 | 查看运营表 | 打开飞书多维表格 | 问答、反馈、未命中、审核均有数据 |

---

## 十七、项目计划

### 17.1 一个月里程碑计划

| 周期 | 目标 | 交付物 |
|---|---|---|
| 第 1 周 | 环境准备与主链路跑通 | 飞书机器人、Spring Boot 服务、WISE API 调通 |
| 第 2 周 | Agent 问答能力开发 | 问题理解 Agent、回答生成 Agent、Chat API / Agent Tools 集成 |
| 第 3 周 | 知识库建设与闭环 | Markdown 文件入库、FAQ 入库、飞书多维表格、反馈、未命中记录 |
| 第 4 周 | 兜底、测试、验收、演示 | 异常兜底、测试用例、Demo 脚本、部署文档、最终汇报 |

---

### 17.2 详细任务拆分

#### 第 1 周：链路跑通

| 任务 | 说明 | 产出 |
|---|---|---|
| 创建飞书机器人 | 配置机器人权限和事件订阅 | 机器人可接收消息 |
| 搭建 Spring Boot 项目 | 初始化工程结构 | 服务可启动 |
| 接入飞书 WebSocket / 事件回调 | 接收用户消息 | 能打印消息日志 |
| 接入 WISE API | 配置 token、base_url | 能调用知识库接口 |
| 实现基础回复 | 简单问答返回飞书 | 主链路可用 |

#### 第 2 周：Agent 开发

| 任务 | 说明 | 产出 |
|---|---|---|
| 问题理解 Agent | 意图识别、关键词提取、标签判断 | 结构化检索请求 |
| 回答生成 Agent | 根据检索结果生成模板答案 | 飞书富文本答案 |
| Session 管理 | 支持多轮对话 | 用户上下文可复用 |
| Agent Tools 集成 | knowledge_search、read_chunks、expand_context | 检索能力增强 |
| 低可信判断 | references 为空不输出步骤 | 防止幻觉 |

#### 第 3 周：知识库与闭环

| 任务 | 说明 | 产出 |
|---|---|---|
| 建立 Document KB | 导入 WMS/QMS/MES 文档 | 文档知识库 |
| 建立 FAQ KB | 录入高频问题和相似问法 | FAQ 知识库 |
| 线上文档自动同步与入库 | 配置飞书多维表格 MinDoc 同步入口，定时爬取 MinDoc 目录树，计算 hash 判断新增/更新/跳过，上传 WISE 并轮询 parse_status | 自动入库流程 |
| 飞书多维表格 | 建问答、反馈、未命中、审核表 | 运营记录表 |
| 用户反馈 | 有用/没用识别和记录 | 反馈闭环 |
| 未命中记录 | 无答案时记录问题 | 知识补充队列 |

#### 第 4 周：测试与验收

| 任务 | 说明 | 产出 |
|---|---|---|
| 功能测试 | 覆盖 WMS/QMS/MES 问答 | 测试报告 |
| 异常测试 | 模拟 WISE、飞书、LLM 异常 | 兜底测试用例 |
| Demo 准备 | 准备 8-10 个演示场景 | 演示脚本 |
| 部署文档 | 编写启动、配置、部署说明 | 部署文档 |
| 最终汇报 | 整理方案、效果、风险、后续计划 | 项目报告 |

---

## 十八、风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| WISE API 不稳定 | 影响问答主链路 | 增加重试、降级、告警 |
| MinDoc 文档权限不足 | 无法自动入库 | 提前确认文档访问权限 |
| 文档质量差 | 检索结果不准确 | 补充 FAQ 和人工标准答案 |
| 用户提问太口语化 | 命中率下降 | 建立相似问法和 FAQ |
| LLM 幻觉 | 误导产线操作 | 无 references 不输出步骤 |
| 飞书消息超时 | 用户体验差 | 先回复“正在查询中” |
| 多维表格写入失败 | 运营数据缺失 | 异步补偿写入 |
| 知识更新不及时 | 答案过期 | 周/月/季度巡检 |
| 跨系统问题复杂 | 回答不完整 | expand_context + 多标签检索 |
| Demo 数据不足 | 演示效果差 | 提前准备至少 20 条知识和 10 条 FAQ |

---

## 十九、最终交付物

1. 飞书制造知识助手机器人
2. Spring Boot 服务代码
3. WISE 制造文档知识库
4. WISE 制造 FAQ 知识库
5. MinDoc 文档爬取与文件入库流程
6. FAQ 自动沉淀流程
7. 飞书多维表格运营记录表
8. 问答测试用例
9. 异常兜底测试用例
10. Demo 演示脚本
11. 部署说明文档
12. PDR 技术方案文档
13. 项目总结报告

---

## 二十、Demo 演示脚本

### 场景一：QMS 智能问答

用户提问：

```text
@制造知识助手 IQC 检验单提交失败怎么办？
```

预期：

```text
返回原因、处理步骤、关联系统、知识来源。
```

---

### 场景二：相似问法命中

用户提问：

```text
@制造知识助手 IQC 单子交不上去
```

预期：

```text
命中同一条 IQC 检验单提交失败 FAQ。
```

---

### 场景三：MES 问答

用户提问：

```text
@制造知识助手 工单无法报工怎么办？
```

预期：

```text
返回 MES 报工相关排查步骤。
```

---

### 场景四：WMS 问答

用户提问：

```text
@制造知识助手 入库单状态异常怎么办？
```

预期：

```text
返回 WMS 入库单状态排查步骤。
```

---

### 场景五：跨系统问题

用户提问：

```text
@制造知识助手 IQC 不合格怎么退货？
```

预期：

```text
回答中同时说明 QMS 判定流程和 WMS 退料执行流程。
```

---

### 场景六：未命中问题

用户提问：

```text
@制造知识助手 某个不存在的异常编码怎么处理？
```

预期：

```text
返回暂未收录。
写入未命中问题表。
```

---

### 场景七：用户反馈

用户回复：

```text
没用
```

预期：

```text
写入反馈记录表。
负反馈次数增加。
达到阈值后生成知识审核任务。
```

---

### 场景八：MinDoc 文档爬取与文件入库

管理员操作：

```text
在飞书多维表格配置 QMS MinDoc 空间。
定时任务自动获取目录树，递归爬取文档。
转 Markdown 并计算 hash。
与映射表比对，新文档或 hash 变化的文档上传 WISE File API。
等待 parse_status = completed。
```

用户再提问：

```text
@制造知识助手 新文档里的问题怎么处理？
```

预期：

```text
机器人能基于新导入文档回答。
```

---

### 场景九：知识沉淀

管理员操作：

```text
从未命中问题生成 FAQ 草稿。
管理员确认。
调用 FAQ API 入库。
再次提问。
```

预期：

```text
问题可以命中新增 FAQ。
```

---

### 场景十：服务异常兜底

测试操作：

```text
模拟 WISE API 不可用。
```

预期：

```text
机器人不沉默，返回系统繁忙兜底话术。
```

---

## 二十一、方案选型与技术决策

### 21.1 为什么选择 WISE 而不是自建 RAG

#### 自建 RAG

优点：

- 自由度高；
- 可以完全自主控制底层检索链路；
- 后续可做深度定制。

缺点：

- 需要建设向量库；
- 需要维护 Embedding 模型；
- 需要设计 Chunk 分块策略；
- 需要维护召回、重排、引用、评估等完整 RAG 链路；
- 一个月周期内交付风险较高。

#### WISE（最终方案）

优点：

- 已具备知识库管理能力；
- 已具备文件解析、Chunk 切分、向量检索和关键词检索能力；
- 已具备 FAQ 能力；
- 已具备 Chat API / Agent Tools 能力；
- 可以将项目重点放在飞书机器人、业务编排、MinDoc 同步和运营闭环上；
- 更符合一个月 Demo 交付周期。

最终选择：**WISE 作为知识库和问答底座，Spring Boot 服务只做入口、编排和治理闭环。**

### 21.2 为什么选择 MinDoc 爬虫同步

#### 人工维护

人工复制 MinDoc 内容或人工上传 Markdown 文件虽然简单，但维护成本高，容易漏更，也无法稳定追踪源文档变化。

#### URL 直接入库

URL 直接入库依赖页面结构和访问权限。如果页面存在登录态、动态渲染、导航干扰、图片表格较多等问题，解析质量不可控。

#### MinDoc → Markdown → WISE File API

最终方案采用：

```text
MinDoc 在线文档
  ↓
定时爬取正文
  ↓
转换为 Markdown 文件
  ↓
计算 md_hash
  ↓
通过 WISE File API 上传
```

优势：

- 内容可控；
- 可以保留 `source_url`、`normalized_url`、`url_hash`、`doc_id`（可选）、`md_hash` 等元数据；
- 支持增量同步；
- 支持版本追踪；
- 便于失败重试和人工兜底；
- 不依赖 Sitemap、Wiki、Confluence 或 URL 自动发现。

### 21.3 为什么选择飞书机器人

- 用户零学习成本；
- 产线、IT、导师本来就在飞书群沟通；
- 群聊中可以直接 @ 机器人提问；
- 机器人可以引用原消息定向回复，适合多人并发提问场景；
- 飞书多维表格可以承载运营记录、反馈、未命中和同步状态。

### 21.4 为什么采用 Agent 编排

本项目不是单纯的“问一句，查一段文档”，而是需要完成：

```text
问题理解
  ↓
FAQ 优先命中
  ↓
知识库检索
  ↓
引用校验
  ↓
结构化回答
  ↓
反馈和未命中闭环
```

Agent 编排可以把 WMS / QMS / MES 意图识别、知识检索、上下文扩展、回答生成和低可信兜底串联起来，提高问答稳定性和可解释性。

---

## 二十二、项目亮点

### 22.1 制造领域专属知识助手

项目聚焦 WMS、QMS、MES 制造业务系统，不是通用聊天机器人。回答内容围绕产线异常、状态机、审批流程、处理步骤和关联系统展开。

### 22.2 MinDoc 自动同步

通过飞书多维表格配置 MinDoc 入口和同步频率，系统自动爬取在线文档、转换 Markdown、计算 hash 并上传 WISE，实现知识保鲜。

### 22.3 知识驱动，避免编造

系统要求有 references 才输出具体操作步骤。无知识依据时不编造答案，而是记录未命中并进入知识补充流程。

### 22.4 群聊并发隔离

群聊中使用 `chat_id + user_id` 作为 `session_key`，多人同时提问互不污染上下文。每条问题创建独立 `qa_task`，并按 `message_id` 定向回复。

### 22.5 qa_task 异步机制

通过 Redis 管理短期异步任务，实现消息幂等、任务状态流转、并发处理、超时控制和失败兜底。

### 22.6 知识闭环运营

完整闭环为：

```text
用户提问 → 知识命中 → 结构化回答 → 用户反馈 → 差评审核 → FAQ / 文档补充 → 知识更新
```

### 22.7 一个月内可落地

项目复用 WISE、飞书机器人、飞书多维表格和 MinDoc，不自建知识库和向量库，开发重心集中，适合一个月周期交付 Demo。

---

## 二十三、Demo 验收方案

### 23.1 验收场景

| 场景 | 验收内容 |
|---|---|
| QMS 问答 | 用户问“IQC 检验单提交失败怎么办”，机器人返回结构化答案 |
| MES 问答 | 用户问“工单无法报工怎么办”，机器人返回原因和处理步骤 |
| WMS 问答 | 用户问“入库单状态异常怎么办”，机器人返回处理建议 |
| FAQ 命中 | 用户使用相似问法，仍能命中同一条标准 FAQ |
| 未命中处理 | 用户问知识库没有的问题，机器人不编造答案并记录未命中 |
| 用户反馈 | 用户回复“有用 / 没用”，系统能正确关联 qa_id 并写入反馈表 |
| 群聊并发 | 多个用户在同一群同时提问，机器人分别引用原消息回复 |
| MinDoc 同步 | 修改 MinDoc 文档后，系统通过 normalized_url / url_hash 定位文档，并识别 md_hash 变化后重新上传 WISE |
| 超时兜底 | WISE 超时或不可用时，机器人返回兜底话术并记录错误 |

### 23.2 验收标准

- 能接收飞书群聊 @ 消息；
- 能识别用户问题并创建独立 qa_task；
- 能调用 WISE FAQ / Chat / Knowledge Search；
- 能返回结构化答案；
- 能在 3 分钟内给出正式答案或兜底回复；
- 能处理相似问法；
- 能记录问答、反馈、未命中和同步状态；
- 能通过 MinDoc 同步生成 Markdown 文件并上传 WISE；
- 能在异常情况下不编造答案；
- 能完成一条端到端 Demo 链路演示。

---

## 二十四、项目价值总结

本项目最终定位为：

```text
基于 WISE 线上知识库能力、MinDoc 在线文档爬虫和飞书机器人的制造知识助手。
```

系统不重复建设知识库、向量库、Embedding、Chunk、RAG 等底层能力，而是复用 WISE 提供的文件解析、FAQ、标签、检索、问答和 Agent Tools 能力。Spring Boot 服务承担飞书入口、Agent 编排、MinDoc 定时爬虫、Markdown 转换、WISE 文件上传、飞书回复格式化、运营数据记录和异常兜底。

本项目的知识同步方式不是“URL 自动发现 + URL 入库”，而是：

```text
MinDoc 空间
  ↓
读取飞书多维表格同步配置
  ↓
获取目录树，递归遍历
  ↓
逐篇爬取正文，转换为 Markdown 文件
  ↓
生成 normalized_url / url_hash，计算 md_hash，并与文档映射表比对
  ↓
hash 变化或新文档 → 通过 WISE File API 以文件形式上传
hash 未变化 → 跳过
  ↓
WISE 解析并参与检索问答
```

最终交付结果是一个能够在飞书中回答 WMS / QMS / MES 产线异常问题的制造知识助手。它能够让产线员工快速获得标准处理步骤，让老员工减少重复答疑，让 IT 更快定位问题，也让知识管理员通过 MinDoc 源文档、未命中问题和反馈数据持续补充与更新知识库。

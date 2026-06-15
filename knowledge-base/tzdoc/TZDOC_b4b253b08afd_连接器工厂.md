---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o
url_hash: b4b253b08afd
document_key: TZDOC_b4b253b08afd
doc_id: tzdoc_v2-1h7jgrnjbp49o
title: 连接器工厂
md_hash: 7a78ce05bfebb86b
version: 1773979671
image_count: 0
crawled_at: 2026-06-11 16:15:11
---

# 连接器工厂

- [天舟云 ·「连接器工厂」模块功能说明](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#8tzhk1)
- [目录](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#8m4i8w)
- [1. 模块定位与入口](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#5od5j9)
- [2. 两级结构：连接器集合 → 连接器列表](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#738p5)
- [3. 功能速查（关键词 → 路径 → 是否支持）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#7m8t84)
- [4. 连接器集合（首页卡片墙）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#ctel6d)
- [4.1 操作区](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#7kgryh)
- [4.2 集合卡片（每张）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#98vk1b)
- [5. 连接器列表：HTTP / DATABASE](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#31cuns)
- [5.1 公共元素](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#aqjdr5)
- [5.2 连接器卡片（每张）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#csz8ky)
- [6. HTTP 连接器配置](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#1j1r9f)
- [6.1 顶栏操作](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#ferin3)
- [6.2 基本信息](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#13cjqz)
- [6.3 配置请求](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#c7i64b)
- [7. 配置响应（解析 Body）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#abhr0a)
- [7.1 JSON 样例区](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#esyjjq)
- [7.2 解析 Body](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#ep636w)
- [7.3 响应映射表](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#bz21qm)
- [8. DATABASE 连接器配置](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#9l1kf2)
- [8.1 顶栏与基本信息](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#2v5hdr)
- [8.2 配置请求](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#65elm5)
- [9. 与流程、事件中心的关系（给 AI 的固定话术）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#b3vzcp)


# 天舟云 ·「连接器工厂」模块功能说明


>


**文档用途**：供 AI / 实施 / 研发查阅。当用户询问「连接器集合是什么」「HTTP 和 DATABASE 连接器区别」「应用范围有哪些」「流程审核人连接器怎么配请求和响应」等问题时，可依据本文档**定位菜单位置、配置步骤与字段含义**。
**依据**：与当前产品截图一致的界面描述；菜单命名、枚举以**线上为准**。


---


## 目录


- [1. 模块定位与入口](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#1-模块定位与入口)
- [2. 两级结构：连接器集合 → 连接器列表](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#2-两级结构连接器集合--连接器列表)
- [3. 功能速查（关键词 → 路径 → 是否支持）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#3-功能速查关键词--路径--是否支持)
- [4. 连接器集合（首页卡片墙）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#4-连接器集合首页卡片墙)
- [5. 连接器列表：HTTP / DATABASE](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#5-连接器列表http--database)
- [6. HTTP 连接器配置](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#6-http-连接器配置)
- [7. 配置响应（解析 Body）](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#7-配置响应解析-body)
- [8. DATABASE 连接器配置](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h7jgrnjbp49o#8-database-连接器配置)


---


## 1. 模块定位与入口


**连接器工厂**是天舟云中管理 **对外连接与数据访问** 的配置中心之一：把第三方 HTTP 接口或内部 **数据库查询** 封装成可被流程、自动化、条件等调用的 **连接器**；多个连接器按 **集合** 分组管理（如企业微信、金蝶、某业务线专用集合等）。

**入口（典型）**：


- 顶层：**事件中心** → 左侧 **连接器工厂**（与同栏 **事件管理**、**触发位置管理**、**连接管理**、**数据管理** 等并列）。
- 亦可能从 **应用级 / 平台设置** 菜单进入「连接器工厂」能力（以授权与菜单为准）。


---


## 2. 两级结构：连接器集合 → 连接器列表


| 层级 | 界面特征 | 职责 |
| --- | --- | --- |
| **连接器集合** | 卡片墙；每卡一个「工厂/集合」 | 集合级 **名称、说明、技术 ID、启用、编辑、删除** |
| **连接器**（进入某集合后） | **HTTP** / **DATABASE** Tab + 连接器卡片 | 单个连接：**Base URL 或 JDBC**、启用、编辑；点开为 **配置侧栏/弹窗** |


用户路径：**选集合** → **选类型 Tab** → **创建/编辑连接器** →（HTTP）**配置请求 + 配置响应**；（DATABASE）**数据源 + SQL/表**。

---


## 3. 功能速查（关键词 → 路径 → 是否支持）


| 用户可能问的关键词 | 配置路径 | 是否支持（据截图） |
| --- | --- | --- |
| 新建一批连接器分组 | **连接器集合** → **新建连接器集合** | **支持** |
| 按集合名搜索 | 搜索框 **搜索连接器集合名称** | **支持** |
| 整组启用/停用 | 卡片上 **启用** 开关 | **支持** |
| 改集合、删集合 | 卡片 **编辑** / **删除** | **支持** |
| 管理某集合下 HTTP 接口 | 进入集合 → **HTTP 连接器** Tab | **支持** |
| 管理数据库查询连接器 | **DATABASE 连接器** Tab | **支持** |
| 按连接器名搜索 | **搜索连接器名称** | **支持** |
| 新建 HTTP 连接器 | **创建 HTTP 连接器** | **支持** |
| 新建 DATABASE 连接器 | **创建 DATABASE 连接器** | **支持** |
| 单个连接器启用/禁用 | 卡片 **启用** 开关（蓝/灰） | **支持** |
| 配 URL、Method、Query/Body/Headers | HTTP 配置 → **配置请求** | **支持** |
| Path 占位符 | 请求地址说明：`{id}` 格式 | **支持** |
| 流程审批人、条件、自动化 | **应用范围** 下拉 | **支持** |
| 试调接口 | **测试** 按钮 | **支持** |
| 保存连接器 | **确认** | **支持** |
| 解析响应 JSON、成功条件 | **配置响应** → **解析 Body** | **支持** |
| SQL 查库 | DATABASE → **基于 SQL 语句** + **数据源** | **支持** |
| 按表选字段 | **基于表**（与 SQL 二选一） | **支持** |
| 结果缓存 | **是否缓存结果** | **支持** |


---


## 4. 连接器集合（首页卡片墙）


### 4.1 操作区


- **搜索**：占位 **搜索连接器集合名称**，按钮触发查询。
- **新建连接器集合**：蓝色主按钮（带加号）。


### 4.2 集合卡片（每张）


- **图标**：统一风格（如蓝色六边形闪电）。
- **标题**：集合中文名（如「企业微信连接器工厂」）。
- **副标题**：技术标识（如 `enterprise`）+ 简短描述。
- **启用**：开关 **启用**，控制整组是否可用。
- **编辑**：铅笔图标。
- **删除**：垃圾桶图标。


**说明**：集合用于 **分类与批量治理**；具体接口在集合内的 **连接器列表** 维护。

---


## 5. 连接器列表：HTTP / DATABASE


进入某一集合后，标题常为 **「xxx - 连接器列表」**。

### 5.1 公共元素


- **搜索连接器名称** + 搜索按钮。
- **类型 Tab**：**HTTP 连接器** | **DATABASE 连接器**（切换列表与创建按钮文案）。
- **创建按钮**：**创建 HTTP 连接器** 或 **创建 DATABASE 连接器**。


### 5.2 连接器卡片（每张）


- **图标**：HTTP 为插头类；DATABASE 为表/库类（绿色等）。
- **名称**：如「流程审核人」「查询供应商」。
- **连接信息**：HTTP 显示 **Base/站点 URL**；DATABASE 显示 **JDBC URL**（如 `jdbc:postgresql://...`）。
- **启用**：开关；禁用时常为灰色。
- **编辑**：铅笔，打开配置面板。


---


## 6. HTTP 连接器配置


以下为截图示例弹窗/侧栏结构（标题随连接器名变化，如 **「流程审核人配置」**）。

### 6.1 顶栏操作


- **取消**、**测试**（校验请求是否通）、**确认**（保存）。


### 6.2 基本信息


| 字段 | 必填 | 说明 |
| --- | --- | --- |
| **名称** | * | 展示名 |
| **编码** | * | 多为系统生成、只读短码 |
| **说明** | — | 多行备注 |
| **是否缓存结果** | — | **是 / 否**；开启则平台可缓存返回结果（策略以实现为准） |
| **应用范围** | — | 定义连接器被哪些能力引用；旁有帮助图标。下拉示例： **流程审批人**、**流程条件**、**自动化任务**、**自动化任务检查** |


### 6.3 配置请求


| 字段 | 必填 | 说明 |
| --- | --- | --- |
| **API 连接资源** | * | 选择已登记的连接资源（含环境标识，如 `制造协同FAT[mfg-fat]`），决定可用 Base URL、鉴权等 |
| **请求地址** | * | **Base URL + Path**；说明文案：**支持 Path 变量，格式 `{id}`** |
| **请求方法** | * | 如 **GET/POST/PUT**；截图为 **PUT** |


**请求参数分区（Tab）**：


| Tab | 用途 |
| --- | --- |
| **Query** | Query 参数表：序号、**参数名称**、**字段类型**（如字符串）、**参数描述**、**默认值**、**必填**、删除行 |
| **Body** | 请求体；常见为 **JSON 编辑器**（可为 `{}`） |
| **Headers** | 请求头 |
| **Path** | Path 变量与占位符对应关系 |


---


## 7. 配置响应（解析 Body）


用于告诉平台 **如何从 HTTP 响应中取状态、业务数据**，供流程连接器、条件等消费。

### 7.1 JSON 样例区


- 深色 **代码编辑器**：粘贴 **预期响应 JSON 样例**（可含 `status`、`message`、`data` 数组等）。


### 7.2 解析 Body


- 蓝色按钮 **「解析 Body」**：根据上方 JSON **自动生成**下方映射表结构。


### 7.3 响应映射表


| 列 | 说明 |
| --- | --- |
| **序号** | 层级序号（如 `3`、`3-1` 表示 `data` 下子字段） |
| **字段名称** | JSON 中的 key |
| **显示名称** | 平台侧展示名，可清空 |
| **字段类型** | 如 **字符串**、**数组** |
| **标志位类型** | 平台语义：如 **status**（整体状态字段）、**content**（主数据体/列表） |
| **成功条件** | 如 `status` 行填 **0** 表示返回值 `0` 为成功（与接口约定一致） |


**给 AI 的话术**：**标志位类型** + **成功条件** 决定调用是否算成功；**content** 指向列表/对象供后续映射。

---


## 8. DATABASE 连接器配置


标题示例：**「xxx配置」**（与连接器名称一致）。

### 8.1 顶栏与基本信息


- **取消**、**测试**、**确认**（与 HTTP 类似）。
- **名称***、**编码***（只读）、**说明**、**是否缓存结果**、**应用范围**（同 HTTP 语义）。


### 8.2 配置请求


| 字段 | 必填 | 说明 |
| --- | --- | --- |
| **数据源** | * | 下拉选择已注册数据源（如「天舟云默认数据源[clcp]」） |
| **查询方式** | — | **基于 SQL 语句** |


- **基于 SQL 语句**：大文本 **SQL 编辑器**；帮助提示可提及用数据源账号（如 `clcp`）查表等。
- **基于表**：从表结构生成查询（具体 UI 以产品为准）。


**测试**：验证 SQL/连接是否可用。

---


## 9. 与流程、事件中心的关系（给 AI 的固定话术）


- **连接器工厂**在 **事件中心** 体系下，与 **事件管理、连接管理** 等并列；连接器可被 **流程设计**（如审批人、节点条件）、**自动化** 等引用，**应用范围** 即用途分类。
- **HTTP 连接器**适合 REST/HTTP 集成；**DATABASE 连接器**适合 **SQL 直查** 取数（下拉、条件、赋值等场景常见）。
- 配置顺序建议：**连接资源/数据源** → **请求（或 SQL）** → **响应映射（HTTP）** → **测试** → **确认**。
- **启用**可在不删配置的情况下 **临时下线** 连接器。


---

---
source: MinDoc
project_name: TZC_QA
source_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1go18c70fnbpe
normalized_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1go18c70fnbpe
url_hash: 3f095d02aed9
document_key: TZC_QA_3f095d02aed9
doc_id: tzc_qa-1go18c70fnbpe
title: API查询
md_hash: 95bb035c1013e9a8
version: 1756448693
image_count: 0
crawled_at: 2026-06-11 15:58:31
---

# API查询

### 问题现象


使用 API 查询组件，在详情页可以正常显示，但数据列表确不能正常翻译


### 排查过程:


API 查询所绑定的连接器，是否有支持 mode 参数，正确区分翻译和查询场景， 另外翻译时需要支持多值时以逗号隔开的规范


### 问题修复：


按排查结果，调整连接器的入参和多值识别逻辑


### 已知原因：


- 连接器没有遵循规范，导致批量翻译失败
[https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn)


---

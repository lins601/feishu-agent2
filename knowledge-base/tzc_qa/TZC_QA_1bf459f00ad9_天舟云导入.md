---
source: MinDoc
project_name: TZC_QA
source_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gng1ugekoukq
normalized_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gng1ugekoukq
url_hash: 1bf459f00ad9
document_key: TZC_QA_1bf459f00ad9
doc_id: tzc_qa-1gng1ugekoukq
title: 天舟云导入
md_hash: 325b93861149b551
version: 1755845488
image_count: 0
crawled_at: 2026-06-11 15:58:31
---

# 天舟云导入

### 问题现象


Excel导入时报错，提示属性值XXX无法翻译，怎么排查？


### 排查过程:


- 初步定位：组件类型带数据字典时，在导入时需把”显示值”转换为”存储值”，在转换值时数据字典接口未返回对应值导致。
- 深入排查：检查无法翻译字段的组件类型。


  - 情况1：如果组件类型为下拉、搜索组件，那么在容器lcp-app项目检查数据集的翻译接口，确认是否正确返回。用报错值去搜索
  - 情况2：如果组件类型为API查询，那么在容器lcp-hub项目检查数据集的翻译接口，确认是否正确返回。用报错值去搜索


### 问题修复：


按排查结果，修复接口或数据库SQL，让连接器或接口正确返回”存储值”


### 已知原因：


- 连接器的接口或SQL不支持批量操作 [https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fqcr43mpa2da](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fqcr43mpa2da)


---

---
source: MinDoc
project_name: TZC_QA
source_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gng5jvto3vok
normalized_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gng5jvto3vok
url_hash: 926b1a31f2d5
document_key: TZC_QA_926b1a31f2d5
doc_id: tzc_qa-1gng5jvto3vok
title: 列表Excel导出
md_hash: 40ef275bcf930267
version: 1755847125
image_count: 0
crawled_at: 2026-06-11 15:58:34
---

# 列表Excel导出

### 归类: Excel导出失败


##### 问题现象: Excel导出内容为空


##### 分析思路：


情况 1、如果整个Excel完全为空(列名称都没有)，那么原因是导出过程报错导致，查看容器lcp-app和lcp-hub查看具体报错


- 可能原因:


  - API查询接口报错
  - API查询数据转换过程报错
  - csb数据字典翻译过程报错
  - 导出事件报错


情况 2、如果Excel存在列头，只是数据为空


- 可能原因:


  - 用户没有数据行的权限或数据行内容本身为空


---

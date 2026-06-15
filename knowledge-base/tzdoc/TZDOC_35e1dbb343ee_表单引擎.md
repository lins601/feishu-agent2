---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1f5tpvbvp6403
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1f5tpvbvp6403
url_hash: 35e1dbb343ee
document_key: TZDOC_35e1dbb343ee
doc_id: tzdoc_v2-1f5tpvbvp6403
title: 表单引擎
md_hash: 278b170099172e88
version: 1757924075
image_count: 0
crawled_at: 2026-06-11 16:07:21
---

# 表单引擎

- [1. 简介](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1f5tpvbvp6403#3e3hv1)
- [1.1 说明](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1f5tpvbvp6403#dwsy44)
- [1.2 应用场景](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1f5tpvbvp6403#3iyhi3)
- [2. 表单类型](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1f5tpvbvp6403#acogn)


# 1. 简介


### 1.1 说明


表单是基础工具，业务模型的载体，业务数据的填报、采集、呈现都需要依赖表单来完成，因此表单也是数据来源。

### 1.2 应用场景


| 场景 | 说明 | 举例 |
| --- | --- | --- |
| 数据上报收集 | 简单的在线收集填写信息 | 问卷调查、调查统计、在线报名、数据上报等 |
| 业务单据 | 业务流驱动，会关联其他业务数据 | 订单管理、产品管理、采购入库等 |
| 流程审批 | 需要审核有流程驱动的业务单据 | 请假申请、报销审批、变更申请等 |


# 2. 表单类型


目前天舟云有四种表单类型：普通表单、流程表单、对象、变更。


| 表单类型 | 说明 | 应用场景 |
| --- | --- | --- |
| 普通表单 | 可以收集业务进展中的所有数据，收集上来的数据还可以进行分权协作，将数据权限发布给需要管理数据的成员或者发布给外部成员进行外部数据收集 | 数据上报收集、业务单据 |
| 流程表单 | 可以让数据自下而上层层流转，逐级审批。也可以分配权限，进一步管理流程数据。普通表单与流程表单最大的区别就是流程表单需要逐级审批，且需要设置流程 | 流程审批 |
| 对象 | 现实中某一类物件的管理，树形分类管理，上下属性可以继承，并且有版本或流程控制属性变更 | 物料、产品、菜品、原料数据管理 |
| 变更 | 与对象配套的流程审批单据，控制对象的版本和审批 | 物流变更申请、 菜品状态变更 |

---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fvp0q12tnbc4
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fvp0q12tnbc4
url_hash: e2d899e6a797
document_key: TZDOC_e2d899e6a797
doc_id: tzdoc_v2-1fvp0q12tnbc4
title: 批量本节点自身重入(审核人)
md_hash: d3e2c357f2af804f
version: 1729137073
image_count: 0
crawled_at: 2026-06-11 16:11:10
---

# 批量本节点自身重入(审核人)

#### 接口说明


 接口名称：批量本节点自身重入(当前处理人操作)
 接口地址：{app项目域名+转发路径}/v1/app/openapi/workflow/batchApproveProcess
 请求方式: POST JSON
 入参说明


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| approveCode | 操作编码 | 字符串(String) | 不可为空 | 固定 sys_node_reentry |
| classId | 表单id | 字符串(String) | 不可为空 | obj_class表的id |
| formInstanceId | 单据id | 字符串(String) | 不可为空 | 业务表中的单据id |
| nodeCode | 当前操作节点编码 | 字符串(String) | 不可为空 |  |
| approveName | 操作名称 | 字符串(String) | 允许为空 |  |
| comment | 审核意见 | 字符串(String) | 允许为空 | 在审批记录中显示 |


请求头


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| x-auth-token | 用户jwt | 字符串(String) | 不可为空 | 审批记录上会作为操作人展示 |
| x-app-id | 应用id | 字符串(String) | 不可为空 | obj_application表id |
| x-tenant-id | 租户id | 字符串(String) | 不可为空 | obj_application表tenant_id |


请求参数例子


```
[
    {
        "comment": "同意1",
        "approveCode": "sys_node_reentry",
        "approveName": "通过",
        "classId": "7a0583f09f564008b0bea63f232d55ae",
        "formInstanceId": "3e45da89a05848e3870ed6c7b97b620a",
        "nodeCode": "n_sys_suspend"
    },
    {
        "comment": "同意",
        "approveCode": "sys_node_reentry",
        "approveName": "通过",
        "classId": "7a0583f09f564008b0bea63f232d55ae",
        "formInstanceId": "e2093b49501c4053a9268d1bd269722e",
        "nodeCode": "n_sys_suspend"
    }
]
```


返回信息


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| status | 返回状态 | 字符串 | 不为空 | 0:成功 其他:失败 |
| success | 业务成功标识 | (boolean) | 不为空 | true:成功 false:失败 |
| result | 返回信息 | 字符串(String) | 可能为空 | 提示信息 |


示例：


```
{
    "status": "0",
    "message": "success",
    "data": {
        "success": true,
        "result": null,
        "status": null
    }
}
```

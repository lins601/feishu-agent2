---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1gua97fchjsbd
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1gua97fchjsbd
url_hash: 09af635f097a
document_key: TZDOC_09af635f097a
doc_id: tzdoc_v2-1gua97fchjsbd
title: 获取用户需交接流程
md_hash: db0354f7488a4bcc
version: 1763539474
image_count: 0
crawled_at: 2026-06-11 16:11:05
---

# 获取用户需交接流程

#### 接口说明


 接口名称：获取用户需交接的流程
 接口地址：{app项目域名+转发路径}/v1/app/openapi/workflow/handleOverList
 请求方式: POST JSON
 入参说明


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| account | 域账号 | 字符串(String) | 不可为空 |  |


请求头


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| x-auth-token | 用户jwt | 字符串(String) | 不可为空 | 审批记录上会作为操作人展示 |
| x-app-id | 应用id | 字符串(String) | 不可为空 | obj_application表id |
| x-tenant-id | 租户id | 字符串(String) | 不可为空 | obj_application表tenant_id |


请求参数例子


```
{
    "account":"yeduanwang"
}
```


返回信息


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| status | 返回状态 | 字符串 | 不为空 | 0:成功 其他:失败 |
| message | 提示消息 | (boolean) | 不为空 |  |
| data | 流程对象列表数据 | 列表 | 可能为空 |  |


示例：


```
{
    "status": "0",
    "message": "success",
    "data": [
        [
            {
                "fileName": "流程名称",
                "value": "TZ-PROCESS_REVIEW_TESTING202511030000003",
                "fileId": "workItemName"
            },
            {
                "fileName": "流程ID",
                "value": "a531d2bc09d649c1af8c3a2b7fd63370",
                "fileId": "processId"
            },
            {
                "fileName": "域账号",
                "value": "yeduanwang|keliming|niewei",
                "fileId": "domainCode"
            },
            {
                "fileName": "当前节点编码",
                "value": "node-eeda1c7b5d6e3",
                "fileId": "curNodeCode"
            },
            {
                "fileName": "当前节点",
                "value": "审批节点3",
                "fileId": "curNodeName"
            },
            {
                "fileName": "创建人域账号",
                "value": null,
                "fileId": "createDomainCode"
            },
            {
                "fileName": "文档状态",
                "value": "20",
                "fileId": "docStatus"
            },
            {
                "fileName": "文档创建时间",
                "value": 1762165364541,
                "fileId": "docCreateTime"
            },
            {
                "fileName": "文档链接",
                "value": "https://lcpsit.gz.cvte.cn/portal/cmApp/cmPage?appId=612821ce41d14642bd6c48562c53474b&tenantId=c518f53d-b405-4111-afe1-5c082b284971&classId=b82e8325878a4cf4b4fd9fd9c2562643&pageId=eda54ef5a04f4431aa896f812975214d&pageFlag=eda54ef5a04f4431aa896f812975214d&resourceName=lcp-data-object&exposeName=ObjectLinkView",
                "fileId": "link"
            },
            {
                "fileName": "单据id",
                "value": "8a045aeacea741e6ae9a3fb9d9fb78a6",
                "fileId": "transactionId"
            },
            {
                "fileName": "表单id",
                "value": "218ef0557a344798ab3eb7b6c5dcc375",
                "fileId": "classId"
            }
        ]
    ]
}
```

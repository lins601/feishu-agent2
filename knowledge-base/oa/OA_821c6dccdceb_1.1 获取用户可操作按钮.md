---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1fvrd63pqfe60
normalized_url: https://docs.cvte.com/docs/oa/oa-1fvrd63pqfe60
url_hash: 821c6dccdceb
document_key: OA_821c6dccdceb
doc_id: oa-1fvrd63pqfe60
title: 1.1 获取用户可操作按钮
md_hash: fddae79adfd3d8b5
version: 1729221743
image_count: 0
crawled_at: 2026-06-11 16:01:26
---

# 1.1 获取用户可操作按钮

#### 接口说明


 接口名称：1.1 获取用户可操作按钮
 接口地址：https://域名/wfp-api/v1/wfp/api/wf/ext/getOperationList
 请求方式: POST
 Content-Type: application/json


 入参说明


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| formInstanceId | 单据id | 字符串(String) | 不可为空 |  |
| handlerAccount | 特权人域账号 | 字符串(String) | 不可为空 |  |


返回参数说明


| 参数 | 描述 | 类型 | 备注说明 |
| --- | --- | --- | --- |
| activityType | 类型 | 字符串(String) | 调用具体操作接口需使用 ，如管理员废弃、管理员终审通过 |
| taskId | 任务id | 字符串(String) | 调用具体操作接口需使用，如管理员废弃、管理员终审通过 |
| operationType | 操作类型 | 字符串(String) | 调用具体操作接口需使用，如管理员废弃、管理员终审通过 |


#### 举例


请求


```
curl --location 'https://csbsit-api.gz.cvte.cn/wfp-api/v1/wfp/api/wf/ext/getOperationList' \
--header 'Content-Type: application/json' \
--header 'Cookie: BIGipServerpool_yp_ingress_nginx_dev_it=2620265738.26745.0000' \
--data '{
    "formInstanceId": "19289e7394be8dcfdc1e7e34d1faa64a",
    "handlerAccount": "特权人域账号"
}'
```


响应


```
{
    "status": "0",
    "message": null,
    "data": [
        {
            "nodeName": "备份必要性审核",
            "operations": [
                {
                    "operationType": "admin_pass",
                    "operationName": "终审通过",
                    "operationHandlerType": "admin"
                },
                {
                    "operationType": "admin_abandon",
                    "operationName": "直接废弃",
                    "operationHandlerType": "admin"
                },
                {
                    "operationType": "admin_changeCurHandler",
                    "operationName": "修改当前处理人",
                    "operationHandlerType": "admin"
                }
            ],
            "taskFrom": "node",
            "activityType": "reviewNode",
            "nodeId": "N60",
            "taskId": "19289e73e0f2a1977ddd125432dafc44"
        }
    ]
}
```

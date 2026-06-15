---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1fvrdkopf9odn
normalized_url: https://docs.cvte.com/docs/oa/oa-1fvrdkopf9odn
url_hash: 6eff650e06ae
document_key: OA_6eff650e06ae
doc_id: oa-1fvrdkopf9odn
title: 1.2 终审通过 (特权人有权限)
md_hash: 347f2122c0a7dfa6
version: 1729222142
image_count: 0
crawled_at: 2026-06-11 16:01:27
---

# 1.2 终审通过 (特权人有权限)

#### 接口说明


 接口名称：1.2 终审通过 (特权人有权限)
 接口地址：https://域名/wfp-api/v1/wfp/api/wf/ext/approveProcess
 请求方式: POST
 Content-Type: application/json


 入参说明


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| formInstanceId | 单据id | 字符串(String) | 不可为空 |  |
| handlerAccount | 特权人域账号 | 字符串(String) | 不可为空 |  |
| activityType | 类型 | 字符串(String) | 不可为空 | 取自来自 1.1接口的返回数据 activityType |
| operationType | 操作类型 | 字符串(String) | 不可为空 | 固定为 “admin_pass” |
| taskId | 任务id | 字符串(String) | 不可为空 | 取自来自 1.1接口的返回数据 taskId |


返回参数说明


| 参数 | 描述 | 类型 | 备注说明 |
| --- | --- | --- | --- |
| status | 状态 | 字符串(String) | 0:成功，其他值:失败 |


#### 举例


请求


```
curl --location 'https://域名/wfp-api/v1/wfp/api/wf/ext/approveProcess' \
--header 'Content-Type: application/json' \
--data '{
    "formInstanceId": "19289e7394be8dcfdc1e7e34d1faa64a",
    "handlerAccount": "特权人域账号",
    "processParam": {
        "activityType": "reviewNode",
        "operationType": "admin_pass",
        "taskId": "19289e73e0f2a1977ddd125432dafc44"
    }
}'
```


响应


```
{
    "status": "0",
    "message": null,
    "data": null
}
```

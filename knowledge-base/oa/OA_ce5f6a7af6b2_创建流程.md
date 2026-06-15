---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1fb9960fr9cjh
normalized_url: https://docs.cvte.com/docs/oa/oa-1fb9960fr9cjh
url_hash: ce5f6a7af6b2
document_key: OA_ce5f6a7af6b2
doc_id: oa-1fb9960fr9cjh
title: 创建流程
md_hash: cade8adf32dd88c0
version: 1773495270
image_count: 0
crawled_at: 2026-06-11 16:01:30
---

# 创建流程

#### 接口说明


 接口名称：OA原生流程创建流程
 接口地址：https://域名/cvte/api/ekpKmReview.do
 请求方式: POST
 Content-Type: application/json


 入参说明


| 参数 | 描述 | 类型 | 可否为空 | 备注说明 |
| --- | --- | --- | --- | --- |
| method | 方法名称 | 字符串(String) | 不可为空 | 固定为: addReview |
| docSubject | 标题 | 字符串(String) | 不允许为空 |  |
| docStatus | 创建后状态 | 字符串(String) | 不允许为空 | 20 - 待审 10 - 草稿 |
| fdTemplateId | 模板id | 字符串(String) | 不允许为空 | 模板配置时网址链接上的id |
| flowParam | 审批意见备注 | 字符串(String) | 允许为空 |  |
| formValues | 表单数据 | 字符串(String) | 允许为空 |  |


#### 举例


请求


```
curl --location 'https://ekpsit.gz.cvte.cn/cvte/api/ekpKmReview.do?method=addReview' \
--header 'Content-Type: application/json' \
--data '{
    "docSubject": "测试-myflow",
    "docCreator": "{\"LoginName\":\"yeduanwang\"}",
    "docStatus": "10",
    "fdTemplateId": "157028665a8867e99c25e734e87a7b60",
    "flowParam": "{\"auditNode\":\"\"}",
    "formValues": "{\"fd_3c11d40fead616.fd_bom_main_code\":[\"001\",\"002\"],\"fd_tv_licence\": \"无\"}"
}'
```


响应


```
{
    "data": {
        "reviewId": "18d3a86ab8bf567df4e03fd4df198c64",
        "reviewNumber": "WF20240124007"
    },
    "isOk": true,
    "returnMessage": ""
}
```

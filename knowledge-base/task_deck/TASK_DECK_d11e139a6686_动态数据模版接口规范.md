---
source: MinDoc
project_name: TASK_DECK
source_url: https://docs.cvte.com/docs/taskDeck/taskDeck-1h6qtrjal356q
normalized_url: https://docs.cvte.com/docs/taskDeck/taskDeck-1h6qtrjal356q
url_hash: d11e139a6686
document_key: TASK_DECK_d11e139a6686
doc_id: taskDeck-1h6qtrjal356q
title: 动态数据模版接口规范
md_hash: 23e3307da9d20892
version: 1773114531
image_count: 0
crawled_at: 2026-06-11 15:57:09
---

# 动态数据模版接口规范

##### 1、接口入参：


```
{
    method: 'get' / 'post' // 请求方式
    header: {
        'x-iac-token': 'xxxx'   // 用iac的鉴权方式
    }
}
```


##### 2、接口返回


```
{
    "status": 200,
    "message": 'xxx',
    "data": 'https://csbtest-api.gz.cvte.cn/cfile/78076a48-bb75-4f55-be7f-d84e5e657bbe/v1/download/03a9a12456454d6fba9fb189d5ae29e4'   // 附件服务的下载地址
}
```

---
source: MinDoc
project_name: TZC_WF
source_url: https://docs.cvte.com/docs/tzc_wf_integrate/tzc_wf_integrate-1h09tt72hmebo
normalized_url: https://docs.cvte.com/docs/tzc_wf_integrate/tzc_wf_integrate-1h09tt72hmebo
url_hash: f43b5f03ab42
document_key: TZC_WF_f43b5f03ab42
doc_id: tzc_wf_integrate-1h09tt72hmebo
title: 集成demo
md_hash: 8c6f62bbdfa8ece1
version: 1765760980
image_count: 0
crawled_at: 2026-06-11 15:57:30
---

# 集成demo

#### 测试接口


```

//创建流程实例
postman request POST 'http://localhost:9031/test/approve/create' \
  --header 'x-iac-token: e784b0fe-0f65-489f-9099-d7b5beb8109a' \
  --header 'x-tenant-id: c518f53d-b405-4111-afe1-5c082b284971' \
  --header 'Content-Type: application/json' \
  --body '{

}'

//提交流程实例
postman request POST 'http://localhost:9031/test/approve/submit' \
  --header 'x-iac-token: e784b0fe-0f65-489f-9099-d7b5beb8109a' \
  --header 'x-tenant-id: c518f53d-b405-4111-afe1-5c082b284971' \
  --header 'Content-Type: application/json' \
  --body '{

}'

//通过流程实例
postman request POST 'http://localhost:9031/test/approve/pass' \
  --header 'x-iac-token: e784b0fe-0f65-489f-9099-d7b5beb8109a' \
  --header 'x-tenant-id: c518f53d-b405-4111-afe1-5c082b284971' \
  --header 'Content-Type: application/json' \
  --body '{

}'
```


#### 客户端集成demo


[https://kb.cvte.com/pages/viewpage.action?pageId=534512296](https://kb.cvte.com/pages/viewpage.action?pageId=534512296)

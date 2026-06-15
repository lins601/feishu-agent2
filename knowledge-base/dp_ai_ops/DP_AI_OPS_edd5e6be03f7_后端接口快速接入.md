---
source: MinDoc
project_name: DP_AI_OPS
source_url: https://docs.cvte.com/docs/dp_ai_ops/dp_ai_ops-1h9vd8jp9rnag
normalized_url: https://docs.cvte.com/docs/dp_ai_ops/dp_ai_ops-1h9vd8jp9rnag
url_hash: edd5e6be03f7
document_key: DP_AI_OPS_edd5e6be03f7
doc_id: dp_ai_ops-1h9vd8jp9rnag
title: 后端接口快速接入
md_hash: 97965555d2d24b34
version: 1780625621
image_count: 0
crawled_at: 2026-06-11 15:56:58
---

# 后端接口快速接入

### 目标


 指引后端服务对接统一运维问答助手(如dify的http节点)


**(无x-auth-token,要生成x-iac-token)**


### 接口


1、提问接口


```
curl --location 'https://lcpsit.gz.cvte.cn/dpaiops/api/cli/send_question_images' \
--header 'x-iac-token: c051ab7c-a6fc-4975-8452-c7b65151500c' \
--form 'dto=@"/Users/yeduanwang/Downloads/dto.json"' \
--form 'images=@"/Users/yeduanwang/Downloads/改善前图片_1 (2).png"'
```


dto.json的数据:


```
{
    "sessionId": "ec2b79e313bc4d4fbda142b72be5b9",
    "workspaceCode":"scm-pms",
    "question": "附件中有什么?",
    "operatorAccount":"fanhuai",
    "operatorName":"饭饭",
    "agentType":"claude",
    "model":"qwen3.7-plus",
    "mode":"agent"
}
```


2、iac token生成


```
curl --location 'https://iac服务域名/iac/app/access_token?appid=${应用appId}&secret=${应用appsecret}' \
```

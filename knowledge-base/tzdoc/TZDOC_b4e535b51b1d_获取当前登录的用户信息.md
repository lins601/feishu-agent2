---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fmdrqnf3899e
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fmdrqnf3899e
url_hash: b4e535b51b1d
document_key: TZDOC_b4e535b51b1d
doc_id: tzdoc_v2-1fmdrqnf3899e
title: 获取当前登录的用户信息
md_hash: a4b7a437bf0fc720
version: 1718611291
image_count: 0
crawled_at: 2026-06-11 16:12:11
---

# 获取当前登录的用户信息

# 获取当前登录的用户信息


```
const userInfo = context.getContext()?.session?.user ?? {};
// 一般有账号，邮箱，用户id，名称，电话
const { account, email, id, name, telephone } = userInfo;
console.log(account);
```

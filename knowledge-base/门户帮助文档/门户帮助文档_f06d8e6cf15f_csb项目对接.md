---
source: MinDoc
project_name: 门户帮助文档
source_url: https://docs.cvte.com/docs/portal/portal-1h7bsoiem99jf
normalized_url: https://docs.cvte.com/docs/portal/portal-1h7bsoiem99jf
url_hash: f06d8e6cf15f
document_key: 门户帮助文档_f06d8e6cf15f
doc_id: portal-1h7bsoiem99jf
title: csb项目对接
md_hash: 0cf19a89df8563b1
version: 1773711140
image_count: 0
crawled_at: 2026-06-11 17:57:19
---

# csb项目对接

#### csb版本要求


cir-frame：1.10.7 以上
cir-login：1.1.2 以上


#### 对接方式


##### 阿波罗配置


在对接4A基础上增加：


```

loginWith4A: true // 开启4A登录
loginWithOp: true // 开启门户登录

op.host：https://op-fat.cvte.com    // 门户对应环境地址，生产环境：https://home.cvte.com

op.appId：xxxxxx   // 需要跟 未知用户 (zhengnanhui) 申请，测试环境和生产环境不同
```


注意事项：阿波罗配置过来的内容都是字符串，对于判断true和false，需要做处理


#### csb配置


文件：config/server.js
例子：


```
// 应用域名
domain: process.env['domain'],

// 是否开启单点登录
loginWith4A: process.env['loginWith4A'] === 'true',

// 门户配置
op: {
    host: process.env['op.host'],
    appId: process.env['op.appId'],
    loginType: 'op' // 可选值是 4a 或 op，表示选择哪个单点登录页面进行登录
},
// 是否门户单点登录
loginWithOp: process.env['loginWithOp'] === 'true',
```

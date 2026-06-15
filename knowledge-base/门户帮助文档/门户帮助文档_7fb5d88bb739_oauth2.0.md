---
source: MinDoc
project_name: 门户帮助文档
source_url: https://docs.cvte.com/docs/portal/portal-1h7btrpjii5ba
normalized_url: https://docs.cvte.com/docs/portal/portal-1h7btrpjii5ba
url_hash: 7fb5d88bb739
document_key: 门户帮助文档_7fb5d88bb739
doc_id: portal-1h7btrpjii5ba
title: oauth2.0
md_hash: c9afbd2d03774156
version: 1773713119
image_count: 0
crawled_at: 2026-06-11 17:57:19
---

# oauth2.0

# 支持标准 Oauth2.0 的 authorization_code 模式


- 目标业务系统通过 cookie 校验用户会话是否过期，若过期，则需要跳转至门户提供的获取 authorizationCode 接口
- 用户在门户登陆授权后，如果具有访问目标业务系统的权限，则会跳转至上一步所提供的重定向地址
- 目标业务系统获取到 code，请求门户的 access_token 获取接口获取 access_token，此步骤在服务端进行
- 目标业务系统获取到 access_token，再次请求门户的获取用户信息接口，然后根据用户信息完成业务的登陆，此步骤在服务端进行。


## 门户接口


host 分为测试和生产环境（由于 4A 只有两个环境，门户做账号对接，所以只能提供两个环境，业务的 fat 和 uat 环境均对接门户的测试环境。正式环境会校验域名，为了方便开发，测试环境不校验域名）


- 测试：op-fat.cvte.com
- 生产：home.cvte.com


---


### 获取 authorizationCode 接口


**请求方式**


跳转（GET）


**请求地址**


`GET /portal/oauth2/authorize`


**参数说明**


| 参数名 | 类型 | 含义 | 特殊格式说明 |
| --- | --- | --- | --- |
| client_id | query | 必选，系统标识，由门户派发 | 无 |
| redirect_uri | query | 必选，校验用户后重定向的地址，由业务系统自定义 | 请对 query 参数做两层 encode，避免 query 参数中的 query 参数丢失（有点拗口，理解一下即可）。如：`http://xx.cvte.com/oauth_init?next=/home?role=1`，则需要先对 `/home` 做一层 encode，为 `%2fhome%3frole%3d1`，然后再对整个地址做 encode，为 `http%3a%2f%2fxx.cvte.com%2foauth_init%3fnext%3d%252fhome%253frole%253d1` |
| scope | query | 可选，表示申请的权限范围，目前该参数无实际作用 | 无 |
| response_type | query | 必选，表示授权类型 | 此处固定为 “code” |
| state | query | 可选，表示客户端的当前状态 | 可以指定任意值，认证服务器会原封不动地返回这个值 |


**错误说明**


| status | message | response |
| --- | --- | --- |
| 4000001 | client_id 必须提供 | `{'status': '4000001', "message": 'service 或 redirect 必须提供'}` |
| 4000002 | 该用户没有权限 | `{'status': '4000002', "message": '该用户没有权限'}` |
| 4000003 | app 不存在 | `{'status': '4000003', "message": 'app 不存在'}` |
| 4000004 | 当前业务域名没有使用该 app 的权限 | `{'status': '4000004', "message": '当前业务域名没有使用该 app 的权限'}` |
| 5000001 | 获取用户信息失败 | `{'status': '5000001', "message": '获取用户信息失败'}` |
| 5000002 | 生成凭证失败 | `{'status': '5000001', "message": '生成凭证失败'}` |


---


### 获取 accessToken 接口


**请求方式**


POST


**请求地址**


`POST /portal/oauth2/token`


**参数说明**


| 参数名 | 类型 | 含义 | 特殊格式说明 |
| --- | --- | --- | --- |
| grant_type | body | 必选，表示使用的授权模式 | 此处固定为 “authorization_code” |
| code | body | 必选，上一步生成的 code | code 过期时间为 120S |
| redirect_uri | body | 必选，表示重定向 URI | 必须与上一步骤传的参数一致 |


**返回码说明**


| status | message |
| --- | --- |
| 4000001 | 重定向地址校验失败 |
| 4000003 | code 必须提供 |
| 4000004 | code 无效或已过期 |
| 5000001 | code 校验出错 |


**返回格式：**


```
{
  "access_token": "ebc93dfb-fab1-4294-b96d-91d015093428",
  "expires_in": 120
}
```

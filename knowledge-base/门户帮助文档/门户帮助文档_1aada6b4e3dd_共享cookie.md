---
source: MinDoc
project_name: 门户帮助文档
source_url: https://docs.cvte.com/docs/portal/portal-1h7bst9hfn2hq
normalized_url: https://docs.cvte.com/docs/portal/portal-1h7bst9hfn2hq
url_hash: 1aada6b4e3dd
document_key: 门户帮助文档_1aada6b4e3dd
doc_id: portal-1h7bst9hfn2hq
title: 共享cookie
md_hash: 5799cd7e5f2de07d
version: 1773712045
image_count: 0
crawled_at: 2026-06-11 17:57:19
---

# 共享cookie

#### 适用场景


1、用户已经与 4A 进行同步
2、业务拥有自己的定制化登陆界面，通过 API 进行账号密码验证
3、内部员工希望登陆完内部系统之后，再打开业务时无须重新登录，反之亦然。


#### 方案


1、先登录的系统，在保存自身登陆状态的同时，通过域账号到统一登录服务换取用户凭证 ticket
2、往 cvte.com（目前仅支持 cvte.com 的子域名）域下写入 Cookie，key 是 cvte-sso-ticket，value 是 ticket 的值
3、后打开的系统，从 cvte.com 域名下获取 Cookie 中的 ticket
4、调用统一登录服务提供的 API，通过 ticket 换取用户信息
5、保存自身登陆状态


#### API


本系统已对接【接口授权中心】，使用前请查。
host 分为测试和生产环境*（由于 4A 只有两个环境，门户做账号对接，所以只能提供两个环境，业务的 fat 和 uat 环境均对接门户的测试环境。正式环境会校验域名，为了方便开发，测试环境不校验域名）*


测试：op-fat.cvte.com
生产：home.cvte.com


#### 获取 ticket


**请求方式**：跳转（POST）
**请求地址**：/apis/v1/ticket
**参数说明**：


| 参数名 | 类型 | 含义 | 特殊格式说明 |
| --- | --- | --- | --- |
| x-iac-token | header | 校验字段 | 无 |
| username | body | 生成凭证的域账号 | 无 |
| password | body | 生成凭据的密码 | 使用 aes-128 算法加密后的密码，密钥请联系 @未知用户 (zhengnanhui) 获取，参考 [node/Java用AES做对称加密算法](https://kb.cvte.com/pages/viewpage.action?pageId=82989737) |


**错误说明**


| status | message |
| --- | --- |
| 4000000 | iac 校验失败 |
| 5000001 | 获取用户信息失败 |
| 5000002 | 生成凭证失败 |
| 5000004 | 用户名密码校验失败 |


#### 校验 ticket


**请求方式**：跳转（GET）
**请求地址**：/apis/v1/ticket_validate
**参数说明**


| 参数名 | 类型 | 含义 | 特殊格式说明 |
| --- | --- | --- | --- |
| x-iac-token | header | 校验字段 | 无 |
| ticket | query | 凭证 | 无 |


错误说明


| status | message |
| --- | --- |
| 4000000 | iac 校验失败 |
| 4000002 | ticket 无效或已过期 |
| 4000003 | ticket 必须提供 |
| 4000004 | ticket 无效或已过期 |
| 5000001 | ticket 校验失败 |
| 5000002 | 获取用户信息失败 |
| 5000003 | 生成 JWT 失败 |

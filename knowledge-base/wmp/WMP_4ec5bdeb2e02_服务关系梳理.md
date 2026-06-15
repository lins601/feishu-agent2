---
source: MinDoc
project_name: WMP
source_url: https://docs.cvte.com/docs/wmp/wmp-1hceh1hb52u53
normalized_url: https://docs.cvte.com/docs/wmp/wmp-1hceh1hb52u53
url_hash: 4ec5bdeb2e02
document_key: WMP_4ec5bdeb2e02
doc_id: wmp-1hceh1hb52u53
title: 服务关系梳理
md_hash: 57ceffe612b8d9be
version: 1779440903
image_count: 0
crawled_at: 2026-06-11 15:56:50
---

# 服务关系梳理

# WMP 服务关系梳理（基于当前代码）


## 1. 项目角色


`wmp` 是一个聚合网关型服务，核心职责是把不同身份体系和消息渠道打通，主要包含：


- 微信公众号 / 企业微信能力封装（OAuth、菜单、消息、媒体、模板消息等）
- 飞书 OAuth 登录接入
- 微信开放平台绑定关系管理（通过 LCP 数据服务落库）
- 对第三方业务系统提供标准化接口（`/thirdparty/*`）
- 微信支付分能力封装（`/apis/v1/wxpay/*`）


---


## 2. 对接服务清单


## 2.1 外部业务/平台服务


| 服务 | 代码位置（示例） | 作用 |
| --- | --- | --- |
| 微信开放平台（公众平台 API）`api.weixin.qq.com` | `services/wechat_service.js`, `routes/apis/controller.js` | 获取 token、用户信息、创建二维码、模板消息、媒体上传下载、小程序 `jscode2session` |
| 企业微信 API `qyapi.weixin.qq.com` | `services/wechat_service.js` | 企业微信 OAuth 用户识别、成员详情查询、上传媒体、获取 launch_code |
| 飞书 Open API `open.feishu.cn` | `routes/pages/feishu_controller.js` | 飞书 OAuth 授权、换取 app_access_token、换取用户身份 |
| IAC（统一鉴权）`itapis.cvte.com/iac` | `services/iac_service.js`, `common/lcpSdk.js` | 校验 access-token、获取 x-iac-token |
| 组织/用户查询网关 `itgw.cvte.com` | `services/wechat_service.js#getWxIdByUsername` | 通过域账号换取企业微信 `wxId` |
| 用户查询服务 `wx-api.gz.cvte.cn`、`itapis.cvte.com/wuli-service` | `services/wechat_service.js#getUserByWechatId/getUserByKeyword` | 微信ID与组织用户信息映射、按关键词查人 |
| 门户登录服务 `op-api.gz.cvte.cn` | `services/user_service.js#auth` | 账号密码校验（绑定流程） |
| LCP 数据开放接口（`csp-lcp-data/openapi`） | `common/lcpSdk.js`, `services/wx_open_service.js` | 微信开放平台绑定关系、模板消息日志等业务数据读写 |
| 微信支付分接口（通过 `tnwx` 访问） | `routes/wxpay/controller.js` | 创建/查询/取消/修改/完结/同步支付分订单、证书拉取与回调解密 |
| Apollo 配置中心 `apollo*.gz.cvte.cn` | `common/apollo.js`, `startup.js` | 启动时拉取并热更新 `config/default.json` |
| APM `esapm.gz.cvte.cn:8200` | `app.js` | 服务性能与错误监控 |


## 2.2 基础设施服务


| 服务 | 配置位置 | 作用 |
| --- | --- | --- |
| MySQL（`rds.gz.cvte.cn`） | `config/default.json`, `models/sequelize.js` | 持久化 `mp/user/password/thirdparty` 等模型数据 |
| Redis（`redis001.gz.cvte.cn`） | `config/default.json`, `common/redis_helper.js` | OAuth `state/code`、用户态、粉丝列表缓存、短期票据与幂等辅助 |


## 2.3 业务回调服务（由接入方提供）


| 场景 | 配置来源 | 说明 |
| --- | --- | --- |
| 扫码登录回调 | `config.default.scan[].url` | WMP 鉴权后回调业务系统，业务系统判断是否允许登录 |
| OAuth 重定向白名单 | `config.default.oauth` + `oauthRules` | 控制 WMP 只允许向白名单业务域名重定向 |
| 微信支付分回调处理 | `wxpay.mchs[].notifyUrl` | 支付分异步通知后的业务处理（函数回调） |


---


## 3. 服务关系总图
Client FrontendWMP Node ServiceApollo ConfigAPMMySQLRedisWeChat Open APIWeCom APIFeishu Open APIIAC Auth ServiceLCP Data OpenAPIUser Query GatewayPortal Login ServiceWxPay Score APIBiz Callback ServiceOAuth Redirect Biz DomainWxPay Callback Handler

---


## 4. 关键链路关系


## 4.1 企微/飞书 OAuth 登录链路
用户端WMPRedis企业微信/公众号飞书IAC业务系统GET /:id/oauth?url=...保存 state 与回跳URLauthorize + token + userinfoOAuth 换 code/user换 x-iac-tokenalt[飞书UA][非飞书UA]保存 user-code 与用户信息302 回业务系统（携带code）带 code 访问业务页面用户端WMPRedis企业微信/公众号飞书IAC业务系统

## 4.2 微信开放平台绑定关系链路
业务方请求 /api/v1/:id/wx/bind*WMP API ControllerLcpSDKLCP Data OpenAPI微信 API 批量查用户信息Redis缓存粉丝/中间态返回绑定结果

## 4.3 模板消息按账号推送链路
业务方调用 template_by_accountWMPLCP绑定关系查询微信模板消息发送LCP模板消息日志写入/更新

---


## 5. 代码层面的服务分层关系


- `routes/*`：统一入口层，承接 HTTP 请求并组织上下文。
- `middlewares/*`：鉴权与参数守卫层（`api_auth_check`、`thirdparty_auth_check`、`wxpay_check`）。
- `services/*`：外部平台调用层（微信、飞书、IAC、LCP、门户、第三方映射）。
- `models/*`：MySQL 持久化层（Sequelize）。
- `common/*`：跨层基础能力（Redis、Apollo 配置、LCP SDK、日志）。


---


## 6. 当前服务关系的核心结论


- `wmp` 本质上是“**身份与消息聚合中台**”，不是单一微信 SDK 代理。
- 强依赖链路是：`WMP -> IAC -> (微信/企业微信/飞书/LCP)`，IAC 几乎是跨平台调用前置。
- 业务数据有“双存储形态”：基础配置落 MySQL，业务关系与日志大量走 LCP OpenAPI。
- Redis 是会话与临时态中枢（OAuth state/code、缓存用户、粉丝缓存、票据），对登录链路稳定性影响很大。
- 与业务系统耦合点在 `scan.url` 回调与 OAuth 回跳白名单，属于“平台-业务边界”关键配置。

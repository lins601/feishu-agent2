---
source: MinDoc
project_name: WMP
source_url: https://docs.cvte.com/docs/wmp/wmp-1hcep0ndasd78
normalized_url: https://docs.cvte.com/docs/wmp/wmp-1hcep0ndasd78
url_hash: f915d6ee8ffa
document_key: WMP_f915d6ee8ffa
doc_id: wmp-1hcep0ndasd78
title: 路由梳理
md_hash: cb42384f5c42c81d
version: 1779441956
image_count: 0
crawled_at: 2026-06-11 15:56:51
---

# 路由梳理

# WMP API 路由清单（逐条注释）


>


文档范围：基于 `routes/` 目录当前代码，覆盖全部已注册路由。
统计结果：共 **65** 条（`apis` 42 条 + `pages` 9 条 + `thirdparty` 5 条 + `wxpay` 8 条 + `txt` 1 条）。


---


## 1. 鉴权与中间件说明


- `apiAuthCheck`


  - 支持两种校验：


    - `access-token` 直传（优先，调用 IAC `verify_token` 校验）
    - 或 `timestamp + authorization + authId` 组合签名校验


  - 主要用于 `/apis/*`、`/api/v1/*`、`/apis/v1/wxpay/*` 等开放接口


- `thirdpartyAuthCheck`


  - 从 query 获取 `access_token`，在 `thirdparty` 表中匹配
  - 校验通过后向上下文注入 `ctx.mpId`


- `wxpayCheck`


  - 校验 `:wmipid` 与 `:mchid` 是否存在，以及二者是否配置映射关系
  - 成功后注入 `ctx.mpInstance` 与 `ctx.mchInstance`


- `limitCheck`


  - 对 `/apis/:id/bind` 做限流：按 `pid` 每分钟最多 5 次（Redis 计数）


- `wechatCallback`


  - `co-wechat` 中间件，验签并解析微信回调 XML


- `wxOpenEvent`（路由级中间件）


  - `co-wechat` middleware，处理微信开放平台事件回调


---


## 2. API 模块（`routes/apis/index.js`）


| # | 方法 | 路径 | 中间件 | 处理函数 | 详细注释 |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/apis/check` | 无 | `heartbeat` | 健康检查，返回服务可用状态。 |
| 2 | GET | `/apis/wechat-callback` | `wechatCallback` | 中间件内部处理 | 微信服务器回调验签入口（GET）。 |
| 3 | POST | `/apis/wechat-callback` | `wechatCallback` | 中间件内部处理 | 微信消息/事件回调入口（POST），解析消息后当前实现返回空串。 |
| 4 | GET | `/apis/:id/access_token` | `apiAuthCheck` | `getAccessToken` | 获取公众号 access token；支持 `?reset=1` 强制刷新。`:id` 为公众号配置主键。 |
| 5 | GET | `/apis/:id/stable_token` | `apiAuthCheck` | `getStableToken` | 获取微信稳定版 token；支持 `?reset=1`。 |
| 6 | GET | `/apis/:id/user/:userId` | `apiAuthCheck` | `getUser` | 查询公众号用户信息（按 openid/userId）。 |
| 7 | GET | `/apis/:id/menu` | `apiAuthCheck` | `getMenu` | 查询公众号自定义菜单。 |
| 8 | POST | `/apis/:id/menu` | `apiAuthCheck` | `createMenu` | 创建公众号菜单；body 中 `menu` 为 JSON 字符串。 |
| 9 | DELETE | `/apis/:id/menu` | `apiAuthCheck` | `deleteMenu` | 删除公众号菜单。 |
| 10 | POST | `/apis/:id/conditional_menu` | `apiAuthCheck` | `conditionalMenu` | 创建个性化菜单；body `menu` 为 JSON 字符串。 |
| 11 | GET | `/apis/:id/conditional_menu/:userId` | `apiAuthCheck` | `tryConditionalMenu` | 测试个性化菜单命中结果。 |
| 12 | DELETE | `/apis/:id/conditional_menu/:menuId` | `apiAuthCheck` | `deleteConditionalMenu` | 删除个性化菜单（按 menuId）。 |
| 13 | POST | `/apis/:id/message/text` | `apiAuthCheck` | `sendText` | 发送文本客服消息；body 需要 `openid`、`text`。 |
| 14 | POST | `/apis/:id/message/all` | `apiAuthCheck` | `sendMsg` | 按 `msgtype` 发送文本/图片/语音消息；body 字段由消息类型决定。 |
| 15 | POST | `/apis/:id/template` | `apiAuthCheck` | `sendTemplate` | 发送模板消息；参数含 `openid/templateid/url/data/miniprogram` 等。 |
| 16 | GET | `/apis/:id/media/:mediaId` | `apiAuthCheck` | `getMedia` | 获取微信媒体文件并透传二进制返回。 |
| 17 | GET | `/apis/:id/jsconfig` | `apiAuthCheck` | `getJsConfig` | 生成微信 JSSDK 签名；query 需 `url`、`apilist`，可选 `debug`。 |
| 18 | POST | `/apis/:id/create_tmp_qr_code` | `apiAuthCheck` | `createTmpQRCode` | 生成临时二维码；body 需 `sceneId`（number/string 均支持）。 |
| 19 | POST | `/apis/:id/create_tmp_qr_code_with_stable_token` | `apiAuthCheck` | `createTmpQRCodeWithStableToken` | 使用稳定 token 生成临时二维码。 |
| 20 | GET | `/apis/:id/oauth_get_user` | `apiAuthCheck` | `oauthGetUser` | OAuth `code` 换用户信息；支持 `snsapi_base/snsapi_userinfo` 两种范围。 |
| 21 | GET | `/apis/:id/qy_jsconfig` | `apiAuthCheck` | `getQyJsConfig` | 生成企业微信 JSSDK 配置（query 同上）。 |
| 22 | GET | `/apis/:id/qy_oauth_get_user` | `apiAuthCheck` | `qyOauthGetUser` | 企业微信 OAuth 获取用户；优先命中 Redis 的 `code-user` 缓存。 |
| 23 | POST | `/apis/:id/qy_upload_media` | `apiAuthCheck` | `qyUploadMedia` | 企业微信素材上传；query `type` + multipart 文件。 |
| 24 | GET | `/apis/:id/qy_get_user_by_account` | `apiAuthCheck` | `getUserFromQyWechat` | 按域账号查询企业微信用户详情（先映射 wxId，再调企业微信 user/get）。 |
| 25 | GET | `/apis/:id/qy_launch_schema` | `apiAuthCheck` | `getQyLaunchSchema` | 生成企业微信单聊启动 schema；query 要 `operator_username/target_username`。 |
| 26 | GET | `/apis/:id/feishu_get_user_info` | `apiAuthCheck` | `getFeishuUserInfo` | 通过 `code` 读取 Redis 中飞书用户信息（openId/tenantKey/unionId/userId）。 |
| 27 | GET | `/apis/:id/mini_get_user` | `apiAuthCheck` | `miniGetUser` | 小程序 code 换 session 并返回 `openid + 绑定用户名`。 |
| 28 | POST | `/apis/:id/mini_create_user` | `apiAuthCheck` | `miniCreateUser` | 小程序账号绑定：按关键词查人后写入 `user` 表。 |
| 29 | POST | `/apis/:id/create_gesture_password` | 无 | `createGesturePassword` | 创建手势密码；依赖 cookie `username` 与 body `pid/password`。 |
| 30 | POST | `/apis/:id/verify_gesture_password` | 无 | `verifyGesturePassword` | 校验手势密码，成功后返回业务跳转链接。 |
| 31 | POST | `/apis/:id/update_gesture_password` | 无 | `updateGesturePassword` | 更新手势密码；依赖 `pid` 与当前登录态。 |
| 32 | POST | `/apis/:id/bind` | `limitCheck` | `bind` | 域账号绑定微信 openId；会调用门户登录接口做账号密码校验。 |
| 33 | GET | `/apis/:id/config` | `apiAuthCheck` | `getConfig` | 根据公众号配置 id 查询配置详情（`mp` 表）。 |
| 34 | POST | `/api/v1/:id/wx/bind` | `apiAuthCheck` | `wxOpenBind` | 微信开放平台绑定：写入 LCP 绑定表，防止重复绑定。 |
| 35 | DELETE | `/api/v1/:id/wx/bind/:bind_id` | `apiAuthCheck` | `wxOpenUnbind` | 解绑（逻辑删除/状态更新），支持 `?is_deleted=`。 |
| 36 | GET | `/api/v1/:id/wx/bind` | `apiAuthCheck` | `wxOpenSearchBind` | 按 `account/open_id/union_id` 查询绑定关系。 |
| 37 | POST | `/api/v1/:id/wx/bind/correlation` | `apiAuthCheck` | `wxOpenBindByAccount` | 关联绑定：根据其它公众号绑定记录回填当前公众号账号关系。 |
| 38 | GET | `/api/v1/:id/wx/event` | `wxOpenEvent` | 中间件内部处理 | 微信开放平台事件回调（GET 验签）。 |
| 39 | POST | `/api/v1/:id/wx/event` | `wxOpenEvent` | 中间件内部处理 | 微信开放平台事件回调（POST 消息体）。 |
| 40 | GET | `/api/v1/:id/wx/accounts` | `apiAuthCheck` | `wxOpenAccounts` | 批量拉取公众号粉丝并批量写入 LCP 绑定表（大批量，慎用）。 |
| 41 | POST | `/api/v1/:id/wx/template_by_account` | `apiAuthCheck` | `sendTemplateByAccount` | 按账号发送模板消息；支持多绑定记录循环推送并记录消息日志。 |
| 42 | PUT | `/api/v1/wx/template_message/:message_id` | `apiAuthCheck` | `updateMessage` | 更新模板消息日志状态（点击/跳转/失败等）。 |


---


## 3. 页面/OAuth 模块（`routes/pages/index.js`）


| # | 方法 | 路径 | 中间件 | 处理函数 | 详细注释 |
| --- | --- | --- | --- | --- | --- |
| 43 | GET | `/` | 无 | `index` | 页面根入口，返回 `service alive`。 |
| 44 | GET | `/:id/oauth` | 无 | `oauth` | 统一 OAuth 入口：按 UA 自动分流到飞书 OAuth 或微信/企微 OAuth。 |
| 45 | GET | `/feishu_oauth_init` | 无 | `feishuOauthInit` | 飞书 OAuth 回调：换 token、缓存用户、跳回业务 URL。 |
| 46 | OPTIONS | `/:id/oauth` | 无 | `oauth` | OAuth 入口的 OPTIONS 支持（预检/CORS 兼容）。 |
| 47 | GET | `/oauth_init` | 无 | `oauthInit` | 微信/企微 OAuth 回调处理：读取 state、生成业务 code、重定向回业务系统。 |
| 48 | GET | `/:id/qy_scan_login` | 无 | `qyScanLogin` | 企业微信扫码登录入口，拼接授权 URL 跳转企业微信。 |
| 49 | GET | `/:id/qy_scan_login_init` | 无 | `qyScanLoginInit` | 扫码登录回调处理，回调业务系统判定权限。 |
| 50 | GET | `/:id/qy_scan_ticket_verify` | `apiAuthCheck` | `qyScanTicketVerify` | 业务系统用 `ticket` 换取用户信息（一次性票据）。 |
| 51 | GET | `/apis/:id/qy_token` | `apiAuthCheck` | `qyToken` | 查询企业微信 access token。 |


---


## 4. 第三方开放模块（`routes/thirdparty/index.js`）


| # | 方法 | 路径 | 中间件 | 处理函数 | 详细注释 |
| --- | --- | --- | --- | --- | --- |
| 52 | GET | `/thirdparty/token` | 无 | `getAccessToken` | 用 `appid + secret` 换第三方 access token。 |
| 53 | POST | `/thirdparty/custom/msg` | `thirdpartyAuthCheck` | `sendCustomMsg` | 第三方发送客服消息（text/image/voice）。 |
| 54 | GET | `/thirdparty/media` | `thirdpartyAuthCheck` | `getMedia` | 第三方拉取微信媒体文件；query 需 `media_id`。 |
| 55 | POST | `/thirdparty/media` | `thirdpartyAuthCheck` | `uploadMedia` | 第三方上传媒体到微信；query 需 `type`。 |
| 56 | GET | `/thirdparty/user` | `thirdpartyAuthCheck` | `getUser` | 第三方按 `openid` 查微信用户详情。 |


---


## 5. 微信支付分模块（`routes/wxpay/index.js`）


| # | 方法 | 路径 | 中间件 | 处理函数 | 详细注释 |
| --- | --- | --- | --- | --- | --- |
| 57 | GET | `/apis/v1/wxpay/:wmipid/service_order/:mchid/cert` | `apiAuthCheck + wxpayCheck` | `getWxPayCert` | 拉取微信平台证书并做签名校验。 |
| 58 | POST | `/apis/v1/wxpay/:wmipid/service_order/:mchid/need_confirm` | `apiAuthCheck + wxpayCheck` | `createWxPayscoreServiceorderNeedConfirm` | 创建支付分待确认订单。 |
| 59 | POST | `/apis/v1/wxpay/:wmipid/service_order/:mchid/cancel/:out_order_no` | `apiAuthCheck + wxpayCheck` | `cancelWxPayscoreServiceorder` | 取消支付分订单。 |
| 60 | POST | `/apis/v1/wxpay/:wmipid/service_order/:mchid/modify/:out_order_no` | `apiAuthCheck + wxpayCheck` | `modifyWxPayscoreServiceorder` | 修改订单金额/订单字段。 |
| 61 | POST | `/apis/v1/wxpay/:wmipid/service_order/:mchid/complete/:out_order_no` | `apiAuthCheck + wxpayCheck` | `completeWxPayscoreServiceorder` | 完结支付分订单。 |
| 62 | POST | `/apis/v1/wxpay/:wmipid/service_order/:mchid/sync/:out_order_no` | `apiAuthCheck + wxpayCheck` | `syncWxPayscoreServiceorder` | 同步支付分订单服务信息。 |
| 63 | GET | `/apis/v1/wxpay/:wmipid/service_order/:mchid` | `apiAuthCheck + wxpayCheck` | `searchWxPayscoreServiceorder` | 查询支付分订单（`out_order_no/query_id` 二选一）。 |
| 64 | GET | `/apis/v1/wxpay/service_order/suc_callback` | `apiAuthCheck + wxpayCheck` | `sucCallbackWxPayscoreServiceorder` | 支付分回调处理（解密资源并转发业务回调）。 |


---


## 6. 文本验证文件模块（`routes/txt/index.js`）


| # | 方法 | 路径 | 中间件 | 处理函数 | 详细注释 |
| --- | --- | --- | --- | --- | --- |
| 65 | GET | `/txt/:name` | 无 | `getTxt` | 返回微信验证文件内容（从 `config.wx_auth_text[name]` 读取）。 |


---


## 7. 常见路径参数与请求字段说明


-

通用路径参数


  - `:id`：WMP 公众号/企业微信配置 ID（`mp` 表主键）
  - `:wmipid`：微信支付关联的 WMP 公众号配置 ID
  - `:mchid`：微信商户号
  - `:bind_id`：微信开放平台绑定记录 ID（LCP 表记录 ID）
  - `:message_id`：模板消息日志记录 ID（LCP 表记录 ID）
  - `:userId/:menuId/:mediaId/:out_order_no`：对应业务对象标识


-

常见鉴权字段


  - Header：`access-token`
  - 或 Query：`timestamp`, `authId`, `authorization`
  - 第三方开放接口：Query `access_token`


-

常见 Body 结构（本项目使用 `koa-better-body`，多数从 `ctx.request.body.fields` 读取）


  - JSON 字符串类：`menu`
  - 消息类：`openid/text/msgtype/templateid/data/...`
  - OAuth/绑定类：`username/password/pid/open_id/union_id/account`


---


## 8. 维护建议


- 每次新增路由时，同步更新本文件的：


  - 路由总数
  - 对应模块表格
  - 参数说明（若新增关键字段）


- 若变更了中间件链路（如 `apiAuthCheck` 签名算法、`wxpayCheck` 映射校验规则），请同步更新第 1 节。

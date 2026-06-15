---
source: MinDoc
project_name: 天舟云帮助文档_2023
source_url: https://docs.cvte.com/docs/tzv16/tzv16-1f5aa5tjc1nc5
normalized_url: https://docs.cvte.com/docs/tzv16/tzv16-1f5aa5tjc1nc5
url_hash: e6a1ea1ffac1
document_key: 天舟云帮助文档_2023_e6a1ea1ffac1
doc_id: tzv16-1f5aa5tjc1nc5
title: apollo配置
md_hash: 5d69fe442182ad2a
version: 1703468307
image_count: 0
crawled_at: 2026-06-11 17:55:51
---

# apollo配置

- [1、`tianzhou.api`命名空间](https://docs.cvte.com/docs/tzv16/tzv16-1f5aa5tjc1nc5#blfdzv)
- [2、`tianzhou.env`命名空间](https://docs.cvte.com/docs/tzv16/tzv16-1f5aa5tjc1nc5#ior4m)
- [3、`tianzhou.portal`命名空间](https://docs.cvte.com/docs/tzv16/tzv16-1f5aa5tjc1nc5#7z57gl)


# 1、`tianzhou.api`命名空间


**天舟云平台前端用到的转发配置**

```
auth.url = https://csb-admin.gz.cvte.cn/legox/admin/v1
api.baseUrl = https://csb-admin.gz.cvte.cn/legox/admin/v1
api.csbBase = https://csb-admin.gz.cvte.cn/legox
annex.domain = https://csb-api.gz.cvte.cn/cfile/
lcp.hub = https://lcp.gz.cvte.cn/hub
lcp.app = https://lcp.gz.cvte.cn/app
lcp.annex = https://csb-api.gz.cvte.cn/cfile
lcp.faas = https://csb-gw.gz.cvte.cn/api
lcp.gw = https://csb-gw.gz.cvte.cn
cirapi.antdframe = https://csb-admin.gz.cvte.cn/legox/admin/v1
cirapi.menumanager = https://csb-admin.gz.cvte.cn/legox/admin/v1
cirapi.viewManager = https://csb-admin.gz.cvte.cn/legox/admin/v1
lcp.datart = https://lcp.gz.cvte.cn/dataview
lcp.data = https://lcp.gz.cvte.cn/data
lcp.common.server = https://lcp.gz.cvte.cn
#2.0.0新增转发
lcp.tenant.server = https://lcpsit.gz.cvte.cn # 这个域名服务只能填写平台的服务，在pro环境是lcp.gz.cvte.cn，其他环境请填写https://lcpsit.gz.cvte.cn
# 2.1.0 新增转发
lcp.todoUrl = https://todo.gz.cvte.cn/todo
lcp.page = https://lcp.gz.cvte.cn/page
```


# 2、`tianzhou.env`命名空间


**天舟云平台前端用到的环境变量和标识配置**

```
domain = https://tz.gz.cvte.cn
port = 65001
cplp.tenantId = c518f53d-b405-4111-afe1-5c082b284971
annex.categoryId = csb_obj_file
apmConfig.need = true
apmConfig.serviceName = csb
apmConfig.environment = prod
apmConfig.serverUrl = https://esapm.cvte.com
zipkinUrl = https://itrace.gz.cvte.cn
kibanaUrl = http://kibana.test.seewo.com
auth.cipherKey = 4PXP3HfgJtWuiJFu
auth.multiOrg = true
redis.host = redis001.gz.cvte.cn
redis.port = 6379
redis.password = Pass4redisserver
redis.database = 21
redis.prefix = csb
loginWith4A = true
loginWithOp = true
op.host = https://op.cvte.com
op.appId = aec899b37e70430fa0fbaeba7beaebf6
plugin.app.id = 1269620726624013b98cf4baadaa759f
wmp.opMobileByWeChat = true
wmp.appId = d72afa0c-f3c7-40c8-b9cd-ede07599a0cc
wmp.host = https://wmp.cvte.com
wmp.isQy = true
wmp.enable = true
tianzhou.tenent.id = c518f53d-b405-4111-afe1-5c082b284971
lcpAppRcRootApp = lcp-2-1-app
lcpWorkspaceRcRootApp = lcp-workspace-2-1-app
systemType = system
#2.0.0新增转发
systemPrefixs = {"portalRouterPrefix":"/portal","appRouterPrefix":"/system","workspaceRouterPrefix":"/workspace"}
systemRouterPrefix = /system
# 2.1.0 新增环境变量
routerPrefix = /workspace
lcp.sentry.config = {"open":"true","replay":"true"}
```


# 3、`tianzhou.portal`命名空间


**天舟云门户系统前端用到的环境变量和标识配置**

```
systemRouterPrefix = /portal
isPortal = true
systemType = portal
```

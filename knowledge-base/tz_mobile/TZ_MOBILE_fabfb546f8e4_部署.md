---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucifk93vafm
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucifk93vafm
url_hash: fabfb546f8e4
document_key: TZ_MOBILE_fabfb546f8e4
doc_id: tz-mobile-1gucifk93vafm
title: 部署
md_hash: b1dbd313e962f430
version: 1763621061
image_count: 0
crawled_at: 2026-06-11 15:57:44
---

# 部署

## 概述


项目支持以下部署方式：


- ✅ Docker 容器部署
- ✅ 静态文件部署
- ✅ 服务器部署
- ✅ CI/CD 自动部署


## 环境要求


- Node.js >= 18.0.0
- pnpm >= 8.0.0
- Docker (可选)


## 构建项目


### 开发环境构建


```
# SIT 环境
CONFIG_ENV=sit pnpm run build:sit

# UAT 环境
CONFIG_ENV=uat pnpm run build:uat
```


### 生产环境构建


```
CONFIG_ENV=production pnpm run build
```


### 启动应用


```
# 启动应用
pnpm start
```

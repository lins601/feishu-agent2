---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guci9g72ssnh
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guci9g72ssnh
url_hash: d14eccabc610
document_key: TZ_MOBILE_d14eccabc610
doc_id: tz-mobile-1guci9g72ssnh
title: 环境配置
md_hash: 52919383ea3d5a90
version: 1763620937
image_count: 0
crawled_at: 2026-06-11 15:57:39
---

# 环境配置

## 概述


框架支持以下环境：


- **local** - 本地开发环境
- **sit** - 测试环境
- **uat** - 预发布环境
- **production** - 生产环境


## 配置文件位置


配置文件位于 `src/config/` 目录：


```
src/config/
├── config.local.js      # 本地环境配置
├── config.sit.js        # 测试环境配置
├── config.uat.js        # 预发布环境配置
└── config.production.js # 生产环境配置
```


## 环境变量


### 设置环境


通过环境变量 `CONFIG_ENV` 指定当前环境：


```
# 开发环境
CONFIG_ENV=sit pnpm run dev

# 生产环境构建
CONFIG_ENV=production pnpm run build
```

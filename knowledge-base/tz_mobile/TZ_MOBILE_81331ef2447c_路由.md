---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guci92gmm40r
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guci92gmm40r
url_hash: 81331ef2447c
document_key: TZ_MOBILE_81331ef2447c
doc_id: tz-mobile-1guci92gmm40r
title: 路由
md_hash: c319cfced5f72561
version: 1763619469
image_count: 0
crawled_at: 2026-06-11 15:57:38
---

# 路由

# 路由


## 概述


路由系统基于 Next.js 14 的 App Router。


## 添加路由


在 `src/app` 目录下创建文件夹和 `page.tsx` 文件即可创建路由。


### 示例


创建 `src/app/about/page.tsx` 文件：


```
export default function AboutPage() {
  return <div>关于我们</div>;
}
```


访问路径：`/about`


### 动态路由


创建 `src/app/user/[id]/page.tsx` 文件：


```
export default function UserPage({ params }: { params: { id: string } }) {
  return <div>用户 ID: {params.id}</div>;
}
```


访问路径：`/user/123`


### 路由组


使用括号创建路由组（不会影响 URL）：


```
src/app/
├── (auth)/
│   ├── login/
│   │   └── page.tsx
│   └── register/
│       └── page.tsx
└── (dashboard)/
    ├── home/
    │   └── page.tsx
    └── profile/
        └── page.tsx
```

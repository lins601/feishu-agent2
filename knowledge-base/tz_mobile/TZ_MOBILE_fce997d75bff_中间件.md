---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guci997egg59
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guci997egg59
url_hash: fce997d75bff
document_key: TZ_MOBILE_fce997d75bff
doc_id: tz-mobile-1guci997egg59
title: 中间件
md_hash: 32a06ce60fc34761
version: 1763619746
image_count: 0
crawled_at: 2026-06-11 15:57:38
---

# 中间件

# 中间件


## 文件位置


在 `src/middleware.ts` 引入中间件文件，然后在 `middleware` 函数中调用。


## 参数说明


### middleware 函数参数


```
export default async function middleware(
  request: NextRequest,    // 请求对象
  event: NextFetchEvent   // 事件对象
)
```


### Middleware 类型


```
type Middleware<T = NextRequest, R = any> = (
  request: NextRequest,
  context: {
    event: NextFetchEvent;
    next: () => Promise<R | undefined>;
  }
) => Promise<R | undefined>;
```


## 示例


### 基础中间件


```
// src/middleware.ts
import { NextRequest, NextResponse, NextFetchEvent } from 'next/server';

export default async function middleware(
  request: NextRequest,
  event: NextFetchEvent
) {
  // 中间件逻辑
  const token = request.cookies.get('x-auth-token')?.value;

  if (!token) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  return NextResponse.next();
}

// 配置匹配规则
export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|api/trpc).*)'],
};
```


### 自定义中间件插件


```
// src/middlewarePlugin/myMiddleware.ts
import type { Middleware } from '@/lib/middleware/compose';
import type { NextRequest } from 'next/server';
import { NextResponse } from 'next/server';

export const myMiddleware: Middleware<NextRequest, NextResponse> = async (
  request,
  context
) => {
  // 修改请求头
  request.headers.set('custom-header', 'value');

  // 继续执行下一个中间件
  return await context.next();
};
```


### 注册中间件插件


```
// src/middleware.ts
import { middlewareMain } from './middleware';
import { tenantMiddlewareInstance } from './middlewarePlugin/tenantId';
import { myMiddleware } from './middlewarePlugin/myMiddleware';

export default async function middleware(
  request: NextRequest,
  event: NextFetchEvent
) {
  return await middlewareMain(request, event, [
    await tenantMiddlewareInstance.getMiddleware(),
    myMiddleware,
  ]);
}
```


### 租户中间件示例


```
// src/middlewarePlugin/tenantId.ts
import { TenantMiddleware } from '@/middlewarePlugin/tenantId';

export const tenantMiddlewareInstance = new TenantMiddleware({
  tenantFieldName: 'x-tenant-id',      // 租户字段名
  devDomain: 'lcpsit.gz.cvte.cn',      // 开发环境域名
  forceReplace: true,                  // 是否强制替换
  tenantHost: process.env.TENANT_HOST, // 租户服务地址
});
```

---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucigq8igh5i
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucigq8igh5i
url_hash: 6a6ad4c5c21b
document_key: TZ_MOBILE_6a6ad4c5c21b
doc_id: tz-mobile-1gucigq8igh5i
title: 表单
md_hash: bb0adfd1f4eda01b
version: 1763689512
image_count: 0
crawled_at: 2026-06-11 15:57:42
---

# 表单

# 表单模板使用指南


## 概述


表单模板提供了两种使用方式：


- **TZFormTemplate** - 服务端组件，自动获取表单配置（推荐）
- **Client** - 客户端组件，需要手动传入表单配置


## 方式一：使用 TZFormTemplate（推荐）


### 特点


- ✅ 自动获取表单配置
- ✅ 自动加载表单数据
- ✅ 支持服务端渲染
- ✅ 简化使用流程


### 使用方法


在 `src/app` 目录下创建页面文件：


```
// src/app/my-form/page.tsx
import TZFormTemplate from '@cvte/tz-mobile';

export default async function MyFormPage(props: {
  searchParams: Record<string, string>;
  params: Record<string, string>;
}) {
  const { searchParams } = props;
  const {
    itemId,        // 数据项 ID
    appId,         // 应用 ID
    tenantId,      // 租户 ID
    classId,       // 表单类 ID（必需）
    opType,        // 操作类型
    metaConfig,    // 元配置
    copyId,        // 复制 ID
    ...rest
  } = searchParams || {};

  // 设置请求头
  const headers = {
    'x-app-id': appId,
    'x-tenant-id': tenantId,
  };

  return (
    <TZFormTemplate
      classId={classId}              // 表单类 ID（必需）
      itemId={copyId || itemId}       // 数据项 ID
      headers={headers}               // 请求头
      opType={opType}                 // 操作类型：'copy' | 'copy_create'
      metaConfig={metaConfig}         // 元配置
      contextData={rest}              // 上下文数据
    />
  );
}
```


### URL 参数


通过 URL 参数传递配置：


```
/my-form?classId=form-class-123&itemId=456&appId=app-1&tenantId=tenant-1&opType=edit
```


### 参数说明


```
interface IFormTemplateProps {
  classId?: string;                  // 表单类 ID（必需，用于获取表单配置）
  itemId?: string;                   // 数据项 ID（编辑/查看时必需）
  headers?: Record<string, string>;  // 请求头
  opType?: 'copy' | 'copy_create';   // 操作类型
  currentNodeCode?: string;           // 当前流程节点代码
  designId?: string;                  // 设计 ID
  designApi?: string;                 // 设计 API
  metaConfig?: {                     // 元配置
    formDefaultData?: Record<string, any>;  // 表单默认数据
    contextData?: Record<string, any>;      // 上下文数据
    compParams?: Record<string, any>;        // 组件参数
  };
  contextData?: Record<string, any>;  // 上下文数据（兼容 URL query 参数）
}
```


### 参数详解


#### classId（必需）


表单类 ID，用于从服务端获取表单配置。如果不提供 `formConfig`，组件会自动通过 `classId` 获取配置。


#### itemId


数据项 ID，用于编辑或查看已有数据：


- **创建模式**：不传 `itemId` 或 `itemId` 为空
- **编辑模式**：传入 `itemId`
- **查看模式**：传入 `itemId`，表单会自动设置为只读


#### headers


请求头，用于 API 调用时传递认证信息等：


```
const headers = {
  'x-app-id': appId,
  'x-tenant-id': tenantId,
  'x-auth-token': token,
};
```


#### opType


操作类型：


- `copy` - 复制模式，不保存数据
- `copy_create` - 复制并创建，保存数据


#### metaConfig


元配置，包含表单的额外配置信息：


```
const metaConfig = {
  formDefaultData: {        // 表单默认数据
    field1: 'default value',
  },
  contextData: {           // 上下文数据
    parentId: '123',
  },
  compParams: {            // 组件参数
    customParam: 'value',
  },
};
```


#### contextData


上下文数据，可以通过 URL query 参数传递，会被合并到 `metaConfig.contextData` 中。


## 方式二：使用 Client 组件


### 特点


- ✅ 客户端组件
- ✅ 需要手动传入表单配置
- ✅ 更灵活的控制


### 使用方法


```
// src/app/my-form/page.tsx
'use client';

import Client from '@/components/business/forms/Client';

export default function MyFormPage(props: {
  searchParams: Record<string, string>;
}) {
  const { classId, itemId } = props.searchParams;

  // 手动定义表单配置
  const formConfig = {
    id: 'form-1',
    code: 'my-form',
    name: '我的表单',
    layout: [
      {
        id: 'layout-1',
        code: 'main',
        type: 'LAYOUT',
        children: [
          {
            id: 'field-1',
            code: 'name',
            type: 'INPUT',
            config: {
              baseConfig: {
                default: {
                  isVisible: true,
                  name: '名称',
                  attrType: 'INPUT',
                },
              },
            },
          },
        ],
      },
    ],
  };

  return (
    <Client
      formConfig={formConfig}        // 表单配置（必需）
      classId={classId}              // 表单类 ID
      itemId={itemId}                // 数据项 ID
      opType="edit"                  // 操作类型
    />
  );
}
```


## 完整示例


### 示例 1: 创建表单


```
// src/app/create-form/page.tsx
import TZFormTemplate from '@/components/business/forms';

export default async function CreateFormPage(props: {
  searchParams: Record<string, string>;
}) {
  const { classId, appId, tenantId } = props.searchParams;

  const headers = {
    'x-app-id': appId,
    'x-tenant-id': tenantId,
  };

  return (
    <TZFormTemplate
      classId={classId}
      headers={headers}
    />
  );
}
```


访问：`/create-form?classId=form-class-123&appId=app-1&tenantId=tenant-1`


### 示例 2: 编辑表单


```
// src/app/edit-form/page.tsx
import TZFormTemplate from '@/components/business/forms';

export default async function EditFormPage(props: {
  searchParams: Record<string, string>;
}) {
  const { classId, itemId, appId, tenantId } = props.searchParams;

  const headers = {
    'x-app-id': appId,
    'x-tenant-id': tenantId,
  };

  return (
    <TZFormTemplate
      classId={classId}
      itemId={itemId}
      headers={headers}
    />
  );
}
```


访问：`/edit-form?classId=form-class-123&itemId=456&appId=app-1&tenantId=tenant-1`


### 示例 3: 复制表单


```
// src/app/copy-form/page.tsx
import TZFormTemplate from '@/components/business/forms';

export default async function CopyFormPage(props: {
  searchParams: Record<string, string>;
}) {
  const { classId, itemId, appId, tenantId } = props.searchParams;

  const headers = {
    'x-app-id': appId,
    'x-tenant-id': tenantId,
  };

  return (
    <TZFormTemplate
      classId={classId}
      itemId={itemId}
      headers={headers}
      opType="copy"  // 复制模式，不保存
    />
  );
}
```


### 示例 4: 带默认数据的表单


```
// src/app/form-with-default/page.tsx
import TZFormTemplate from '@/components/business/forms';

export default async function FormWithDefaultPage(props: {
  searchParams: Record<string, string>;
}) {
  const { classId, appId, tenantId } = props.searchParams;

  const headers = {
    'x-app-id': appId,
    'x-tenant-id': tenantId,
  };

  const metaConfig = {
    formDefaultData: {
      name: '默认名称',
      status: 'active',
    },
    contextData: {
      parentId: '123',
    },
  };

  return (
    <TZFormTemplate
      classId={classId}
      headers={headers}
      metaConfig={metaConfig}
    />
  );
}
```


### 示例 5: 通过 URL 传递上下文数据


```
// src/app/form-with-context/page.tsx
import TZFormTemplate from '@/components/business/forms';

export default async function FormWithContextPage(props: {
  searchParams: Record<string, string>;
}) {
  const { classId, appId, tenantId, parentId, category, ...rest } = props.searchParams;

  const headers = {
    'x-app-id': appId,
    'x-tenant-id': tenantId,
  };

  return (
    <TZFormTemplate
      classId={classId}
      headers={headers}
      contextData={rest}  // URL 中的其他参数会作为上下文数据传递
    />
  );
}
```


访问：`/form-with-context?classId=form-class-123&parentId=456&category=electronics`


## 注意事项


-

**classId 必需**：使用 `TZFormTemplate` 时，`classId` 是必需的，用于获取表单配置。

-

**服务端组件**：`TZFormTemplate` 是服务端组件，不能使用 `'use client'` 指令。

-

**客户端组件**：`Client` 组件是客户端组件，需要使用 `'use client'` 指令。

-

**数据加载**：如果提供了 `itemId`，组件会自动加载对应的表单数据。

-

**元配置格式**：`metaConfig` 可以是对象或 JSON 字符串，组件会自动解析。

-

**上下文数据合并**：`contextData` 会合并到 `metaConfig.contextData` 中，`metaConfig.contextData` 优先级更高。

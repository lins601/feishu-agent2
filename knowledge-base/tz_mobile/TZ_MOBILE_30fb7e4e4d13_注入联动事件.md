---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucq7g3ndic7
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucq7g3ndic7
url_hash: 30fb7e4e4d13
document_key: TZ_MOBILE_30fb7e4e4d13
doc_id: tz-mobile-1gucq7g3ndic7
title: 注入联动事件
md_hash: 9f4260728147f2ad
version: 1763621867
image_count: 0
crawled_at: 2026-06-11 15:57:44
---

# 注入联动事件

## 文件位置


```
import type { IEventRegistryItem } from '@cvte/mobile-framework';
```


## 字段说明


```
interface IEventRegistryItem {
  code: string;                    // 事件代码（唯一标识）
  title: string;                   // 事件标题
  description: string;             // 事件描述
  event: () => Promise<any>;       // 事件处理函数
}
```


### 联动事件参数


```
interface LinkageEventParams {
  sourceFieldPath: string;           // 触发字段路径
  sourceFieldRowInfo?: IArrayContext; // 触发字段所在行信息
  targetFieldPath: string;           // 目标字段路径
  formula?: { rule: string };        // 公式规则（如果有）
}
```


### 联动上下文


```
interface ILinkageContext {
  arrayContext?: IArrayContext;      // 数组上下文（表格行信息）
  tempStateManager: TempStateManager; // 临时状态管理器
  configStore: ConfigStore;          // 配置存储
  formData: Record<string, any>;      // 表单数据
}
```


## 注入示例


```
// src/components/business/injectEvents.ts
export const baseEventConfig: IEventRegistryItem[] = [
  {
    code: '__form:calculateExpression',
    title: '表单联动公式计算',
    description: '表单联动公式计算',
    event: () =>
      import('@/components/business/forms/utils/handleRelateFormula'),
  },
  {
    code: '__myLinkageEvent',
    title: '我的联动事件',
    description: '自定义联动事件示例',
    event: () =>
      import('@/components/business/forms/utils/events/myLinkageEvent'),
  },
];
```


### 联动事件处理函数示例


```
// src/components/business/forms/utils/events/myLinkageEvent.ts
import { ILinkageContext } from '@cvte/mobile-framework';

export default async function myLinkageEvent(
  context: ILinkageContext,
  params: LinkageEventParams
) {
  const { tempStateManager } = context;
  const { sourceFieldPath, targetFieldPath } = params;

  // 获取触发字段的值
  const sourceValue = tempStateManager.getData(sourceFieldPath);

  // 联动处理逻辑
  tempStateManager.updateData(targetFieldPath, sourceValue);

  return {
    success: true,
  };
}
```


## 引入和注册


在 `src/components/layout/MobileFrameworkWrapper.tsx` 中引入并注册：


```
// src/components/layout/MobileFrameworkWrapper.tsx
import { registerEvent } from '@cvte/mobile-framework';
import { baseEventConfig } from '@/components/business/injectEvents';

// 在文件底部注册联动事件
registerEvent(baseEventConfig);
```

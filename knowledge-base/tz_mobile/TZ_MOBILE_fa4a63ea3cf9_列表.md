---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucigiijsh5n
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucigiijsh5n
url_hash: fa4a63ea3cf9
document_key: TZ_MOBILE_fa4a63ea3cf9
doc_id: tz-mobile-1gucigiijsh5n
title: 列表
md_hash: d7a1073054c6c12d
version: 1763976787
image_count: 1
crawled_at: 2026-06-11 15:57:40
---

# 列表

# 移动端Table


## 渲染机制


[图片1: null]


## 事件触发机制


Table 组件使用 `eventCenter` 来统一管理事件触发。


#### 事件的执行流程：


##### 事件执行遵循 **preEvent → event → afterEvent** 的顺序：


```
// 1. 检查按钮是否禁用
if (disabled) return;

// 2. 执行前置事件 (preEvent)
const preData = await runMain(configs, { event: preEvent });
if (preData?.success === false) return preData;

// 3. 执行主事件 (event)
const data = await runMain(configs, { preData, event });
if (data?.success === false) return data;

// 4. 执行后置事件 (afterEvent)
const afterData = await runMain(configs, {
  preData: data,
  event: afterEvent,
});

return afterData;
```


#### 事件类型：


local事件（runType: ‘local’）, 本地事件通过事件注册表查找并执行：
内部实现细节如下：


```
// 1. 获取事件名称
const eventName = event?.customEventName || event?.name;

// 2. 从注册表获取事件配置
const fnConfig = getEvent(eventName);

// 3. 动态加载事件函数
const fn = await fnConfig.event();

// 4. 执行事件函数
const runFn = fn?.default || fn?.[eventName];
const data = await runFn?.(configs, extra);
```


#### 事件注册：


事件需要在应用启动时进行注册。根据运行环境的不同，事件分为两种类型：`服务端事件`和`客户端事件`。


>


举例：如列表加载数据的事件。首屏是服务端渲染的，所以第一次数据加载是在服务端进行的。点击换页时，这时是在客户端进行的。所以加载数据的事件是需要在服务端和客户端各注册一次。


服务端注册路径：`src/app/layout.tsx`
客户端注册路径：`src/components/layout/MobileFrameworkWrapper.tsx`


```
import { registerEvent } from '@/components/registry';

registerEvent([
  {
    code: 'deleteRow', // 事件代码
    name: '删除行', // 事件名称
    event: () => import('./events/deleteRow'), // 事件函数模块
  },
]);

// 或者是：
export const loadDataConfig = {
  code: 'lcpLoadTableData',
  title: '加载数据列表数据',
  description: '',
  event: handleLcpLoadTableData,
};
```


#### 事件上下文：


```
interface IActionButtonContext {
  /** 选中的行数据 */
  selectedRowData: Record<string, any>[];
  /** 当前行的行数据 */
  currentRowData?: Record<string, any>;
  /** 刷新当前列表的函数 */
  refresh: () => Promise<void>;
  /** 跳转路由的函数 */
  routeJump: (url: string) => void;
  /** 额外参数 */
  extraParams?: Record<string, any>;
}
```


## 组件注入


[https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guciaks6la62](https://docs.cvte.com/docs/tz-mobile/tz-mobile-1guciaks6la62)


## 如何使用


```
import { ServerRender as Table } from '@cvte/mobile-framework';
```


## 参数说明


| 字段 | 说明 | 类型 | 必填 | 默认值 |
| --- | --- | --- | --- | --- |
| `layout` | 表格布局配置数组，定义表格的结构、列、操作按钮等 | `ILayoutItem[]` | 是 | - |
| `context` | 上下文对象，包含请求头信息和页面内容信息 | `{ headers: {appId: string;tenantId: string}; content?: { pageId: string; areaCode: string; [propsName: string]: any } }` | 是 | - |
| `apiConfig` | API 配置映射，用于自定义接口地址和请求方法 | `{ apiMapping?: Record<string, { url: string; method: string }> }` | 否 | - |
| `initialState` | 初始状态，用于设置初始筛选条件 | `{ filterData?: Record<string, any> }` | 否 | - |
| `initialData` | 服务端预获取数据 | `{ tree: IOptionItem[]; dataSource: any[]; total: number; hasMore: boolean; loading: boolean; sortKey?: string; sortDirection?: 'asc' \| 'desc' }` | 否 | - |
| `translatedData` | 服务端预翻译的值 | `IBatchTranslateResult` | 否 | - |
| `layoutMapping` | 布局映射配置，用于表单字段映射 | `IFormLayoutMapping` | 否 | - |


## 详细类型说明


### ILayoutItem


表格布局项类型，定义表格的结构配置。这是表格布局系统的核心数据结构，用于描述表格、筛选器、按钮等组件的配置信息。


#### 接口定义


```
interface ILayoutItem {
  id: string; // 布局项唯一标识符（通常使用 UUID）
  code: string; // 布局项代码，用于标识和查找
  name: string; // 布局项显示名称
  type: string; // 布局项类型，决定渲染的组件类型
  buttons?: ILayoutItem[] | null; // 按钮配置数组，用于定义操作按钮
  config: Config; // 配置对象，包含基础配置、UI配置、规则配置等
  children?: ILayoutItem[] | null; // 子布局项数组，支持嵌套结构
}
```


#### 字段详细说明


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `string` | 是 | 布局项的唯一标识符，通常使用 UUID 生成，用于 React 的 key 属性 |
| `code` | `string` | 是 | 布局项的代码标识，用于在布局树中查找和定位特定的布局项 |
| `name` | `string` | 是 | 布局项的显示名称，用于界面展示和用户识别 |
| `type` | `string` | 是 | 布局项类型，决定该布局项会被渲染成什么组件。常见值见下方 |
| `buttons` | `ILayoutItem[] \| null` | 否 | 按钮配置数组，定义该布局项关联的操作按钮（如工具栏按钮、行操作按钮等） |
| `config` | `Config` | 是 | 配置对象，包含 `baseConfig`（基础配置）、`uiConfig`（UI配置）、`rulesConfig`（规则配置）等 |
| `children` | `ILayoutItem[] \| null` | 否 | 子布局项数组，支持嵌套的布局结构，用于构建复杂的布局树 |


#### type枚举值


| type 值 | 说明 | 用途 |
| --- | --- | --- |
| `'layout'` | 布局容器 | 最外层的布局容器，通常包含整个页面的布局结构 |
| `'TABLE'` | 表格组件 | 定义表格的列、数据源等 |
| `'FILTER'` | 筛选表单 | 定义头部筛选条件的表单字段 |
| `QUICK_SEARCH` | 快速搜索 | 定义快速搜索的字段 |
| `'LCP_PAGE_TREE'` | 树形组件 | 定义左侧树形导航 |
| `'LCP_FLEX_CONTAINER'` | 弹性容器 | 用于包裹表格和筛选器的容器 |
| `'BUTTON'` | 按钮组件 | 操作按钮 |
| `'COLUMN'` | 表格列 | 定义表格的列配置 |
| `CUSTOM_TOOL_EXTRA` | 工具 | 自定义工具 |
| `LIST` | 列表行 | 列表中的每一行 |


#### `Config` 类型结构


```
interface Config {
  baseConfig: BaseConfig & {
    dictConfig?: DictConfig; // 字典配置
    dictId?: string; // 字典ID
  };
  uiConfig?: Record<string, any>; // UI配置，用于自定义样式和展示
  rulesConfig?: Record<string, any>; // 规则配置，用于定义业务规则
}

interface BaseConfig {
  default: {
    isVisible?: boolean; // 是否显示
    isHideColumnTitle?: boolean; // 是否隐藏子列标题
    name?: string; // 字段名称
    attrType?: string; // 字段类型（如 'text', 'number', 'date' 等）
    placeholder?: string; // 字段占位符
    maxVisibleActions?: number; // 最大可见操作按钮数
    checkable?: {
      // 勾选配置
      isEnable?: boolean; // 是否启用勾选
      mode?: 'checkbox' | 'radio'; // 勾选模式
    };
    isCardTitle?: boolean; // 是否作为卡片标题
    isCardExtra?: boolean; // 是否作为卡片额外内容
    sortable?: boolean; // 是否可排序
    event?: {
      // 事件配置
      runType: 'local'; // 运行类型
      name: string; // 事件名称
      params?: Record<string, any>; // 事件参数
    };
    [propName: string]: any; // 其他扩展属性
  };
  [props: string]: any; // 其他场景的配置
}
```


#### ILayoutItem使用示例


[https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gun21vvt2jtm](https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gun21vvt2jtm)


### `IHeaderContext`


请求头上下文类型：


```
{
  appId: string; // 应用ID
  tenantId: string; // 租户ID
}
```


### `context.content`


页面内容上下文类型：


```
{
  pageId: string;           // 页面ID
  areaCode: string;         // 区域代码
  [propsName: string]: any;  // 其他自定义属性
}
```


### `apiConfig.apiMapping`


API 映射配置类型：


```
Record<
  string,
  {
    url: string; // 接口地址
    method: string; // 请求方法（如 'GET', 'POST' 等）
  }
>;
```


### `initialState.filterData`


初始筛选数据类型：


```
Record<string, any>; // 键值对形式的筛选条件
```


### `initialData`


初始数据类型：


```
{
  tree: IOptionItem[];              // 树形数据
  dataSource: any[];                // 表格数据源
  total: number;                    // 总数据条数
  hasMore: boolean;                 // 是否还有更多数据
  loading: boolean;                 // 加载状态
  sortKey?: string;                 // 排序字段
  sortDirection?: 'asc' | 'desc';   // 排序方向
}
```


### `IBatchTranslateResult`


批量翻译结果类型：


```
{
  translatedFormData: Record<string, any>; // 翻译后的表单数据
  optionsData: IClientFormProps['optionsData']; // 翻译过程中的选项数据
}
```


### `IFormLayoutMapping`


表单布局映射类型：


```
Record<string, IFormLayoutMappingItem>;
```


### `IOptionItem`


选项项类型，用于树形数据。


## 使用示例


基于 `lcpAdvanceTableTemplate/page.tsx` 的实际使用：


```
import { ServerRender as Table } from '@cvte/mobile-framework';

const layoutTemplate = {
  id: uuid(),
  code: layoutCode,
  name: pageInfo?.data?.pageName,
  type: 'layout',
  buttons: null,
  config: {
    baseConfig: {
      default: {
        isVisible: true,
        name: pageInfo?.data?.pageName,
        attrType: 'layout',
      },
    },
  },
  children: layoutData.layout,
};

<Table
  layout={[layoutTemplate]}
  apiConfig={{
    apiMapping: layoutData.apiConfig,
  }}
  initialState={{
    filterData: defaultFilterData || {},
  }}
  context={{
    headers: { tenantId, appId },
    content: { pageId, areaCode: layoutCode },
  }}
/>;
```


## 注意事项


- **必需参数**：`layout` 和 `context` 是必需的，必须提供
- **context.headers**：必须包含 `appId` 和 `tenantId`
- **context.content**：建议包含 `pageId` 和 `areaCode`，用于标识页面和区域
- **服务端渲染**：`ServerRender` 组件用于服务端渲染场景，会自动处理数据获取和翻译
- **initialState.filterData**：用于设置初始筛选条件，通常从布局中的 FILTER 元素获取默认值
- **apiConfig.apiMapping**：用于自定义或覆盖默认的 API 配置


## 相关类型定义


详细的类型定义请参考：


- `ITable`: `/src/components/table/type/index.interface.d.ts`
- `ILayoutItem`: 来自 `[@cvte](https://github.com/cvte)/mobile-framework`
- `IHeaderContext`: `/src/components/table/type/index.interface.d.ts`

---

# 文档图片附录


---

## 图片1: null

![null](data:image/jpeg;base64,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)



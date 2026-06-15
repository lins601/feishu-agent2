---
source: MinDoc
project_name: TZ_MOBILE
source_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucq43326m06
normalized_url: https://docs.cvte.com/docs/tz-mobile/tz-mobile-1gucq43326m06
url_hash: f52ce0f6d288
document_key: TZ_MOBILE_f52ce0f6d288
doc_id: tz-mobile-1gucq43326m06
title: 联动
md_hash: 9ca7adbdf468f7f6
version: 1763622227
image_count: 0
crawled_at: 2026-06-11 15:57:42
---

# 联动

# 表单联动


表单联动功能允许字段之间相互影响，实现动态显示、隐藏、禁用、赋值等操作。


## 概述


表单联动支持以下功能：


- ✅ 字段显示/隐藏控制
- ✅ 字段启用/禁用控制
- ✅ 字段值自动计算
- ✅ 字段选项动态更新
- ✅ 公式计算支持
- ✅ 条件判断支持


## 联动配置


### 联动数据结构


```
interface IRelation {
  formCode: string;              // 表单代码
  attrCode: string;              // 触发字段代码
  actionCodes: string[];         // 触发事件：'change', 'blur', 'focus', 'search'
  groups: IRelationGroup[];     // 联动组
}

interface IRelationGroup {
  id: string;                    // 组 ID
  conditions: IRelationCondition[];  // 条件列表
  relations: IRelationAction[];  // 联动操作列表
}

interface IRelationCondition {
  rule: string;                  // 规则：'eq', 'neq', 'gt', 'lt', 'ctn', etc.
  valueType: 'value' | 'form' | 'empty';  // 值类型
  value: string | IFormAttr;     // 值或字段引用
}

interface IRelationAction {
  id: string;                    // 操作 ID
  actionCode: string;            // 操作类型：'visible', 'disabled', 'value', etc.
  value: any;                    // 操作值
  attrCode: string;              // 目标字段代码
  formCode?: string;             // 目标表单代码（可选）
}
```


## 基础联动


### 显示/隐藏字段


当某个字段的值等于特定值时，显示或隐藏另一个字段：


```
const formConfig = {
  // ... 其他配置
  relations: [
    {
      formCode: 'my-form',
      attrCode: 'type',
      actionCodes: ['change'],
      groups: [
        {
          id: 'group-1',
          conditions: [
            {
              rule: 'eq',
              valueType: 'value',
              value: 'advanced',
            },
          ],
          relations: [
            {
              id: 'rel-1',
              actionCode: 'visible',
              value: true,
              attrCode: 'advancedField',
            },
          ],
        },
      ],
    },
  ],
};
```


### 启用/禁用字段


根据条件启用或禁用字段：


```
relations: [
  {
    formCode: 'my-form',
    attrCode: 'status',
    actionCodes: ['change'],
    groups: [
      {
        id: 'group-1',
        conditions: [
          {
            rule: 'eq',
            valueType: 'value',
            value: 'approved',
          },
        ],
        relations: [
          {
            id: 'rel-1',
            actionCode: 'disabled',
            value: true,
            attrCode: 'editField',
          },
        ],
      },
    ],
  },
]
```


### 字段赋值


根据条件自动设置字段值：


```
relations: [
  {
    formCode: 'my-form',
    attrCode: 'category',
    actionCodes: ['change'],
    groups: [
      {
        id: 'group-1',
        conditions: [
          {
            rule: 'eq',
            valueType: 'value',
            value: 'electronics',
          },
        ],
        relations: [
          {
            id: 'rel-1',
            actionCode: 'value',
            value: 'default-code',
            attrCode: 'productCode',
          },
        ],
      },
    ],
  },
]
```


## 条件规则


### 支持的规则类型


- `eq` - 等于
- `neq` - 不等于
- `gt` - 大于
- `gte` - 大于等于
- `lt` - 小于
- `lte` - 小于等于
- `ctn` - 包含
- `nctn` - 不包含
- `vln` - 值在列表中
- `nvln` - 值不在列表中
- `start` - 以…开始
- `end` - 以…结束


### 值类型


- `value` - 固定值
- `form` - 引用其他字段值
- `empty` - 空值判断


### 条件示例


```
// 等于固定值
{
  rule: 'eq',
  valueType: 'value',
  value: 'type1',
}

// 等于其他字段值
{
  rule: 'eq',
  valueType: 'form',
  value: {
    formCode: 'my-form',
    attrCode: 'otherField',
  },
}

// 包含某个值
{
  rule: 'ctn',
  valueType: 'value',
  value: 'keyword',
}

// 值在列表中
{
  rule: 'vln',
  valueType: 'value',
  value: ['value1', 'value2', 'value3'],
}

// 为空
{
  rule: 'eq',
  valueType: 'empty',
  value: '',
}
```


## 多条件组合


### AND 条件（多个条件都满足）


```
groups: [
  {
    id: 'group-1',
    conditions: [
      {
        rule: 'eq',
        valueType: 'value',
        value: 'type1',
      },
      {
        rule: 'neq',
        valueType: 'form',
        value: {
          formCode: 'my-form',
          attrCode: 'status',
        },
      },
    ],
    relations: [
      // 所有条件都满足时执行的操作
    ],
  },
]
```


### OR 条件（多个组，满足任意一个）


```
relations: [
  {
    formCode: 'my-form',
    attrCode: 'type',
    actionCodes: ['change'],
    groups: [
      {
        id: 'group-1',
        conditions: [
          { rule: 'eq', valueType: 'value', value: 'type1' },
        ],
        relations: [
          { actionCode: 'visible', value: true, attrCode: 'field1' },
        ],
      },
      {
        id: 'group-2',
        conditions: [
          { rule: 'eq', valueType: 'value', value: 'type2' },
        ],
        relations: [
          { actionCode: 'visible', value: true, attrCode: 'field2' },
        ],
      },
    ],
  },
]
```


## 公式计算


### 公式联动


支持使用公式计算字段值：


```
relations: [
  {
    formCode: 'my-form',
    attrCode: 'quantity',
    actionCodes: ['change'],
    groups: [
      {
        id: 'group-1',
        conditions: [],
        relations: [
          {
            id: 'rel-1',
            actionCode: 'formula',
            value: {
              rule: '${my-form.price} * ${my-form.quantity}',
            },
            attrCode: 'total',
          },
        ],
      },
    ],
  },
]
```


### 公式语法


公式支持以下语法：


- `${formCode.attrCode}` - 引用字段值
- 数学运算符：`+`, `-`, `*`, `/`, `%`
- 函数：`SUM()`, `AVG()`, `MAX()`, `MIN()`, `UUID()`, `UUIDEIGHT()`


### 公式示例


```
// 简单计算
'${my-form.price} * ${my-form.quantity}'

// 条件计算
'${my-form.price} > 100 ? ${my-form.price} * 0.9 : ${my-form.price}'

// 函数调用
'SUM(${my-form.item1}, ${my-form.item2}, ${my-form.item3})'

// UUID 生成
'UUID()'
```


## 联动事件


### 触发事件类型


- `change` - 值改变时触发
- `blur` - 失去焦点时触发
- `focus` - 获得焦点时触发
- `search` - 搜索时触发


### 事件配置


```
{
  formCode: 'my-form',
  attrCode: 'field1',
  actionCodes: ['change', 'blur'],  // 多个事件
  groups: [
    // ...
  ],
}
```


## 完整示例


### 示例 1: 级联选择


```
const formConfig = {
  // ... 其他配置
  relations: [
    {
      formCode: 'my-form',
      attrCode: 'province',
      actionCodes: ['change'],
      groups: [
        {
          id: 'group-1',
          conditions: [],
          relations: [
            {
              id: 'rel-1',
              actionCode: 'options',
              value: {
                // 根据省份动态加载城市选项
                api: '/api/cities',
                params: {
                  province: '${my-form.province}',
                },
              },
              attrCode: 'city',
            },
          ],
        },
      ],
    },
  ],
};
```


### 示例 2: 复杂联动


```
const formConfig = {
  relations: [
    {
      formCode: 'order-form',
      attrCode: 'orderType',
      actionCodes: ['change'],
      groups: [
        {
          id: 'group-1',
          conditions: [
            {
              rule: 'eq',
              valueType: 'value',
              value: 'express',
            },
          ],
          relations: [
            {
              id: 'rel-1',
              actionCode: 'visible',
              value: true,
              attrCode: 'expressFee',
            },
            {
              id: 'rel-2',
              actionCode: 'visible',
              value: false,
              attrCode: 'normalFee',
            },
            {
              id: 'rel-3',
              actionCode: 'value',
              value: 20,
              attrCode: 'expressFee',
            },
          ],
        },
        {
          id: 'group-2',
          conditions: [
            {
              rule: 'eq',
              valueType: 'value',
              value: 'normal',
            },
          ],
          relations: [
            {
              id: 'rel-4',
              actionCode: 'visible',
              value: false,
              attrCode: 'expressFee',
            },
            {
              id: 'rel-5',
              actionCode: 'visible',
              value: true,
              attrCode: 'normalFee',
            },
          ],
        },
      ],
    },
    {
      formCode: 'order-form',
      attrCode: 'quantity',
      actionCodes: ['change', 'blur'],
      groups: [
        {
          id: 'group-3',
          conditions: [],
          relations: [
            {
              id: 'rel-6',
              actionCode: 'formula',
              value: {
                rule: '${order-form.price} * ${order-form.quantity}',
              },
              attrCode: 'total',
            },
          ],
        },
      ],
    },
  ],
};
```


### 示例 3: 表格内联动


```
// 表格行内的字段联动
relations: [
  {
    formCode: 'order-form',
    attrCode: 'items[].productId',
    actionCodes: ['change'],
    groups: [
      {
        id: 'group-1',
        conditions: [],
        relations: [
          {
            id: 'rel-1',
            actionCode: 'value',
            value: {
              // 根据产品 ID 自动填充价格
              api: '/api/products/${order-form.items[].productId}/price',
            },
            attrCode: 'items[].price',
          },
        ],
      },
    ],
  },
]
```

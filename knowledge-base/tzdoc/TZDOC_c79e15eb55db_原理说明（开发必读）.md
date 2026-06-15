---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1foq4mgrsckij
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1foq4mgrsckij
url_hash: c79e15eb55db
document_key: TZDOC_c79e15eb55db
doc_id: tzdoc_v2-1foq4mgrsckij
title: 原理说明（开发必读）
md_hash: fbe3b3b83484c9ae
version: 1747364108
image_count: 0
crawled_at: 2026-06-11 16:08:14
---

# 原理说明（开发必读）

- [组件运行过程](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1foq4mgrsckij#g69ryk)
- [翻译与查询](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1foq4mgrsckij#2q2nd8)
- [接口规范约束](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1foq4mgrsckij#ag1z9e)


## 组件运行过程


```
1、API 查询组件在使用时，用户输入关键字进行搜索，组件会用输入值作为“模糊查询字段”的值调用连接器，连接器需要实现模糊匹配搜索，返回命中的数据，用户选择后，程序将拿着“实际存储字段”落库

2、API 查询组件在详情页渲染、列表渲染、导出时会自动根据“实际存储字段”的值做为“精确翻译字段”的值去调连接器，获取”显示字段“，并展示或导出到 excel；

3、API 查询组件在导入时，会用导入值作为“模糊查询字段”的值，调用连接器获取“实际存储字段”，然后落库；
```


## 翻译与查询


```
1、根据用户输入值作为“模糊查询字段”，模糊匹配搜索数据的过程，称为“查询”。

2、根据“实际存储字段”查找“显示字段“的过程，称为“翻译”或“正向翻译”。比如列表渲染、导出数据，需要根据存储值翻译出显示值

3、根据用户输入值做为“模糊查询字段”的值查找“实际存储字段”的过程，称为“反向翻译”。比如导入数据，需要根据文本反向翻译出存储值用于落库。
```


## 接口规范约束


```
1、根据“实际存储字段”能够直接检索到唯一的“显示字段”，返回为空或者多个时，则系统报错；

2、根据“显示字段”能够检索到唯一的“实际存储字段”，如果返回为多个并且在翻译场景下，则系统报错；

3、真对有的字典项失效的场景，需要业务方自行控制，保证历史的数据也能正常翻译；
```

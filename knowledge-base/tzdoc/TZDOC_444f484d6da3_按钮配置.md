---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fkgv47nsolhq
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fkgv47nsolhq
url_hash: 444f484d6da3
document_key: TZDOC_444f484d6da3
doc_id: tzdoc_v2-1fkgv47nsolhq
title: 按钮配置
md_hash: 24d1352b436c251f
version: 1774237466
image_count: 0
crawled_at: 2026-06-11 16:09:57
---

# 按钮配置

#### 目标


```
本文档用于说明高级列表中如何配置按钮，让用户理解配置的按钮怎么和列表的数据做关联
```


###### 工具栏/行按钮


 支持的按钮:


- a. 跳转类：创建、编辑、跳转
按钮事件可配置的元素: 跳转的菜单(可调整跳转链接和跳转参数)、跳转方式(页签、新窗口、弹窗、抽屉)、打开宽度、打开高度、保存后是否关闭（开关）、是否不分解metaConfig.comParams到url参数中（开关）
- b. 数据操作类: 保存、删除
按钮事件可配置的元素: 操作的key(列表数据id的key,默认为id)、classId的key(填写字段配置中classId的字段列名)
- c. 导入类
按钮事件可配置元素: 导入地址、模板地址、classId。详细说明参考文档 ([https://docs.cvte.com/docs/tzdoc_v2//1202](https://docs.cvte.com/docs/tzdoc_v2//1202))
- d.自定义
 按钮前置事件、按钮事件、按钮后置事件都可自定义，自定义方式可写在线JS代码


说明: 所有按钮都支持 前置事件、按钮事件、后置事件，都可对按钮的各种行为进行自定义

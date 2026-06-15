---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1gcjdccmbmovb
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1gcjdccmbmovb
url_hash: bed7af0ccf94
document_key: TZDOC_bed7af0ccf94
doc_id: tzdoc_v2-1gcjdccmbmovb
title: 属性联动列表中的属性名称丢失
md_hash: 5258ca3f4c2b5f83
version: 1743576457
image_count: 0
crawled_at: 2026-06-11 16:15:36
---

# 属性联动列表中的属性名称丢失

##### 现象


```
1、属性联动配置列表，属性名称不显示，只显示一串ID
2、属性联动实际不生效
```


##### 问题原因


```
以下步骤会导致异常
1、sit新建了字段A+配置了属性联动
2、执行运维同步(sit->生产)
3、sit删除字段A，重新建了字段A
4、执行运维同步(sit->生产)
```


###### 避免办法


```
1、同字段不要多次新建，可以直接从属性树拖出来
2、要改属性类型一定要新建时，按文档操作  https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fkstntur66hm
```

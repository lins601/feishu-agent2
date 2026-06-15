---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u
url_hash: de9559fa9281
document_key: TZSERVER_de9559fa9281
doc_id: tzserver_v2-1g2d8s8ct2n8u
title: 后端
md_hash: 030bbd6bf5cd4105
version: 1735875228
image_count: 0
crawled_at: 2026-06-11 16:20:07
---

# 后端

- [增量升级(v2.8.0->v2.8.1)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#dl9plz)
- [程序发布前执行项](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#fu6rv1)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#4pmpnk)
- [1.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#7r228u)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#ecsjod)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#7ve7nx)
- [业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#70jisd)
- [配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#vfq9m)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#ezz637)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g2d8s8ct2n8u#df6lv0)


## 增量升级(v2.8.0->v2.8.1)


### 程序发布前执行项


#### 1、Apollo配置


无

###### 1.2 CSB-LEGOX


无

```
-- pg
ALTER TABLE dictionary_item ADD item_config text NULL;
COMMENT ON COLUMN dictionary_item.item_config IS '字典配置:标签颜色';

-- Oracle
ALTER TABLE dictionary_item ADD item_config varchar2(4000) NULL;
COMMENT ON COLUMN dictionary_item.item_config IS '字典配置:标签颜色';
```


#### 2、Nacos配置


无

#### 3、数据库脚本


独立部署程序自动执行，不需要手动执行，只有多租户需要执行：研发、行政、众远智慧

```
-- 更新表单设计是否可复制为是
update obj_general_config g set attr3 = replace(attr3,'"isCopiable":"0"','"isCopiable":"1"')
where g.relate_id in (
    select l.id from obj_layout_config l where l.biz_id in (
        select f.id from obj_form_design f where f.biz_id in (
            select c.id from obj_class c where c.is_deleted = '0' and c.form_type IN( 'FORM_NORMAL','FORM_WORKFLOW')
        ) and f.biz_type = 'LCP_FORM_DESIGN' )
) and g.attr3 like '%"isCopiable":"0"%'
;

ALTER TABLE obj_attribute_group ADD is_virtual varchar(4) NULL;
COMMENT ON COLUMN obj_attribute_group.is_virtual IS '是否虚拟明细表';

ALTER TABLE state_machine_task_log ALTER COLUMN op_type TYPE varchar(1000) USING op_type::varchar;

ALTER TABLE state_machine_task ADD config varchar(2000) NULL;

```


##### 业务库


无

##### 配置库


### 程序发布


| 应用 | 分支 |
| --- | --- |
| lcp-app | 2.8.1-SNAPSHOT |
| lcp-data | 2.8.1-SNAPSHOT |
| lcp-hub | 2.8.1-SNAPSHOT |
| csb-data-dictionary | 2.0.9.4.1-SNAPSHOT 或 3.0.1-SNAPSHOT |


### 应用运维同步


```
   无
```

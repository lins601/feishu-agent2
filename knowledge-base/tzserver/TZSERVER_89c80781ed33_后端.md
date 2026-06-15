---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea
url_hash: 89c80781ed33
document_key: TZSERVER_89c80781ed33
doc_id: tzserver_v2-1g155e0nragea
title: 后端
md_hash: 19753655023dd22f
version: 1730879644
image_count: 0
crawled_at: 2026-06-11 16:20:05
---

# 后端

- [增量升级(v2.7.1->v2.8.0)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#8jt0kp)
- [程序发布前执行项](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#77xfup)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#431b2h)
- [1.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#gdl8za)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#dk3zn7)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#ecxqv8)
- [业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#fvxvwr)
- [配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#bltv21)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#172p45)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1g155e0nragea#4k0d58)


## 增量升级(v2.7.1->v2.8.0)


### 程序发布前执行项


#### 1、Apollo配置


无

###### 1.2 CSB-LEGOX


无

#### 2、Nacos配置


无

#### 3、数据库脚本


##### 业务库


无

##### 配置库


独立部署程序自动执行，不需要手动执行，只有多租户需要执行：研发、行政

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

```


### 程序发布


| 应用 | 分支 |
| --- | --- |
| lcp-app | 2.8.0-SNAPSHOT |
| lcp-data | 2.8.0-SNAPSHOT |
| lcp-hub | 2.8.0-SNAPSHOT |
| lcp-legox | 更新csb-sys-base 2.0.9.6.9-SNAPSHOT或者 3.0.1-SNAPSHOT |


### 应用运维同步


| 应用 | 表单 | 产品 | 备注 |
| --- | --- | --- | --- |
| 平台配置管理 | 站点管理->门户管理 | 系统中心 | 仅执行运维同步，具体数据行无需同步 |

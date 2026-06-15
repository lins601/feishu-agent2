---
source: MinDoc
project_name: TZC_QA
source_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gl2491s5aj54
normalized_url: https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gl2491s5aj54
url_hash: 9cf2e813426d
document_key: TZC_QA_9cf2e813426d
doc_id: tzc_qa-1gl2491s5aj54
title: 表单配置专题
md_hash: 88dcb72f0ece7fd3
version: 1753101376
image_count: 0
crawled_at: 2026-06-11 15:58:29
---

# 表单配置专题

- [表单迁移到别的应用菜单下](https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gl2491s5aj54#5gun64)
- [Q: 已经建的表单想调整挂载的应用和菜单路径](https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gl2491s5aj54#ent2sw)
- [A: 复制在设计器上有一个控制开关，必须开启下可以](https://docs.cvte.com/docs/tzc_qa/tzc_qa-1gl2491s5aj54#64w279)


# 表单迁移到别的应用菜单下


已经建的表单想调整挂载的应用和菜单路径
相关问题提出人登记


| 提出人 | 产品线 |
| --- | --- |
| 张家源 | 人力 |
|  |  |


---


#### Q: 已经建的表单想调整挂载的应用和菜单路径


#### A: 复制在设计器上有一个控制开关，必须开启下可以


1.更新obj_class表的app_id
2.把菜单挪到对应的应用【系统管理】-【页面资源】（页面资源+菜单 sys_page sys_menu）
3.更新菜单参数里面的appId
4.更新sys_page中的system_id和src_app为app_id，更新sys_menu中的system_id为app_id
5.更新表sys_view_datasource的app_id (关联关系 sys_database_id = class_id)
6.实体业务表的app_id

---

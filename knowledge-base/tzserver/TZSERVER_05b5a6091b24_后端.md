---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1ff4fbj1q714m
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1ff4fbj1q714m
url_hash: 05b5a6091b24
document_key: TZSERVER_05b5a6091b24
doc_id: tzserver_v2-1ff4fbj1q714m
title: 后端
md_hash: 4ecb4c022b9ce16b
version: 1710406594
image_count: 0
crawled_at: 2026-06-11 16:19:37
---

# 后端

### 增量升级(v2.3.0->v2.3.1)


#### 程序发布前执行项


##### 1、Apollo配置


##### 2、Nacos配置


##### 3、数据库脚本


###### 业务库(lcp-data)


```
-- 主表增加索引：变更、MBOM变更
create index idx_changebaseclass_no on changebaseclass(serial_number);
create index idx_change_order_class_no on change_order_class(serial_number);

-- 主表增加索引：物件、文档、替代
create index idx_itembaseclass_no on itembaseclass(serial_number);
create index idx_documentsclass_no on documentsclass(serial_number);
create index idx_baselinebaseclass_no on baselinebaseclass(serial_number);

-- 主表增加索引：产品型号、产品全称 -- 仅ZYZH租户才有这两个主表
create index idx_product_model_no on product_model(serial_number);
create index idx_product_name_no on product_name(serial_number);
```


###### 配置库(lcp-app)


```

--天舟云默认租户flyway会自动执行，不要手动
--业务域独立部署flyway会自动执行,不要手动
CREATE INDEX obj_general_config_relate_id_idx ON obj_general_config USING btree (relate_id);
CREATE INDEX obj_attr_tribe_rel_relate_id_idx ON clcp.obj_attr_tribe_rel (relate_id);
CREATE INDEX obj_permission_condition_policy_id_idx ON obj_permission_condition (policy_id);
CREATE INDEX obj_permission_object_policy_id_idx ON obj_permission_object (policy_id);
CREATE INDEX obj_permission_subject_policy_id_idx ON obj_permission_subject (policy_id);
create index idx_obj_pref_value_cls_owner_conf on obj_preference_value(class_id, owner_id, conf_id);
```


###### CSB库(csb-legox)


```

-- resourceName=lcp-data-object exposeName=DataObject 改成 resourceName=tz-render exposeName=DataObject
UPDATE  SYS_PAGE  t SET ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'resourceName=lcp-data-object','resourceName=tz-render')
WHERE t.ATTRIBUTE3 = 'modules/FormTemplate/Resource'
AND t.ATTRIBUTE6 LIKE '%resourceName=lcp-data-object%' AND t.ATTRIBUTE6 LIKE '%exposeName=DataObject%'
;

-- resourceName=cir-state-flow-manager exposeName=FlowDesign 改成 resourceName=tz-design exposeName=cirStateFlowManager.FlowDesign
UPDATE  SYS_PAGE  t SET ATTRIBUTE6 = REPLACE(REPLACE(t.ATTRIBUTE6, 'resourceName=cir-state-flow-manager','resourceName=tz-design'), 'exposeName=FlowDesign','exposeName=cirStateFlowManager.FlowDesign')
WHERE t.ATTRIBUTE3 = 'modules/FormTemplate/Resource'
AND t.ATTRIBUTE6 LIKE '%resourceName=cir-state-flow-manager%' AND t.ATTRIBUTE6 LIKE '%exposeName=FlowDesign%'
;

-- resourceName=cir-state-flow-manager exposeName=TianzhouFlowManager 改成 resourceName=tz-design exposeName=cirStateFlowManager.TianzhouFlowManager
UPDATE  SYS_PAGE  t SET ATTRIBUTE6 = REPLACE(REPLACE(t.ATTRIBUTE6, 'resourceName=cir-state-flow-manager','resourceName=tz-design'), 'exposeName=TianzhouFlowManager','exposeName=cirStateFlowManager.TianzhouFlowManager')
WHERE t.ATTRIBUTE3 = 'modules/FormTemplate/Resource'
AND t.ATTRIBUTE6 LIKE '%resourceName=cir-state-flow-manager%' AND t.ATTRIBUTE6 LIKE '%exposeName=TianzhouFlowManager%'
;

-- resourceName=cir-lcp-sdk exposeName=LCPDetailTemplate 改成 resourceName=tz-render exposeName=LCPDetailTemplate
UPDATE  SYS_PAGE  t SET ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'resourceName=cir-lcp-sdk','resourceName=tz-render')
--,ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'exposeName=LCPDetailTemplate','exposeName=LCPDetailTemplate')
WHERE t.ATTRIBUTE3 = 'modules/FormTemplate/Resource'
AND t.ATTRIBUTE6 LIKE '%resourceName=cir-lcp-sdk%' AND t.ATTRIBUTE6 LIKE '%exposeName=LCPDetailTemplate%'
;

-- resourceName=cir-lcp-sdk exposeName=LCPPageTemplate 改成 resourceName=tz-render exposeName=LCPPageTemplate
UPDATE  SYS_PAGE  t SET ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'resourceName=cir-lcp-sdk','resourceName=tz-render')
--,ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'exposeName=LCPPageTemplate','exposeName=LCPPageTemplate')
WHERE t.ATTRIBUTE3 = 'modules/FormTemplate/Resource'
AND t.ATTRIBUTE6 LIKE '%resourceName=cir-lcp-sdk%' AND t.ATTRIBUTE6 LIKE '%exposeName=LCPPageTemplate%'
;

-- resourceName=cir-lcp-sdk exposeName=LCPResourceTemplate 改成 resourceName=tz-render exposeName=LCPResourceTemplate
UPDATE  SYS_PAGE  t SET ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'resourceName=cir-lcp-sdk','resourceName=tz-render')
--,ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'exposeName=LCPResourceTemplate','exposeName=LCPResourceTemplate')
WHERE t.ATTRIBUTE3 = 'modules/FormTemplate/Resource'
AND t.ATTRIBUTE6 LIKE '%resourceName=cir-lcp-sdk%' AND t.ATTRIBUTE6 LIKE '%exposeName=LCPResourceTemplate%'
;

-- resourceName=cir-lcp-sdk exposeName=LCPListTemplate 改成 resourceName=tz-render exposeName=LCPListTemplate
UPDATE  SYS_PAGE  t SET ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'resourceName=cir-lcp-sdk','resourceName=tz-render')
--,ATTRIBUTE6 = REPLACE(t.ATTRIBUTE6, 'exposeName=LCPListTemplate','exposeName=LCPListTemplate')
WHERE t.ATTRIBUTE3 = 'modules/FormTemplate/Resource'
AND t.ATTRIBUTE6 LIKE '%resourceName=cir-lcp-sdk%' AND t.ATTRIBUTE6 LIKE '%exposeName=LCPListTemplate%'
;
```


##### 4、定时任务


```
执行器名称: tzcc
任务名称: 数据字典缓存更新
运行模式: dictionnaryCacheJob
cron: 0 0 2 * * ?

```


#### 程序发布后执行项

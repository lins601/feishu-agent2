---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back
url_hash: 0c2c7383813b
document_key: TZSERVER_0c2c7383813b
doc_id: tzc_upgrade_3_6_0_back
title: 后端3.6
md_hash: 4569ee934d7a8387
version: 1774247004
image_count: 0
crawled_at: 2026-06-11 16:20:42
---

# 后端3.6

- [增量升级(v3.5.1->v3.6.0)](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#fcsi8r)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#135wd9)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#8bva89)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#6uuw0w)
- [3.1 配置库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#ee6bs1)
- [PostgreSQL](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#fjnrog)
- [3.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#8xdnde)
- [pg库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#g19k3r)
- [3.2 业务库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#f9sdtw)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#8byybx)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_6_0_back#8rtcmx)


# 增量升级(v3.5.1->v3.6.0)


>


 程序发布前执行项


# 1、Apollo配置


无

# 2、Nacos配置


无

# 3、数据库脚本


## 3.1 配置库


##### PostgreSQL


公有租户手动执行，独立部署无需执行

```
CREATE INDEX state_machine_task_biz_type_idx ON clcp.state_machine_task USING btree (biz_type);
CREATE INDEX state_machine_task_parent_id_idx ON clcp.state_machine_task USING btree (parent_id);
CREATE INDEX smt_process_id_idx ON clcp.state_machine_task USING btree (process_id);
CREATE INDEX state_machine_task_log_idx2 ON clcp.state_machine_task_log USING btree (process_id);
CREATE INDEX state_machine_task_log_op_user_idx ON clcp.state_machine_task_log USING btree (op_user);
CREATE INDEX smt_error_process_id_idx ON clcp.state_machine_task_error (process_id);
CREATE INDEX smt_error_task_id_idx ON clcp.state_machine_task_error USING btree (task_id);
CREATE INDEX state_machine_todo_target_handler_user_idx ON clcp.state_machine_todo_target USING btree (handler_user);
CREATE INDEX state_machine_todo_target_todo_id_idx ON clcp.state_machine_todo_target USING btree (todo_id);
```


## 3.2 CSB-LEGOX


### pg库


无

## 3.2 业务库


无

# 程序发布


| 应用 | 分支 | 备注 |
| --- | --- | --- |
| lcp-app | 3.6.0-SNAPSHOT |  |
| lcp-data | 3.6.0-SNAPSHOT |  |
| lcp-hub | 3.6.0-SNAPSHOT |  |
| lcp-legox | 3.0.4-SNAPSHOT | csb-sys-base(3.0.4-SNAPSHOT/2.0.9.8-SNAPSHOT) 重新构建即可 |


# 应用运维同步


```
   无
```

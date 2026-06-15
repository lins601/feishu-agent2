---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i
url_hash: 9f91b35e1a04
document_key: TZSERVER_9f91b35e1a04
doc_id: tzserver_v2-1guaq0a6a6e6i
title: 后端
md_hash: 33f7e04b7e774e6f
version: 1774247045
image_count: 0
crawled_at: 2026-06-11 16:20:39
---

# 后端

- [增量升级(v3.5.0->v3.5.1)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#7osgsh)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#765y2y)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#1zkj18)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#cda8ox)
- [3.1 配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#aqrjgp)
- [PostgreSQL](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#es6vdl)
- [3.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#bmxxw1)
- [pg库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#1ozati)
- [3.2 业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#kcz2i)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#e37giz)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1guaq0a6a6e6i#bx8tco)


# 增量升级(v3.5.0->v3.5.1)


>


 程序发布前执行项


# 1、Apollo配置


无

# 2、Nacos配置


无

# 3、数据库脚本


## 3.1 配置库


公有租户手动执行，独立部署无需执行

##### PostgreSQL


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
| lcp-app | 3.5.1-SNAPSHOT |  |
| lcp-data | 3.5.1-SNAPSHOT |  |
| lcp-hub | 3.5.1-SNAPSHOT |  |
| lcp-legox | 3.0.4-SNAPSHOT | csb-sys-base(3.0.4-SNAPSHOT/2.0.9.8-SNAPSHOT) 重新构建即可 |


# 应用运维同步


```
   无
```

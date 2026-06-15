---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir
url_hash: 8af98ee4402a
document_key: TZSERVER_8af98ee4402a
doc_id: tzserver_v2-1h3aio1jot4ir
title: 后端
md_hash: 0c98fef7909ee51e
version: 1774247019
image_count: 0
crawled_at: 2026-06-11 16:20:44
---

# 后端

- [增量升级(v3.6.0->v3.6.1)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#8bgar0)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#6dbd0c)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#bjgu8e)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#3c975w)
- [3.1 配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#99iq8n)
- [PostgreSQL](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#8bfv6i)
- [3.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#2ppx4s)
- [oracle库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#ff9dce)
- [3.2 业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#amrl2k)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#acubr0)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h3aio1jot4ir#7f5bf)


# 增量升级(v3.6.0->v3.6.1)


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
ALTER TABLE state_machine_template ADD process_mode varchar(100) NULL;
```


## 3.2 CSB-LEGOX


### oracle库


```

ALTER TABLE CSB.SYS_PAGE MODIFY ATTRIBUTE6 VARCHAR2(1024);
```


## 3.2 业务库


无

# 程序发布


| 应用 | 分支 | 备注 |
| --- | --- | --- |
| lcp-app | 3.6.1-SNAPSHOT |  |
| lcp-data | 3.6.1-SNAPSHOT |  |
| lcp-hub | 3.6.1-SNAPSHOT |  |
| csb-legox | csb-sys-base 2.0.9.9-SNAPSHOT/3.0.5-SNAPSHOT |  |


# 应用运维同步


```
   无
```

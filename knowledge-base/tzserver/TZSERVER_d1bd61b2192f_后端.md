---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q
url_hash: d1bd61b2192f
document_key: TZSERVER_d1bd61b2192f
doc_id: tzserver_v2-1h84cgs1rto7q
title: 后端
md_hash: c5785f1eb3a6044b
version: 1774573241
image_count: 0
crawled_at: 2026-06-11 16:20:46
---

# 后端

- [增量升级(v3.6.1->v3.7.0)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#azkf0b)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#ac0kye)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#32m7h3)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#1q4vr8)
- [3.1 配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#dur6cu)
- [PostgreSQL](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#ayzjka)
- [3.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#ezlz2l)
- [oracle库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#8sx538)
- [3.2 业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#6aofoh)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#21rv2f)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1h84cgs1rto7q#emr5fr)


# 增量升级(v3.6.1->v3.7.0)


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

## 3.2 CSB-LEGOX


### oracle库


## 3.2 业务库


无

# 程序发布


| 应用 | 分支 | 备注 |
| --- | --- | --- |
| lcp-app | 3.7.0-SNAPSHOT |  |
| lcp-data | 3.7.0-SNAPSHOT |  |
| lcp-hub | 3.7.0-SNAPSHOT |  |
| csb-legox | 如需支持多语言，需合并csb-legox中3.5.0-SNAPSHOT分支中多语言的代码 |  |


# 应用运维同步


```
   无
```

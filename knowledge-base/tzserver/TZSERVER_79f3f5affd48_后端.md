---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back
url_hash: 79f3f5affd48
document_key: TZSERVER_79f3f5affd48
doc_id: tzc_upgrade_3_2_0_back
title: 后端
md_hash: b9268b56802918c7
version: 1753261733
image_count: 0
crawled_at: 2026-06-11 16:20:26
---

# 后端

- [增量升级(v3.1.0->v3.2.0)](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#4bqcst)
- [程序发布前执行项](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#cr19ke)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#9sfs5c)
- [1.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#bbys72)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#79ebhj)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#844qi9)
- [业务库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#ay22mu)
- [配置库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#25mg4k)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#954z4j)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_2_0_back#3badnq)


## 增量升级(v3.1.0->v3.2.0)


### 程序发布前执行项


#### 1、Apollo配置


###### 1.2 CSB-LEGOX


#### 2、Nacos配置


无

#### 3、数据库脚本


##### 业务库


无

##### 配置库


独立部署程序自动执行，不需要手动执行，只有多租户需要执行：研发、行政、众远智慧

### 程序发布


| 应用 | 分支 | 备注 |
| --- | --- | --- |
| lcp-app | 3.2.0-SNAPSHOT |  |
| lcp-data | 3.2.0-SNAPSHOT |  |
| lcp-hub | 3.2.0-SNAPSHOT |  |
| lcp-legox | 3.0.3-SNAPSHOT | csb-data-dictionary(3.0.3-SNAPSHOT/2.0.9.5-SNAPSHOT)csb-sys-base(3.0.3-SNAPSHOT/2.0.9.7-SNAPSHOT) |


### 应用运维同步


```
   无
```

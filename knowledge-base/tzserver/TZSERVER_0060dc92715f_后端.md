---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back
url_hash: 0060dc92715f
document_key: TZSERVER_0060dc92715f
doc_id: tzc_upgrade_3_3_0_back
title: 后端
md_hash: 4f1f138f2adc8d74
version: 1756197267
image_count: 0
crawled_at: 2026-06-11 16:20:31
---

# 后端

- [增量升级(v3.2.0->v3.3.0)](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#bui6at)
- [程序发布前执行项](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#7a3reg)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#6k93yt)
- [1.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#6177vs)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#cw6fp4)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#eoylo0)
- [业务库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#19kxa5)
- [配置库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#3ixaq4)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#3pz30a)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_3_0_back#5ybxei)


## 增量升级(v3.2.0->v3.3.0)


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
| lcp-app | 3.3.0-SNAPSHOT |  |
| lcp-data | 3.3.0-SNAPSHOT |  |
| lcp-hub | 3.3.0-SNAPSHOT |  |
| lcp-legox | 3.0.3-SNAPSHOT | csb-sys-base(3.0.3-SNAPSHOT/2.0.9.7-SNAPSHOT) |


### 应用运维同步


```
   无
```

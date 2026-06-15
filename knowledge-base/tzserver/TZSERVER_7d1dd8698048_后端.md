---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa
url_hash: 7d1dd8698048
document_key: TZSERVER_7d1dd8698048
doc_id: tzserver_v2-1gphvjnpa8joa
title: 后端
md_hash: c37b9fa7ba3f1044
version: 1758162692
image_count: 0
crawled_at: 2026-06-11 16:20:32
---

# 后端

- [增量升级(v3.3.0->v3.3.1)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#8bgzq8)
- [程序发布前执行项](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#3i9vhd)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#ekcljx)
- [1.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#6qax46)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#1ibanp)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#ed388d)
- [业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#diubn5)
- [配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#8eu47s)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#cavdg5)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gphvjnpa8joa#3xpsjd)


## 增量升级(v3.3.0->v3.3.1)


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
| lcp-app | 3.3.1-SNAPSHOT |  |
| lcp-data | 3.3.1-SNAPSHOT |  |
| lcp-hub | 3.3.1-SNAPSHOT |  |
| lcp-legox | 3.0.3-SNAPSHOT | csb-sys-base(3.0.3-SNAPSHOT/2.0.9.7-SNAPSHOT) |


### 应用运维同步


```
   无
```

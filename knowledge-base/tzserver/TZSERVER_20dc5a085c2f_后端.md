---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g
url_hash: 20dc5a085c2f
document_key: TZSERVER_20dc5a085c2f
doc_id: tzserver_v2-1hc77dfrhgl7g
title: 后端
md_hash: 7512d50b10c5aef5
version: 1779959028
image_count: 0
crawled_at: 2026-06-11 16:20:48
---

# 后端

- [增量升级(v3.7.0->v3.7.1)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#1mj1jx)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#c81bx8)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#cjrm92)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#ganhun)
- [3.1 配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#b30rih)
- [PostgreSQL](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#ctjzaq)
- [3.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#mkhf4)
- [oracle库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#b2r5at)
- [3.2 业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#5eowi6)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#eqd5lz)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1hc77dfrhgl7g#crxce6)


# 增量升级(v3.7.0->v3.7.1)


>


 程序发布前执行项


# 1、Apollo配置


无

# 2、Nacos配置


无

# 3、数据库脚本


## 3.1 配置库


##### PostgreSQL


**CPLM、行政需手动执行**(公有租户手动执行，独立部署无需执行)

```
-- 为节点表增加 front 字段
ALTER TABLE state_machine_node ADD COLUMN layout TEXT;
COMMENT ON COLUMN state_machine_node.layout IS '前端位置信息';

-- 为连线配置表增加 front 字段
ALTER TABLE state_machine_trans_conf ADD COLUMN layout TEXT;
COMMENT ON COLUMN state_machine_trans_conf.layout IS '前端位置信息';

-- 为连线分支表增加 front 字段
ALTER TABLE state_machine_trans_branch ADD COLUMN layout TEXT;
COMMENT ON COLUMN state_machine_trans_branch.layout IS '前端位置信息';

```


## 3.2 CSB-LEGOX


### oracle库


## 3.2 业务库


无

# 程序发布


| 应用 | 分支 | 备注 |
| --- | --- | --- |
| lcp-app | 3.7.1-SNAPSHOT |  |
| lcp-data | 3.7.1-SNAPSHOT |  |
| lcp-hub | 3.7.1-SNAPSHOT |  |
| csb-legox | 3.0.5-SNAPSHOT | csb-sys-base 2.0.9.9-SNAPSHOT/3.0.5-SNAPSHOT；csb-data-dictionary 2.0.9.5-SNAPSHOT/3.0.3-SNAPSHOT |


# 应用运维同步


```
   无
```

---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back
url_hash: ecc73da16e87
document_key: TZSERVER_ecc73da16e87
doc_id: tzc_upgrade_3_4_0_back
title: 后端3.4
md_hash: a3dabf9e0f02a1fb
version: 1759225056
image_count: 0
crawled_at: 2026-06-11 16:20:34
---

# 后端3.4

- [增量升级(v3.3.0->v3.4.0)](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#9z1329)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#cspefm)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#8sgj2z)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#1o22eo)
- [3.1 配置库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#6issu8)
- [3.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#82t0hp)
- [pg库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#cjjkos)
- [oracle库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#2pjaig)
- [3.2 业务库](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#5g3xv0)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#8f8ggo)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzc_upgrade_3_4_0_back#6buyog)


# 增量升级(v3.3.0->v3.4.0)


>


 程序发布前执行项


# 1、Apollo配置


无

# 2、Nacos配置


无

# 3、数据库脚本


## 3.1 配置库


```
-- PostgreSQL 高级列表按钮配置扩展字段
ALTER TABLE tz_list_button ADD COLUMN extra text;
COMMENT ON COLUMN tz_list_button.extra IS '按钮UI配置扩展字段';


-- Oracle 高级列表按钮配置扩展字段
ALTER TABLE tz_list_button ADD extra clob;
COMMENT ON COLUMN tz_list_button.extra IS '按钮UI配置扩展字段';
```


独立部署程序自动执行，不需要手动执行，只有多租户需要执行：研发、行政、众远智慧

## 3.2 CSB-LEGOX


### pg库


```
--pg
ALTER TABLE sys_page ADD platform_type varchar(8) NULL;
COMMENT ON COLUMN sys_page.platform_type IS '平台类型:0-PC;1-移动端;100-全部';

-- 按需执行
CREATE TABLE sys_page_ext(
    id VARCHAR(36) NOT NULL,
    tag_list VARCHAR(2048),
    crt_user VARCHAR(512),
    crt_name VARCHAR(512),
    crt_host VARCHAR(512),
    crt_time TIMESTAMP,
    upd_user VARCHAR(512),
    upd_name VARCHAR(512),
    upd_host VARCHAR(512),
    upd_time TIMESTAMP,
    attribute1 VARCHAR(256),
    attribute2 VARCHAR(256),
    attribute3 VARCHAR(256),
    attribute4 VARCHAR(256),
    attribute5 VARCHAR(256),
    PRIMARY KEY (id)
);

COMMENT ON TABLE sys_page_ext IS '页面资源扩展表';
COMMENT ON COLUMN sys_page_ext.id IS '页面资源主键id';
COMMENT ON COLUMN sys_page_ext.tag_list IS '页面资源标签列表';
COMMENT ON COLUMN sys_page_ext.crt_user IS '创建人ID';
COMMENT ON COLUMN sys_page_ext.crt_name IS '创建人名称';
COMMENT ON COLUMN sys_page_ext.crt_host IS '创建ID';
COMMENT ON COLUMN sys_page_ext.crt_time IS '创建时间';
COMMENT ON COLUMN sys_page_ext.upd_user IS '更新人ID';
COMMENT ON COLUMN sys_page_ext.upd_name IS '更新人名称';
COMMENT ON COLUMN sys_page_ext.upd_host IS '更新ID';
COMMENT ON COLUMN sys_page_ext.upd_time IS '更新时间';
COMMENT ON COLUMN sys_page_ext.attribute1 IS '拓展字段1';
COMMENT ON COLUMN sys_page_ext.attribute2 IS '拓展字段2';
COMMENT ON COLUMN sys_page_ext.attribute3 IS '拓展字段3';
COMMENT ON COLUMN sys_page_ext.attribute4 IS '拓展字段4';
COMMENT ON COLUMN sys_page_ext.attribute5 IS '拓展字段5';
```


### oracle库


```
-- Oracle
ALTER TABLE sys_page ADD platform_type varchar2(8) NULL;
COMMENT ON COLUMN sys_page.platform_type IS '平台类型:0-PC;1-移动端;100-全部';

-- 按需执行
CREATE TABLE sys_page_ext(
    id VARCHAR2(36) NOT NULL,
    tag_list VARCHAR2(2048),
    crt_user VARCHAR2(512),
    crt_name VARCHAR2(512),
    crt_host VARCHAR2(512),
    crt_time DATE,
    upd_user VARCHAR2(512),
    upd_name VARCHAR2(512),
    upd_host VARCHAR2(512),
    upd_time DATE,
    attribute1 VARCHAR2(256),
    attribute2 VARCHAR2(256),
    attribute3 VARCHAR2(256),
    attribute4 VARCHAR2(256),
    attribute5 VARCHAR2(256),
    PRIMARY KEY (id)
);

COMMENT ON TABLE sys_page_ext IS '页面资源扩展表';
COMMENT ON COLUMN sys_page_ext.id IS '页面资源主键id';
COMMENT ON COLUMN sys_page_ext.tag_list IS '页面资源标签列表';
COMMENT ON COLUMN sys_page_ext.crt_user IS '创建人ID';
COMMENT ON COLUMN sys_page_ext.crt_name IS '创建人名称';
COMMENT ON COLUMN sys_page_ext.crt_host IS '创建ID';
COMMENT ON COLUMN sys_page_ext.crt_time IS '创建时间';
COMMENT ON COLUMN sys_page_ext.upd_user IS '更新人ID';
COMMENT ON COLUMN sys_page_ext.upd_name IS '更新人名称';
COMMENT ON COLUMN sys_page_ext.upd_host IS '更新ID';
COMMENT ON COLUMN sys_page_ext.upd_time IS '更新时间';
COMMENT ON COLUMN sys_page_ext.attribute1 IS '拓展字段1';
COMMENT ON COLUMN sys_page_ext.attribute2 IS '拓展字段2';
COMMENT ON COLUMN sys_page_ext.attribute3 IS '拓展字段3';
COMMENT ON COLUMN sys_page_ext.attribute4 IS '拓展字段4';
COMMENT ON COLUMN sys_page_ext.attribute5 IS '拓展字段5';
```


## 3.2 业务库


# 程序发布


| 应用 | 分支 | 备注 |
| --- | --- | --- |
| lcp-app | 3.4.0-SNAPSHOT |  |
| lcp-data | 3.4.0-SNAPSHOT |  |
| lcp-hub | 3.4.0-SNAPSHOT |  |
| lcp-legox | 3.0.3-SNAPSHOT | csb-sys-base(3.0.4-SNAPSHOT/2.0.9.8-SNAPSHOT) |
| lcp-log | 3.4.0-SNAPSHOT | 仅天舟云发布 |


# 应用运维同步


```
   无
```

---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4
url_hash: d171c20f3593
document_key: TZSERVER_d171c20f3593
doc_id: tzserver_v2-1gs7v9eb0vbc4
title: 后端
md_hash: 30374ac2d7516d06
version: 1764143692
image_count: 0
crawled_at: 2026-06-11 16:20:37
---

# 后端

- [增量升级(v3.4.0->v3.5.0)](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#73sgel)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#eixnsg)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#aoimea)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#9jfkdn)
- [3.1 配置库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#a8u1fy)
- [PostgreSQL](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#bqqkuo)
- [Oracle](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#fdi79b)
- [3.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#44iih1)
- [pg库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#ssmkd)
- [3.2 业务库](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#imyj0)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#a8f2vp)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1gs7v9eb0vbc4#bw0ys9)


# 增量升级(v3.4.0->v3.5.0)


>


 程序发布前执行项


# 1、Apollo配置


无

# 2、Nacos配置


无

# 3、数据库脚本


## 3.1 配置库


##### PostgreSQL


独立部署程序自动执行，不需要手动执行，只有多租户需要执行：研发、行政、众远智慧

```

ALTER TABLE obj_excel_column ADD is_show_ext_sheet varchar(32) NULL;
COMMENT ON COLUMN obj_excel_column.is_show_ext_sheet IS '是否显示在额外sheet';

ALTER TABLE obj_excel_column ADD ext_sheet_columns varchar(2048) NULL;
COMMENT ON COLUMN obj_excel_column.ext_sheet_columns IS '额外显示字段';
```


##### Oracle


独立部署程序自动执行，不需要手动执行，只有多租户需要执行：研发、行政、众远智慧

```
-- 添加 is_show_ext_sheet 列并添加注释
ALTER TABLE obj_excel_column
    ADD is_show_ext_sheet VARCHAR2(32) NULL;

COMMENT ON COLUMN obj_excel_column.is_show_ext_sheet
IS '是否显示在额外sheet';

-- 添加 ext_sheet_columns 列并添加注释
ALTER TABLE obj_excel_column
    ADD ext_sheet_columns VARCHAR2(2048) NULL;

COMMENT ON COLUMN obj_excel_column.ext_sheet_columns
IS '额外显示字段';
```


独立部署程序自动执行，不需要手动执行，只有多租户需要执行：研发、行政、众远智慧

## 3.2 CSB-LEGOX


### pg库


无

## 3.2 业务库


# 程序发布


| 应用 | 分支 | 备注 |
| --- | --- | --- |
| lcp-app | 3.5.0-SNAPSHOT |  |
| lcp-data | 3.5.0-SNAPSHOT |  |
| lcp-hub | 3.5.0-SNAPSHOT |  |
| lcp-legox | 3.0.3-SNAPSHOT | csb-sys-base(3.0.4-SNAPSHOT/2.0.9.8-SNAPSHOT) 重新构建即可 |
| lcp-log | 3.4.0-SNAPSHOT | 仅天舟云发布 |


# 应用运维同步


```
   无
```

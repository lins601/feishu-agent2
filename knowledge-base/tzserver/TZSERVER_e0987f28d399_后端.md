---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1go90k83nlj9a
normalized_url: https://docs.cvte.com/docs/tzserver_v2/tzserver_v2-1go90k83nlj9a
url_hash: e0987f28d399
document_key: TZSERVER_e0987f28d399
doc_id: tzserver_v2-1go90k83nlj9a
title: 后端
md_hash: 11ce2130e669bdca
version: 1756721340
image_count: 0
crawled_at: 2026-06-11 16:20:28
---

# 后端

#### 程序包更新


```
lcp-app项目升级到3.2.2-SNAPSHOT
```


#### 数据库脚本执行


pgsql


```
ALTER TABLE obj_workflow_relation ADD column IF NOT EXISTS is_default varchar(100) NULL;
COMMENT ON COLUMN obj_workflow_relation.is_default IS '是否默认流程';

WITH ranked_records AS (
    SELECT
        id,
        ROW_NUMBER() OVER (PARTITION BY class_id ORDER BY crt_time ASC) AS rn
    FROM obj_workflow_relation where is_deleted ='0'
)
UPDATE obj_workflow_relation t
SET is_default = '1'
    FROM ranked_records r
WHERE t.id = r.id AND r.rn = 1;
```


oracle


```
ALTER TABLE obj_workflow_relation ADD is_default varchar2(100) NULL;
COMMENT ON COLUMN obj_workflow_relation.is_default IS '流程类型';

MERGE INTO obj_workflow_relation t
    USING (
        SELECT
            id,
            ROW_NUMBER() OVER (PARTITION BY class_id ORDER BY crt_time ASC) AS rn
        FROM obj_workflow_relation where is_deleted ='0'
    ) r
    ON (t.id = r.id AND r.rn = 1)
    WHEN MATCHED THEN
        UPDATE SET t.is_default = '1';
```

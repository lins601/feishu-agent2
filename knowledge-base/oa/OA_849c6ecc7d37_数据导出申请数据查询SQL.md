---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1h5rgba0r4bqi
normalized_url: https://docs.cvte.com/docs/oa/oa-1h5rgba0r4bqi
url_hash: 849c6ecc7d37
document_key: OA_849c6ecc7d37
doc_id: oa-1h5rgba0r4bqi
title: 数据导出申请数据查询SQL
md_hash: 7ec68f4fbf91e271
version: 1772008543
image_count: 0
crawled_at: 2026-06-11 16:01:51
---

# 数据导出申请数据查询SQL

```
SELECT
    soe.fd_name AS 申请人,
    e.FD_APPLICANT_PATH AS 部门全称,
    e.FD_DATA_NAME AS 项目名称,
    e.FD_DATA_SYSTEM AS 数据所属系统,
    e.FD_REASON AS 申请原因,
    e.FD_REQUEST AS 导出数据要求,
    e.FD_PURPOSE AS 导出数据用途,
    e.FD_CREATION_DATE AS 申请时间,
    e.FD_SEQ_NO AS 流水号,
    e.FD_NEEDED_DATE AS 需求完成时间
FROM ekp_bi_data_export e
LEFT JOIN sys_Org_element soe
    ON soe.fd_id = e.FD_APPLICANT  -- 确认fd_id和FD_APPLICANT数据类型一致（比如都是字符串/数字）
WHERE e.FD_DATA_SYSTEM  IN ('CPLM','Agile')
AND e.FD_CREATION_DATE > TIMESTAMP '2025-07-01 00:00:00.000'
ORDER BY e.FD_CREATION_DATE desc;
```

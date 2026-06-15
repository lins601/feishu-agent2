---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1gvp0rqo9ct8h
normalized_url: https://docs.cvte.com/docs/oa/oa-1gvp0rqo9ct8h
url_hash: bcf4761a6da7
document_key: OA_bcf4761a6da7
doc_id: oa-1gvp0rqo9ct8h
title: 查询流程模板固定审核人
md_hash: 544a47a667b97ab0
version: 1765536803
image_count: 0
crawled_at: 2026-06-11 16:01:44
---

# 查询流程模板固定审核人

```
SELECT krt.FD_NAME AS 流程名称 ,krt.FD_ID AS 流程id,'原生流程' AS 流程类型
FROM LBPM_PROCESS_DEFINITION d
LEFT JOIN LBPM_TEMPLATE lt ON (lt.fd_id =d.FD_TEMPLATE_ID )
LEFT JOIN KM_REVIEW_TEMPLATE krt ON (krt.fd_id = lt.FD_MODEL_ID)
WHERE
--d.FD_TEMPLATE_ID ='15b17d3c549fcfac7e8a8f943f2af27f'
 d.fd_content like '%handlerSelectType="org"%'
AND d.FD_IS_CURRENT_VERSION ='1' AND lt.FD_IS_DELETE ='0'
UNION
SELECT krt.FD_NAME AS 流程名称,krt.fd_id 流程id,'集成流程' AS 流程类型
FROM LBPM_PROCESS_DEFINITION d
LEFT JOIN LBPM_TEMPLATE lt ON (lt.fd_id =d.FD_TEMPLATE_ID )
LEFT JOIN lbpm_docking_template krt ON (krt.fd_id = lt.FD_MODEL_ID)
WHERE
--d.FD_TEMPLATE_ID ='15b17d3c549fcfac7e8a8f943f2af27f'
 d.fd_content like '%handlerSelectType="org"%'
AND d.FD_IS_CURRENT_VERSION ='1' AND lt.FD_IS_DELETE ='0'
;
```


```
SELECT krt.FD_NAME AS 流程名称 ,krt.FD_ID AS 流程id,'原生流程' AS 流程类型
FROM LBPM_PROCESS_DEFINITION d
LEFT JOIN LBPM_TEMPLATE lt ON (lt.fd_id =d.FD_TEMPLATE_ID )
LEFT JOIN KM_REVIEW_TEMPLATE krt ON (krt.fd_id = lt.FD_MODEL_ID)
WHERE
--d.FD_TEMPLATE_ID ='15b17d3c549fcfac7e8a8f943f2af27f'
 d.fd_content like '%handlerSelectType="org"%'
  AND d.FD_CONTENT LIKE '%handlerIds="'||'1558017dcf8cef964d66eb34b5882c37'||'"%'
AND d.FD_IS_CURRENT_VERSION ='1' AND lt.FD_IS_DELETE ='0'
AND krt.fd_id IS NOT null
UNION
SELECT krt.FD_NAME AS 流程名称,krt.fd_id 流程id,'集成流程' AS 流程类型
FROM LBPM_PROCESS_DEFINITION d
LEFT JOIN LBPM_TEMPLATE lt ON (lt.fd_id =d.FD_TEMPLATE_ID )
LEFT JOIN lbpm_docking_template krt ON (krt.fd_id = lt.FD_MODEL_ID)
WHERE
--d.FD_TEMPLATE_ID ='15b17d3c549fcfac7e8a8f943f2af27f'
 d.fd_content like '%handlerSelectType="org"%'
 AND d.FD_CONTENT LIKE '%handlerIds="'||'1558017dcf8cef964d66eb34b5882c37'||'"%'
AND d.FD_IS_CURRENT_VERSION ='1' AND lt.FD_IS_DELETE ='0'
```

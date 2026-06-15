---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1gogfq6fns22i
normalized_url: https://docs.cvte.com/docs/oa/oa-1gogfq6fns22i
url_hash: fe91dea8818d
document_key: OA_fe91dea8818d
doc_id: oa-1gogfq6fns22i
title: 查询工作项
md_hash: ae37eba554881581
version: 1769479790
image_count: 0
crawled_at: 2026-06-11 16:01:33
---

# 查询工作项

#### 查询待处理流程


```
SELECT * FROM (
SELECT tmp.* FROM (
SELECT ldm.DOC_SUBJECT "流程标题",sop.FD_LOGIN_NAME "处理人账号" ,ldm.DOC_CREATE_TIME "创建时间" ,lt.FD_NAME AS 模板名称,ldm.DOC_STATUS 流程状态 ,'集成' AS 类型,lds.FD_NAME 系统或分类,ldm.fd_id AS 流程id
,ldr.FD_FORM_INSTANCE_ID 单据id,'https://xx.xx.xx/sys/lbpmdocking/lbpm_docking_main/lbpmDockingMain.do?method=view&fdId='||ldm.fd_id AS 流程链接
FROM LBPM_WORKITEM lw
LEFT JOIN lbpm_docking_main ldm ON (ldm.fd_id = lw.FD_PROCESS_ID )
LEFT JOIN LBPM_DOCKING_TEMPLATE lt ON (lt.fd_id = ldm.FD_TEMPLATE_ID )
LEFT JOIN LBPM_DOCKING_RELATION ldr ON (ldr.FD_MAIN_MODEL_ID = ldm.FD_ID )
LEFT JOIN SYS_CATEGORY_MAIN scm ON (scm.FD_ID = lt.FD_CATEGORY_ID )
LEFT JOIN LBPM_DOCKING_SYSTEM lds ON (lds.fd_id = ldr.FD_SYSTEM_ID )
LEFT JOIN sys_org_person sop ON (sop.fd_id = lw.FD_EXPECTED_ID)
WHERE 1=1
--AND sop.fd_login_name IN ('caiziqin')
AND lw.FD_START_DATE < TIMESTAMP '2026-08-12 00:00:00.000' AND ldm.fd_id IS NOT NULL AND ldm.DOC_STATUS!='10'
UNION
SELECT ldm.DOC_SUBJECT "流程标题",sop.FD_LOGIN_NAME "处理人账号",ldm.DOC_CREATE_TIME "创建时间",lt.FD_NAME AS 模板名称,ldm.DOC_STATUS 流程状态,'原生' AS 类型,scm.fd_name AS 系统或分类,ldm.fd_id AS 流程id
,ldm.fd_id 单据id,'https://xx.xx.xx/km/review/km_review_main/kmReviewMain.do?method=view&fdId='||ldm.fd_id AS 流程链接
FROM LBPM_WORKITEM lw
LEFT JOIN km_review_main ldm ON (ldm.fd_id = lw.FD_PROCESS_ID )
LEFT JOIN KM_REVIEW_TEMPLATE lt ON (lt.fd_id = ldm.FD_TEMPLATE_ID )
LEFT JOIN SYS_CATEGORY_MAIN scm ON (scm.FD_ID = lt.FD_CATEGORY_ID )
LEFT JOIN sys_org_person sop ON (sop.fd_id = lw.FD_EXPECTED_ID)
WHERE 1=1
--AND sop.fd_login_name IN ('caiziqin')
AND lw.FD_START_DATE < TIMESTAMP '2026-08-12 00:00:00.000' AND ldm.fd_id IS NOT NULL AND ldm.DOC_STATUS!='10'
) tmp ORDER BY tmp.处理人账号 desc,tmp.创建时间  DESC
) t WHERE 1=1;


```

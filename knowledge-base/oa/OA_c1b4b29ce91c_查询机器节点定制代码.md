---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1h33k6qq24bbv
normalized_url: https://docs.cvte.com/docs/oa/oa-1h33k6qq24bbv
url_hash: c1b4b29ce91c
document_key: OA_c1b4b29ce91c
doc_id: oa-1h33k6qq24bbv
title: 查询机器节点定制代码
md_hash: 6fd547db67694e89
version: 1768917207
image_count: 0
crawled_at: 2026-06-11 16:01:35
---

# 查询机器节点定制代码

# 查询机器节点定制代码


## 把RdReviewRobot替换为要查询的代码


```
SELECT DISTINCT krt.FD_NAME ,'原生流程' AS 流程类型 ,krt.fd_id AS 流程模板id
FROM ekp.LBPM_RT_NODE_DEFINE lp
LEFT JOIN lbpm_process p ON (lp.fd_process_id = p.fd_id)
LEFT JOIN km_review_main krm   ON (p.fd_model_id =krm.FD_ID )
LEFT JOIN km_review_template krt ON (krt.fd_id = krm.FD_TEMPLATE_ID )
WHERE 1=1
AND lp.FD_CONTENT LIKE '<robotNode%' AND lp.FD_CONTENT LIKE '%RdReviewRobot%'
AND krt.fd_id IS NOT null
UNION ALL
SELECT DISTINCT krt.FD_NAME ,'集成流程' AS 流程类型 , krt.fd_id AS 流程模板id
FROM ekp.LBPM_RT_NODE_DEFINE lp
LEFT JOIN lbpm_process p ON (lp.fd_process_id = p.fd_id)
LEFT JOIN lbpm_docking_main krm   ON (p.fd_model_id =krm.FD_ID )
LEFT JOIN lbpm_docking_template krt ON (krt.fd_id = krm.FD_TEMPLATE_ID )
WHERE 1=1 AND krt.fd_id IS NOT null
AND lp.FD_CONTENT LIKE '<robotNode%' AND lp.FD_CONTENT LIKE '%RdReviewRobot%';
```


## 查看流程模板方法


原生流程模板:


```
https://xx.xx.xx/km/review/km_review_template/kmReviewTemplate.do?method=view&fdId=替换为流程模板id
```


集成流程模板:


```
https://xx.xx.xx/sys/lbpmdocking/lbpm_docking_template/lbpmDockingTemplate.do?method=view&fdId=替换为流程模板id
```

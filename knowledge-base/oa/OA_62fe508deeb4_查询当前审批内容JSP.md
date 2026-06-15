---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1goi0jaika23q
normalized_url: https://docs.cvte.com/docs/oa/oa-1goi0jaika23q
url_hash: 62fe508deeb4
document_key: OA_62fe508deeb4
doc_id: oa-1goi0jaika23q
title: 查询当前审批内容JSP
md_hash: 0679de7f34355cae
version: 1757037872
image_count: 0
crawled_at: 2026-06-11 16:01:34
---

# 查询当前审批内容JSP

#### 查询当前流程的JSP


```
SELECT krt.fd_name AS 模板名称,sxt.FD_DISPLAY_JSP
FROM SYS_XFORM_TEMPLATE sxt
LEFT JOIN km_review_template krt ON (krt.FD_ID =sxt.FD_MODEL_ID )
WHERE krt.FD_NAME LIKE '%源控%';
```

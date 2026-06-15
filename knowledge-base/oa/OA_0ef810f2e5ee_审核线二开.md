---
source: MinDoc
project_name: OA
source_url: https://docs.cvte.com/docs/oa/oa-1ffe96usmo3l6
normalized_url: https://docs.cvte.com/docs/oa/oa-1ffe96usmo3l6
url_hash: 0ef810f2e5ee
document_key: OA_0ef810f2e5ee
doc_id: oa-1ffe96usmo3l6
title: 审核线二开
md_hash: b63bf4a811788325
version: 1710748309
image_count: 0
crawled_at: 2026-06-11 16:01:31
---

# 审核线二开

#### 获取部门的审核线


com.landray.kmss.cvte.review.util.SysOrgElementUtil.getRoleLineElement(elment, confName, roleName)


##### 参数说明


| 参数名称 | 参数说明 | 参数类型 |
| --- | --- | --- |
| elment | 真实部门对象 | com.landray.kmss.sys.organization.model.SysOrgElement |
| confName | 可选值: “部门HRBP”,”部门主管”,”中心主管”,”事业部总监”,”总经理”,”人力资源负责人”,”BG财务审核人” | 字符串 |
| roleName | 值保持和confName一样 | 字符串 |


##### 使用例子


com.landray.kmss.cvte.review.util.SysOrgElementUtil.getRoleLineElement($组织架构.根据登录名取用户$($当前用户$).getFdParent(), “人力资源负责人”, “人力资源负责人”)

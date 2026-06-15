---
source: MinDoc
project_name: 开发规范文档
source_url: https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3
normalized_url: https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3
url_hash: f3ee9cc7c4b9
document_key: 开发规范文档_f3ee9cc7c4b9
doc_id: plateform_dev_rule-1gml6nmrhrrs3
title: 代码提交Log规范
md_hash: f0e3334bb92c5393
version: 1754903382
image_count: 0
crawled_at: 2026-06-11 17:53:02
---

# 代码提交Log规范

- [一、原则](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#3kwnzq)
- [二、格式说明](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#8ft8m4)
- [2.1 格式详细说明](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#gbq7ib)
- [2.2 格式示例](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#fpxyfj)
- [2.3 操作编码说明](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#dt0e85)
- [三、细节规定](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#4uwsew)
- [3.1 大小写](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#98sjee)
- [3.2 间隔操作符](https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1gml6nmrhrrs3#d5zogr)


# 一、原则


- 能准确清晰地说明本次代码修改的意图，便于理解和追溯
- 能区分产品规划的功能、版本等，便于遴选和撤回
- 用语通俗易懂，符合团队上下文


# 二、格式说明


>


 [操作编码],[版本号],[需求编号],[JIRA编号],[功能描述]


## 2.1 格式详细说明


| 栏目 | 说明 | 是否必填 | 备注 |
| --- | --- | --- | --- |
| 操作编码 | 此次修改操作代号 | 是 | modify/fix/feature/revert/merge |
| 版本号 | 所属产品的版本号 | 是 | 3.3.0 |
| 需求编号 | 需求来源编号 | 是 | TZC000987/简道云编号 |
| JIRA编号 | jira任务编号 | 是 | DP-793 |
| 功能描述 | what/why/how | 是 | 说明改了什么/为什么改/怎么改的 |


## 2.2 格式示例


>


fix,3.2.0,TZC000987,DP-793,【首屏优化】后端提供关联数据翻译支持批量翻译


## 2.3 操作编码说明


| 操作编码 | 说明 | 备注 |
| --- | --- | --- |
| modify | 修改 | 功能调整，逻辑完善 |
| fix | 修复 | 修复bug，缺陷等 |
| feature | 新特性/新功能 | 新增的功能/文件/类 |
| revert | 还原 | 代码取消错误需要还原 |
| merge | 合并代码 | 合并代码中出现的冲突解决 |


# 三、细节规定


## 3.1 大小写


- [操作编码]统一使用小写
- [版本号]不区分快照还是稳定版本，统一使用数字代号，不带其他，例如：V3.3.0、v3.3.0


## 3.2 间隔操作符


统一使用 **英文逗号** 隔开，不间隔空格，最后的功能描述除外

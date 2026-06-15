---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1gebecru2gsn1
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1gebecru2gsn1
url_hash: 65931cf6dc96
document_key: TZDOC_65931cf6dc96
doc_id: tzdoc_v2-1gebecru2gsn1
title: 自定义导入(部分接管)
md_hash: 84055d1e085e42a6
version: 1745548077
image_count: 0
crawled_at: 2026-06-11 16:10:37
---

# 自定义导入(部分接管)

# 1 场景说明


 在一些情况下，业务想自定义导入逻辑
 开发存在如下问题
 1、数据字典需要自己翻译
 2、需要自己写前端代码，配置网关转发


 天舟云提供了方案解决以上问题
 1、在导入前事件自定义导入逻辑
 2、天舟云会把字段的翻译信息传到业务事件
 3、业务返回指定标识，阻断天舟云默认的后续导入动作

---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h8vi4k6djudg
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1h8vi4k6djudg
url_hash: 6cf1aa844b91
document_key: TZDOC_6cf1aa844b91
doc_id: tzdoc_v2-1h8vi4k6djudg
title: 定制二开事件错误
md_hash: d06d1a9c2b6b5b66
version: 1775529553
image_count: 0
crawled_at: 2026-06-11 16:14:04
---

# 定制二开事件错误

### 场景


天舟云开放了大量的事件(创建前、导入前、审批前等)，业务方可根据需要订阅对应的事件，在执行业务事件时有可能会出现一些报错


### 事件接口出现404报错


如报错: [http://lcp-app/v1/internal/event/client/px/dispatch，是不是有工程项目名？要增加一层路径地址？](http://lcp-app/v1/internal/event/client/px/dispatch，是不是有工程项目名？要增加一层路径地址？)


**排查步骤**


1、检查业务系统是否在注册中心注册成功
 [https://csb-eureka1.gz.cvte.cn/](https://csb-eureka1.gz.cvte.cn/) 中检查服务名(如**lcp-app**)是否存在，可忽略大小写


2、检查业务系统项目内部是否有context path
 在鲸云找到容器访问的地址，浏览器直接访问接口看是否报错404
 如: [http://172.29.10.40:8081/v1/internal/event/client/px/dispatch](http://172.29.10.40:8081/v1/internal/event/client/px/dispatch)

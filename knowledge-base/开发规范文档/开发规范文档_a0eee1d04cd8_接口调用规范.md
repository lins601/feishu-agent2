---
source: MinDoc
project_name: 开发规范文档
source_url: https://docs.cvte.com/docs/plateform_dev_rule/call_rest_rule
normalized_url: https://docs.cvte.com/docs/plateform_dev_rule/call_rest_rule
url_hash: a0eee1d04cd8
document_key: 开发规范文档_a0eee1d04cd8
doc_id: call_rest_rule
title: 接口调用规范
md_hash: 62bf6308c9ead1a8
version: 1703665167
image_count: 0
crawled_at: 2026-06-11 17:53:02
---

# 接口调用规范

## 1 请求数极尽少


- 减少很小接口请求
- 同一个功能不同组件尽量合并到一个接口返回
- 开发以组件方式，接口调用以功能聚合


## 2 延迟滞后请求非主功能请求


- 只请求首次打开或本次功能所需的接口
- 翻译旁枝功能可以延迟请求做缓存策略方式


## 3 启用缓存策略


- 数据字典翻译
- 菜单
- 当前用户信息
- 不怎么变化的数据接口


## 4 分主次顺序


- 核心功能接口优先请求
- 辅助旁枝功能接口次之


## 5 优化策略


- http2协议
- 多域名负载:cdn1.cvte.com;cdn2.cvte.com;

---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/serial_no_error
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/serial_no_error
url_hash: 2d9395fc958b
document_key: TZDOC_2d9395fc958b
doc_id: serial_no_error
title: 流水号报错常见问题
md_hash: 37c18a570825c175
version: 1775121849
image_count: 0
crawled_at: 2026-06-11 16:14:04
---

# 流水号报错常见问题

# 天舟云流水号 — 报错排查指引


>


流水号可能出现报错，本文档汇总常见报错场景与排查步骤，帮助用户自助解决问题。


---


## 一、serialno_rule_no：`数据重复`


### 现象


数据有多条导致报错了


### 原因


并发导致数据插入重复


### 解决方法


将表serialno_rule_no的rule_id和param_code建立组合唯一索引


---


## 二、超过最大重试次数


### 现象


表单创建时候报：流水号超过最大重试次数


### 原因


当前太多并发请求同一个流水号，导致自旋重试次数超过限制报错


### 解决方法


- 重试一下
- 加大csb.serial.repeat.times配置


---

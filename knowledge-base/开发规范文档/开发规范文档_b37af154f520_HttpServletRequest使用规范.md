---
source: MinDoc
project_name: 开发规范文档
source_url: https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1f7tufn496f7h
normalized_url: https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1f7tufn496f7h
url_hash: b37af154f520
document_key: 开发规范文档_b37af154f520
doc_id: plateform_dev_rule-1f7tufn496f7h
title: HttpServletRequest使用规范
md_hash: ebf20f5b71855735
version: 1702288925
image_count: 0
crawled_at: 2026-06-11 17:53:02
---

# HttpServletRequest使用规范

## 1. 不要将HttpServletRequest传递到任何异步方法中


在Tomcat中，Request以及Response对象都是会被循环使用的，每次请求结束后都会将Request重置。在异步方法中操作HttpServletRequest，就会出现上一次请求结束了，Request重置后又被修改了，下一次请求进来，拿到的Request并不是重置后的，就会出现问题。具体可参考：[https://www.jb51.net/article/226225.htm](https://www.jb51.net/article/226225.htm)

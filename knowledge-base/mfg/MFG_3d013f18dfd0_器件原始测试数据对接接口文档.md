---
source: MinDoc
project_name: MFG
source_url: https://docs.cvte.com/docs/mfg/mfg-1gns2cesl4qp3
normalized_url: https://docs.cvte.com/docs/mfg/mfg-1gns2cesl4qp3
url_hash: 3d013f18dfd0
document_key: MFG_3d013f18dfd0
doc_id: mfg-1gns2cesl4qp3
title: 器件原始测试数据对接接口文档
md_hash: 20b5509d1c1f06f0
version: 1756265767
image_count: 0
crawled_at: 2026-06-11 14:22:49
---

# 器件原始测试数据对接接口文档

# 器件原始测试数据对接接口文档


###### 版本号：v1.0.1


###### 更新日期：2024 年 8 月


###### 文档目的：定义系统对外提供的 API 接口规范，包括接口地址、请求参数、响应格式、错误码等，为前后端开发、测试及第三方集成提供参考依据


适用范围：[前端开发团队、后端开发团队、测试团队、第三方合作厂商等]


### 1. 文档说明


#### 1.1 接口通用规则


请求协议：采用 HTTPS 协议，确保数据传输安全性
生产环境：[https://itgw.cvte.com](https://itgw.cvte.com)
测试环境：[https://itgw-sit.cvte.com](https://itgw-sit.cvte.com)


字符编码：统一使用 UTF-8 编码
超时时间：默认接口超时时间为 60s，特殊耗时接口（如文件上传）可延长至 120s
**认证方式：采用 Token 认证，请求时需在 HTTP Header 中携带 apikey: {token}（token 由 CVTE 提供，请妥善保管）**


### 1.2 通用响应格式


所有接口的响应均遵循统一结构，包含状态码、提示信息及业务数据：


| 字段名 | 类型 | 必选 | 说明 |
| --- | --- | --- | --- |
| status | string | 是 | 0 成功，其他失败 |
| message | string | 是 | 描述信息 |
| data | object | 是 | 业务数据，具体看接口参数返回 |


成功案例：


```
{
    "status": "0",
    "message": "success",
    "data": false
}
```


失败案例：


```
{
    "status": "500",
    "message": "请选择要上传的文件",
    "data": {
        "traceId": null
    }
}
```


### 1.3 器件原始测试数据压缩文件上传(ZIP 格式)


接口地址：/env-101/mfg/qmsapp/spec/open/api/v1/upload
请求方式：POST
内容类型: multipart/form-data
请求参数(multipart/form-data)


| 字段名 | 类型 | 必选 | 说明 |
| --- | --- | --- | --- |
| materialNo | string | 是 | 物料编码 |
| supplierCode | string | 是 | 供应商码（由 CVTE 体提供） |
| dc | string | 是 | dc 日期 |
| lotNo | string | 是 | 生产批次 |
| file | File | 是 | 文件类型 |


```
curl --location 'https://itgw-sit.cvte.com/env-101/mfg/qmsapp/spec/open/api/v1/upload' \
--header 'apikey: xxxxxxx' \
--form 'materialNo="004.010.0050060"' \
--form 'supplierCode="xxxx"' \
--form 'dc="250731"' \
--form 'lotNo="25073101"' \
--form 'file=@"/path/to/file"'
```

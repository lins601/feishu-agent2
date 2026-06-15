---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1g3vrlaju3lt1
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1g3vrlaju3lt1
url_hash: e474b37ae421
document_key: TZDOC_e474b37ae421
doc_id: tzdoc_v2-1g3vrlaju3lt1
title: 表单数据创建
md_hash: 0b0f3fa6ca4ec7b2
version: 1763947678
image_count: 0
crawled_at: 2026-06-11 16:11:12
---

# 表单数据创建

## 1 总述


```
  表单数据创建
```


| 栏目 | 说明 | 备注 |
| --- | --- | --- |
| URL | /v1/app/openapi/form/{主表单分类编码(表名)}/batch | APP项目接口 |
| 所属服务 | lcp-app |  |
| 请求方式 | POST |  |
| 请求数据格式 | JSON |  |
| 返回数据格式 | JSON |  |
| Header | x-iac-token或x-auth-token,x-app-id,x-tenant-id |  |


## 2 入参说明


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| id | String | 否 | 表单主表数据id |  |
| classId | String | 是 | 分类id |  |
| objAttrItemList | List< FormAttrValueDTO > | 是 | 主表数据 |  |
| tableList | List< TableInfo > | 是 | 明细表数据 |  |
| userInfo | UserInfo | 否 | 操作用户信息 |  |
| saveMode | String | 否 | 保存模式 | 暂存:stage ,保存: save,发起流程: submit ,不填默认为保存 |
| UserInfo结构 |  |  |  |  |


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| id | String | 否 | 用户id |  |
| account | String | 否 | 用户域账号 |  |
| name | String | 否 | 用户姓名 |  |


TableInfo结构


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| tableName | String | 是 | 明细表表名 |  |
| rowList | List< LineValue > | 是 | 明细行信息 |  |


FormAttrValueDTO结构


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| attrId | String | 是 | 属性id | 和apiName二选一 |
| apiName | String | 是 | 属性apiName | 和attrId二选一 |
| attrValue | Object | 是 | 属性值 | 多值使用集合List,单值使用对应类型值 |


LineValue


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| dataList | List< ValueInfo > | 是 | 明细表字段列表 |  |


ValueInfo


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| apiName | String | 是 | 字段名(code) |  |
| attrValue | Object | 是 | 字段值 |  |


### 入参例子


```
[
    {
        "classId": "47ae003849234a7ba9872bd629e61c76",//表单分类id
        "objAttrItemList":[ //主表数据
             {
                "apiName":"SEX",
                "attrValue":"1"
             }
         ],
        "tableList": [{
            "tableName": "PROD_DELISTING_FLOW_ITEM_BJ",//明细表表名
            "rowList": [
                {
                    "dataList": [
                        {
                            "apiName": "BOTTLENECK_ITEM_NUMBER",
                            "attrValue": "0002222"
                        }
                    ]
                }
            ]
        }]
    }
]
```


### 3 出参说明


#### 出参示例


```
{
    "status": "0",
    "message": "success",
    "data": {
        "pagination": null,
        "content": 1
    }
}
```

---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fu0orqttjjgi
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fu0orqttjjgi
url_hash: 5832ca3bb0f9
document_key: TZDOC_5832ca3bb0f9
doc_id: tzdoc_v2-1fu0orqttjjgi
title: 动态流程分支条件接口
md_hash: a0553630fc76eb48
version: 1727158101
image_count: 0
crawled_at: 2026-06-11 16:09:11
---

# 动态流程分支条件接口

## 1 总述


| 栏目 | 说明 | 备注 |
| --- | --- | --- |
| 请求方式 | POST |  |
| 请求数据格式 | JSON |  |
| 返回数据格式 | JSON |  |
| Header | x-iac-token或x-auth-token |  |


## 2 入参说明


**SDK对象**: com.cvte.lcp.base.common.dto.workflow.WorkFlowConnectorConditionParamDTO


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| formId | String | 是 | 表单分类 |  |
| stateMachineCode | String | 是 | 工作流编码 |  |
| conditionCode | String | 是 | 分支条件编码 |  |
| formInstanceId | String | 是 | 表单数据id(单据实例id) |  |
| optAccount | String | 是 | 操作人账号 |  |
| optName | String | 是 | 操作人姓名 |  |
| crtAccount | String | 是 | 单据创建人账号 |  |
| crtName | String | 是 | 单据创建人姓名 |  |
| mainMap | Map<String,Object> | 是 | 主表数据 | key为字段的apiName,value为字段值，主表字段: {“FACTORY_CODE”:”1001”,”FACTORY_NAME”:”工厂1””} |
| bizListMap | Map<String, List<Map<String,Object>>> | 否 | 明细表数据 | key为明细表表名(大写),value为明细表数据，业务表字段: {“DETAILS”:[{“MATERIAL_CODE”:”1001”,”MATERIAL_NAME”:”物料1”}]} |


### 入参例子


```
{
    "formId": "f185e92d519a4972824c19f4e1ab4b15",
    "productId": "e7a8d019e4d64e12aa1407fad35bb040",
    "optName": "叶端旺",
    "bizListMap": {},
    "optAccount": "yeduanwang",
    "crtName": "叶端旺",
    "formInstanceId": "165ced70bd0046c880dd1f85af8bf607",
    "mainMap": {
        "TEXT_DFABE4B326655DC7": "111",
        "CRT_TIME": 1727146332597,
        "CRT_USER": "yeduanwang",
        "UPD_TIME": 1727148751545,
        "UPD_USER": "yeduanwang",
        "CURRENT_NODE": "n_sys_draft",
        "WF_RELATION_ID": "6dddfd85a58c4b27b866c0b83c7d7379",
        "CURRENT_HANDLER": "yeduanwang",
        "CURRENT_HANDLER_NAME": "叶端旺",
        "CURRENT_NODE_NAME": "起草节点",
        "CLASS_ID": "f185e92d519a4972824c19f4e1ab4b15",
        "SERIAL_NUMBER": "TZ-DYNAMIC_CONDITIONS202409240000001",
        "LIFECYCLE": "DRAFT",
        "ID": "165ced70bd0046c880dd1f85af8bf607",
        "WF_INSTANCE_ID": "0a464c322b544c9b9314362a5f2154b3",
        "DVERSION_NO": 2
    },
    "conditionCode": "custom_condition_code",
    "stateMachineCode": "DYNAMIC_CONDITIONS_1726815964791",
    "tenantId": "c518f53d-b405-4111-afe1-5c082b284971",
    "crtAccount": "yeduanwang"
}
```


### 3 出参说明


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| data | String | 是 | 条件是否成立 | “1”：条件成立 “0”:条件不成立 |


#### 出参示例


```
{
    "status": "0",
    "message": "success",
    "data": "1"
}
```

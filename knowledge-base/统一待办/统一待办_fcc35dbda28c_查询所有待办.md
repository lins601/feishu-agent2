---
source: MinDoc
project_name: 统一待办
source_url: https://docs.cvte.com/docs/unify_todo/unify_todo-1eko9994useli
normalized_url: https://docs.cvte.com/docs/unify_todo/unify_todo-1eko9994useli
url_hash: fcc35dbda28c
document_key: 统一待办_fcc35dbda28c
doc_id: unify_todo-1eko9994useli
title: 查询所有待办
md_hash: 785b95b7b8e707c8
version: 1775618137
image_count: 0
crawled_at: 2026-06-11 17:54:17
---

# 查询所有待办

[https://kb.cvte.com/pages/viewpage.action?pageId=172615354](https://kb.cvte.com/pages/viewpage.action?pageId=172615354)


# 查询接口


## 接口关键说明


| 接口关键值域 | 说明 | 备注 |
| --- | --- | --- |
| 接口URL | `https://csbsit-api.gz.cvte.cn/todo-api/v1/todo/teams/unified` | 正式环境: `https://csb-api.gz.cvte.cn/todo-api/v1/todo/teams/unified` |
| 接口请求方式 | GET |  |
| 交互数据格式 | Form |  |
| 请求数据对象 | handler（登录用户名账号） |  |
| 返回对象 | RestResponse |  |
| 鉴权方式 | JWT 鉴权 |  |


---


## 1.1 请求数据说明


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| keyword | String | 否 | 关键字 | 待办标题/内容 |
| sourceKey | String | 否 | 待办来源 | 个人待办：发起人；系统待办：来源系统 |
| pageNum | Integer | 否 | 页码 | 默认 1 |
| pageSize | Integer | 否 | 每页条数 | 默认 20 |


---


## 1.2 请求数据示例


```
{
    "status": "0",
    "message": "success",
    "data": {
        "list": [
            {
                "id": "93e3c7ad35554c6c84d02112517da91c",
                "todoId": "6097ddcc94af44148a260576ddb14514",
                "userId": null,
                "userAccount": null,
                "userName": null,
                "userType": "2",
                "state": "2",
                "readTime": 1553270400000,
                "isCompleteRead": null,
                "isEnabled": "1",
                "isDeleted": "0",
                "title": "1",
                "content": "<p></p>",
                "deadline": null,
                "applicantId": "qianyuhui",
                "applicantUser": "qianyuhui",
                "applicantName": "钱育辉",
                "handlerId": "chenweibin",
                "handlerUser": "chenweibin",
                "handlerName": "陈伟斌",
                "todoFile": null,
                "todoUrl": null,
                "todoType": null,
                "todoCate": null,
                "todoState": "3",
                "cc": "null",
                "crtTime": 1553305030000,
                "completeTime": null,
                "sid": null,
                "bizType": "0",
                "applicantUserTaskId": null,
                "todoSource": "2",
                "systemName": null,
                "refreshTime": null,
                "remark": null
            },
            {
                "id": "f83f8981102c4d09a36e8f4bb18d7901",
                "todoId": "15ffceec73d8405391ead8430292e470",
                "userId": null,
                "userAccount": null,
                "userName": null,
                "userType": "2",
                "state": "2",
                "readTime": 1548691200000,
                "isCompleteRead": null,
                "isEnabled": "1",
                "isDeleted": "0",
                "title": "修复CSB账号N次密码输入错误后会永久锁定的问题",
                "content": null,
                "deadline": null,
                "applicantId": "zouhuangying",
                "applicantUser": "zouhuangying",
                "applicantName": "邹皇英",
                "handlerId": "chenweibin",
                "handlerUser": "chenweibin",
                "handlerName": "陈伟斌",
                "todoFile": null,
                "todoUrl": null,
                "todoType": null,
                "todoCate": null,
                "todoState": "3",
                "cc": "null",
                "crtTime": 1542186879000,
                "completeTime": null,
                "sid": null,
                "bizType": "0",
                "applicantUserTaskId": null,
                "todoSource": "2",
                "systemName": null,
                "refreshTime": null,
                "remark": null
            }
        ],
        "pagination": {
            "pageSize": 20,
            "pageNum": 1,
            "pages": 1,
            "total": 2
        }
    }
}
```

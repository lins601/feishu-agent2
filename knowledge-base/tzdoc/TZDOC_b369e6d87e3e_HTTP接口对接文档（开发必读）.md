---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn
url_hash: b369e6d87e3e
document_key: TZDOC_b369e6d87e3e
doc_id: tzdoc_v2-1fopbj68na1pn
title: HTTP接口对接文档（开发必读）
md_hash: 5ca8367b30293c5d
version: 1723443137
image_count: 0
crawled_at: 2026-06-11 16:08:15
---

# HTTP接口对接文档（开发必读）

- [1 总述](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn#d3e5ai)
- [2 入参例子说明](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn#cgcfua)
- [入参例子](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn#a1jb8t)
- [3 出参说明](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn#cmdmwk)
- [出参示例](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn#2yvgth)
- [4 代码demo](https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fopbj68na1pn#1gwe4g)


本文主要介绍如果要在 api 查询组件使用 HTTP 连接器，连接器所连接的接口需要遵循的规范

## 1 总述


| 栏目 | 说明 | 备注 |
| --- | --- | --- |
| 请求方式 | POST |  |
| 请求数据格式 | JSON |  |
| 返回数据格式 | JSON |  |
| Header | x-iac-token或x-auth-token |  |


## 2 入参例子说明


| 参数名 | 数据类型 | 是否必填 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| mode | String | 是 | 模式 | 参数名需固定：“mode” ;参数值：查询/翻译：SEARCH/TRANSLATE |
| name | String | 否 | 模糊查询字段 | 参数名不固定，也用于反向翻译，支持多个用英文逗号隔开 |
| code | String | 否 | 精确匹配字段 | 参数名不固定，用于精确匹配翻译，支持多个用英文逗号隔开 |
| pageIndex | Integer | 否 | 当前页码 | 参数名固定：“pageIndex” ,默认是1 |
| pageSize | Integer | 否 | 一页行数 | 参数名固定：“pageSize” .默认是20 |


### 入参例子


```
{
    "mode": "SEARCH",
    "name": " 张三"
}
```


### 3 出参说明


| 参数名 | 数据类型 | 是否必需 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| code | String | 是 | 参数名可不固定，实际存储字段 |  |
| name | String | 是 | 参数名可不固定，显示字段 |  |
| parentId | String | 否 | 树形数据字典项父级ID | 参数名不固定，树形时候需要返回 |
| pageIndex | Integer | 否 | 当前页码 | 参数名可固定：“pageIndex” ,默认是1 |
| pageSize | Integer | 否 | 一页行数 | 参数名不固定：“pageSize” .默认是20 |
| total | Integer | 否 | 总行数 | 参数名可不固定 |
| pages | Integer | 否 | 总页数 | 参数名可不固定 |


#### 出参示例


```
{
    "status": "0",
    "message": "success",
    "data": [
        {
            "code": "zhansan",
            "name": "张三"
        },
        {
            "code": " zhangsanfeng",
            "name": " 张三丰"
        }
    ]
}
```


### 4 代码demo


```
    @ApiOperation(value="API查询DEMO")
    @PostMapping("/test/api")
    public RestResp<List<ApiSearchRes>> testApi(@RequestBody @Validated ApiSearchReq req) {
        List<ApiSearchRes> data = baseCommonAppService.apiSearch(req);
        return RestResp.Builder.build(data);
    }


@Data
public class ApiSearchReq {
    /**
     * 模式：SEARCH/TRANSLATE/】
     * 固定参数
     */
    @ApiModelProperty(value = "模式：SEARCH/TRANSLATE")
    private String mode;
    /**
     * 固定参数
     */
    private Integer pageIndex;
    /**
     * 固定参数
     */
    private Integer pageSize;

    private String id;

    private String code;

    private String name;
}

@Data
public class ApiSearchRes {

    /**
     * 固定参数
     */
    private Integer pageIndex;

    /**
     * 固定参数
     */
    private Integer pageSize;

    /**
     * 固定参数
     */
    private Integer total;

    /**
     * 固定参数
     */
    private Integer pages;


    private String parentId;

    private String id;

    private String code;

    private String name;

}


    @Override
    public List<ApiSearchRes> apiSearch(ApiSearchReq req) {
        // 查询模式
        if ("SEARCH".equals(req.getMode())) {
            // 如果是 query 模式，根据配置的 name 字段进行模具查询,name 的值可能为空
            ApiSearchRes apiSearchRes = new ApiSearchRes();
            apiSearchRes.setId("1");
            apiSearchRes.setCode("编码");
            apiSearchRes.setName("名称");
            apiSearchRes.setParentId("-1");
            ApiSearchRes apiSearchRes2 = new ApiSearchRes();
            apiSearchRes.setId("2");
            apiSearchRes.setCode("编码2");
            apiSearchRes.setName("名称2");
            apiSearchRes.setParentId("1");
            return Arrays.asList(apiSearchRes, apiSearchRes2);
        }
        //  翻译模式
        if (StringUtilsEx.isNotEmpty(req.getCode())) {
            // 1、如果是 translate 模式，code 不为空，则为正向翻译，根据 code 进行精准查询，返回为空或者多个则报错
            // 2、针对数据字典过期失效、禁用的情况，业务方需要进行处理
            // 3、需要对 code 进行分割，分隔符为英文逗号，支持批量翻译
            ApiSearchRes apiSearchRes = new ApiSearchRes();
            apiSearchRes.setId("1");
            apiSearchRes.setCode("编码");
            apiSearchRes.setName("名称");
            apiSearchRes.setParentId("-1");
            return Collections.singletonList(apiSearchRes);
        }
        if (StringUtilsEx.isNotEmpty(req.getName())) {
            // 1、 如果是 translate 模式，name 不为空，则为反向翻译，根据 name 进行精准查询
            // 2、针对数据字典过期失效、禁用的情况，业务方需要进行处理
            // 3、需要对 name 进行分割，分隔符为英文逗号，支持批量反向翻译
            ApiSearchRes apiSearchRes = new ApiSearchRes();
            apiSearchRes.setId("1");
            apiSearchRes.setCode("编码");
            apiSearchRes.setName("名称");
            apiSearchRes.setParentId("-1");
            return Collections.singletonList(apiSearchRes);
        }
        return new ArrayList<>();

    }
```

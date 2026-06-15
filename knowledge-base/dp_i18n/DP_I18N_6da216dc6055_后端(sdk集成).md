---
source: MinDoc
project_name: DP_I18N
source_url: https://docs.cvte.com/docs/dp-i18n/dp-i18n-1h5qs5ugl2imn
normalized_url: https://docs.cvte.com/docs/dp-i18n/dp-i18n-1h5qs5ugl2imn
url_hash: 6da216dc6055
document_key: DP_I18N_6da216dc6055
doc_id: dp-i18n-1h5qs5ugl2imn
title: 后端(sdk集成)
md_hash: d93ebab4f2b2f812
version: 1777534973
image_count: 0
crawled_at: 2026-06-11 15:57:18
---

# 后端(sdk集成)

# 多语言集成指南


## 概述


本指南介绍如何集成 `dp-i18n-sdk` 实现接口响应数据的自动国际化翻译。


## 快速开始


### 1. 添加依赖


在 `pom.xml` 中添加：


```
<dependency>
    <groupId>com.cvte.i18n</groupId>
    <artifactId>dp-i18n-sdk</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```


### 2. 配置参数


在 `application.yml` 中配置：


```
dp:
  i18n:
    #  是否启用多租户,默认为fasle
    tenant: true
    #  业务系统标识（必填）
    #  一个产品线固定为一个，需要和多语言管理平台上的大类一致
    big: OMS
    # 服务端地址（必填）
    # sit:https://lcpsit.gz.cvte.cn/i18n
    # uat:https://lcpuat.gz.cvte.cn/i18n
    # pro:https://lcp.gz.cvte.cn/i18n
    server-url: https://lcpsit.gz.cvte.cn/i18n
    # 默认语言，默认为 zh-CN
    default-lang: zh-CN
    # 租户信息获取域名地址(未启用多租户则不用)
    # sit:https://lcpsit.gz.cvte.cn/tenant
    # uat:https://lcpuat.gz.cvte.cn/tenant
    # pro:https://lcp.gz.cvte.cn/tenant
    tenant-api-url: https://lcpsit.gz.cvte.cn/tenant
    iac:
      app-id: xxx
      app-secret: yyyy
```


### 3. 创建配置类


注册多语言处理器：


```
@Slf4j
@Configuration
public class I18nHandlerConfig {

    @Resource
    private DpI18nHandlerRegistry handlerRegistry;

    @Resource
    private YourCustomI18nHandler yourCustomI18nHandler;

    @PostConstruct
    public void registerHandlers() {
        // 注册处理器：HTTP方法 + 接口路径 + 处理器实例
        handlerRegistry.register("POST", "/your/api/path", yourCustomI18nHandler);
        log.info("国际化处理器注册完成，共注册 {} 个处理器", handlerRegistry.size());
    }
}
```


### 4. 实现处理器


创建自定义处理器：


```
@Slf4j
@Component
public class YourCustomI18nHandler implements DpI18nTranslateHandler {

    @Resource
    private DpI18nTranslateService translateService;

    @Override
    public Object handle(HttpServletRequest request, Object responseBody) {
        if (!(responseBody instanceof RestResp)) {
            return responseBody;
        }

        RestResp restResp = (RestResp) responseBody;
        Object data = restResp.getData();

        if (data == null) {
            return responseBody;
        }

        try {
            // 获取当前大类
            String currentBig = translateService.getCurrentBig(request);

            // 翻译业务数据
            translateYourData(currentBig, data);

        } catch (Exception e) {
            log.error("翻译数据时发生异常", e);
        }

        return responseBody;
    }

    private void translateYourData(String currentBig, Object data) {
        // 实现具体的翻译逻辑
    }
}
```


## 核心 API


### DpI18nTranslateService


```
// 获取当前请求的语言环境
String getCurrentBig(HttpServletRequest request);

// 翻译文本
String translate(String currentBig, String prefix, String category, String originalText);
```


参数说明：


- `currentBig`: 业务系统标识（如 “OMS”）
- `prefix`: 翻译键前缀（如 “CSB_DICT”, “LCP_ATTR”）
- `category`: 分类标识（可选，用于细分翻译范围）
- `originalText`: 原始文本


### DpI18nHandlerRegistry


```
// 注册处理器，支持路径变量
void register(String httpMethod, String apiPath, DpI18nTranslateHandler handler);

// 示例
handlerRegistry.register("GET", "/api/dict/list", dictListHandler);
handlerRegistry.register("POST", "/v1/form/{id}/layout", formLayoutHandler);
```


## 实践示例


### 示例 1：翻译字典数据


```
@Component
public class DictI18nHandler implements DpI18nTranslateHandler {

    @Resource
    private DpI18nTranslateService translateService;

    private static final String CSB_DICT_PREFIX = "CSB_DICT";

    @Override
    public Object handle(HttpServletRequest request, Object responseBody) {
        String currentBig = translateService.getCurrentBig(request);

        List<DictItem> dictList = extractDictList(responseBody);

        for (DictItem item : dictList) {
            String translatedName = translateService.translate(
                currentBig,
                CSB_DICT_PREFIX,
                item.getDictType(),
                item.getName()
            );
            item.setName(translatedName);
        }

        return responseBody;
    }
}
```


### 示例 2：翻译嵌套结构


```
private void translateNestedData(String currentBig, Object data) {
    // 翻译当前节点
    String originalName = getFieldValue(data, "name");
    if (originalName != null) {
        String translatedName = translateService.translate(
            currentBig,
            "LCP_ATTR",
            null,
            originalName
        );
        setFieldValue(data, "name", translatedName);
    }

    // 递归处理子节点
    List<?> children = getFieldValue(data, "children");
    if (children != null) {
        for (Object child : children) {
            translateNestedData(currentBig, child);
        }
    }
}
```


## 语言环境获取


SDK 按以下优先级获取语言环境：


- 请求头 `Accept-Language`
- 配置的默认语言


示例：


```
GET /api/dict/list
Accept-Language: en-US
```


## 常见问题


### 翻译不生效


检查项：


- 确认处理器已正确注册
- 检查接口路径是否匹配
- 验证语言包是否已上传到 dp-i18n 服务
- 查看日志确认是否有异常

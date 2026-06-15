---
source: MinDoc
project_name: HW_LCP
source_url: https://docs.cvte.com/docs/hw_lcp_deps_help/hw_lcp_deps_help-1gsrvupd0s6ce
normalized_url: https://docs.cvte.com/docs/hw_lcp_deps_help/hw_lcp_deps_help-1gsrvupd0s6ce
url_hash: 5a41bf969a04
document_key: HW_LCP_5a41bf969a04
doc_id: hw_lcp_deps_help-1gsrvupd0s6ce
title: 服务地址
md_hash: 59f154aed4cb513a
version: 1761892583
image_count: 0
crawled_at: 2026-06-11 15:58:16
---

# 服务地址

### 中间件


-

**数据库**


```
spring.datasource.url = jdbc:postgresql://xx.xx.xx.xx:5432/pgdb_yhs?&ssl=false
```

-

**Redis**


```
spring.redis.host = 10.10.15.78
spring.redis.port = 6379
```

-

**xxl-job**


```
csb.job.admin.addresses = http://10.10.15.78:8080/xxl-job-admin/
```

-

**注册中心地址**


```
eureka.client.serviceUrl.defaultZone = http://10.10.15.78:8761/eureka/
```

-

**es客户端访问**


```
spring.elasticsearch.rest.uris = http://10.10.15.78:9200
```

-

**rabbmitMQ**


```
spring.rabbitmq.url = http://10.10.15.78:5672
```

-

**nacos**


```
http://10.10.15.78:8848/nacos
```

-

**zipkin**


```
spring.zipkin.baseUrl = http://10.10.15.78:9411
```


### 应用服务


-

**lcp-app**


```
http://10.10.14.216:8081
```

-

**lcp-data**


```
http://10.10.14.216:8082
```

-

**lcp-hub**


```
http://10.10.14.216:8083
```

-

**lcp-legox**


```
http://10.10.14.216:8084
```

-

**lcp-gateway**


```
http://10.10.14.216:8085
```

-

**lcp-jwt**


```
http://10.10.14.216:8086
```

-

**lcp-log**


```
http://10.10.14.216:8087
```

-

**lcp-attachment**


```
http://10.10.14.216:8088
```

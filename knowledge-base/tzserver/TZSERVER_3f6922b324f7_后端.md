---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/back_3_0_0
normalized_url: https://docs.cvte.com/docs/tzserver_v2/back_3_0_0
url_hash: 3f6922b324f7
document_key: TZSERVER_3f6922b324f7
doc_id: back_3_0_0
title: 后端
md_hash: abba6a0a4d4b8f1e
version: 1749178164
image_count: 0
crawled_at: 2026-06-11 16:20:19
---

# 后端

- [增量升级(v2.9.0->v3.0.0)](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#ezgefq)
- [程序发布前执行项](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#cnex05)
- [1、Apollo配置](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#arhuag)
- [1.1 platform.tzHub](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#3rimwx)
- [1.2 CSB-LEGOX](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#3plk6z)
- [2、Nacos配置](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#biffjh)
- [3、数据库脚本](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#cm73ro)
- [业务库](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#c9g8yj)
- [配置库](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#97vvas)
- [数据初始化 - 集成流程](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#9dynjp)
- [数据初始化 - 菜单发布](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#1vzoqa)
- [业务库](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#7sqmc5)
- [配置库](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#5hjd53)
- [程序发布](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#7yk26c)
- [csb-legox参考pom.xml](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#b09yge)
- [应用运维同步](https://docs.cvte.com/docs/tzserver_v2/back_3_0_0#4z95aq)


## 增量升级(v2.9.0->v3.0.0)


### 程序发布前执行项


#### 1、Apollo配置


###### 1.1 platform.tzHub


仅仅天舟云本身升级需要配置，产品线只需要确认域名配置是否配置和正确性
产品线配置放在产品线各自的独有配置文件中，不建议修改公共配置platform.tzHub

```
lcp.hub.initialAuth = false
lcp.hub.app.IsGroupManage = 0
# 默认菜单根节点配置：页面Id；页面编码；页面名称
lcp.hub.root.page = TZC;TZC;TZC
```


###### 1.2 CSB-LEGOX


程序配置applicantion.yml修改配置

```
csb:
    mybatis:
        basePackage: com.cvte.csb.select.core.mapper,com.cvte.csb.sys.*.mapper,com.cvte.csb.sys.base.mapper,com.cvte.csb.dashboard.core.mapper,com.cvte.csb.serial.core.mapper,com.cvte.csb.dictionary.mapper,com.cvte.csb.log.mapper,com.cvte.csb.file.mapper,com.cvte.csb.wfp.core.mapper

```


csb-sys-base 有新增字段

```
ALTER TABLE sys_page ADD real_page_id varchar(64);
```


 **以下按需执行以下按需执行**

```
-- Oracle版本
ALTER TABLE sys_view_datasource ADD datasource_code varchar2(64) NULL;

COMMENT ON COLUMN sys_view_datasource.datasource_code IS '视图主编码';

ALTER TABLE sys_view_datasource ADD sys_database_type varchar2(64) NULL;

COMMENT ON COLUMN sys_view_datasource.sys_database_type IS '数据源类型:表单 数据库';

ALTER TABLE sys_view_datasource ADD biz_id varchar2(32) NULL;

COMMENT ON COLUMN sys_view_datasource.biz_id IS '业务归属来源ID';

ALTER TABLE sys_view_datasource ADD biz_type varchar2(255) NULL;

COMMENT ON COLUMN sys_view_datasource.biz_type IS '业务归属类型';

ALTER TABLE sys_view_datasource ADD app_id varchar2(64) NULL;

COMMENT ON COLUMN sys_view_datasource.app_id IS '应用ID';

ALTER TABLE sys_view_datasource ADD tenant_id varchar2(64) NULL;

COMMENT ON COLUMN sys_view_datasource.tenant_id IS '租户号';

ALTER TABLE sys_view_datasource ADD product_id varchar2(64) NULL;

COMMENT ON COLUMN sys_view_datasource.product_id IS '产品ID';

-- pg 2.0.9.7.7-SNAPSHOT 如果已经执行可以忽略
ALTER TABLE Sys_User_View_Field ADD COLUMN Width VARCHAR(64);
COMMENT ON COLUMN Sys_User_View_Field.Width IS '宽度';

ALTER TABLE Sys_View_Datasource ADD COLUMN Agg_Config VARCHAR;

COMMENT ON COLUMN Sys_View_Datasource.Agg_Config IS '聚合配置';
-- pg 2.0.9.7.7-SNAPSHOT 如果已经执行可以忽略


ALTER TABLE sys_view_condition ADD view_source_id varchar2(36) NULL;

COMMENT ON COLUMN sys_view_condition.view_source_id IS '视图资源ID';


ALTER TABLE sys_view ADD view_type varchar2(128) NULL;

COMMENT ON COLUMN sys_view.view_type IS '视图类型';

ALTER TABLE sys_view ADD user_id varchar2(128) NULL;

COMMENT ON COLUMN sys_view.user_id IS '用户id';


ALTER TABLE sys_user_view_field_order ADD sys_view_column_id varchar(32) NULL;
COMMENT ON COLUMN sys_user_view_field_order.sys_view_column_id IS '视图字段ID';
```


#### 2、Nacos配置


无

#### 3、数据库脚本


##### 业务库


无

##### 配置库


独立部署程序自动执行，不需要手动执行
只有多租户需要人工数据库执行：研发、行政

```
ALTER TABLE obj_http ADD tags varchar(256) NULL;
ALTER TABLE obj_http ADD todo_prefix varchar(256) NULL;
COMMENT ON COLUMN obj_http.todo_prefix IS '待办入口地址前缀(如https://xxx.gz.cvte.cn/patrol)';

ALTER TABLE state_machine_template ADD column IF NOT EXISTS workflow_type varchar(100) NULL;
COMMENT ON COLUMN state_machine_template.workflow_type IS '流程类型';
ALTER TABLE state_machine_template ADD column IF NOT EXISTS system_code varchar(200) NULL;
COMMENT ON COLUMN state_machine_template.system_code IS '系统编码';

ALTER TABLE obj_application ADD column IF NOT EXISTS table_name_prefix varchar(200) NULL;
COMMENT ON COLUMN obj_application.table_name_prefix IS '物理表前缀';

-- PostgreSQL 实现方案
ALTER TABLE tz_list_scheme ADD COLUMN db_id VARCHAR(64);
COMMENT ON COLUMN tz_list_scheme.db_id IS '数据源id';

CREATE TABLE obj_common_config (
                                   id varchar(32) NOT NULL,
                                   biz_id varchar(64) NULL,
                                   biz_type varchar(64) NULL,
                                   config_content text NULL,
                                   is_deleted varchar(1) NULL,
                                   is_enabled varchar(1) NULL,
                                   crt_user varchar(50) NULL,
                                   crt_host varchar(100) NULL,
                                   crt_name varchar(50) NULL,
                                   crt_time timestamp NULL,
                                   upd_user varchar(50) NULL,
                                   upd_name varchar(50) NULL,
                                   upd_host varchar(100) NULL,
                                   upd_time timestamp NULL,
                                   app_id varchar(64) NULL, -- 应用ID
                                   tenant_id varchar(64) NULL, -- 租户号
                                   product_id varchar(64) NULL, -- 产品id
                                   CONSTRAINT obj_common_config_pk PRIMARY KEY (id)
);

```


###### 数据初始化 - 集成流程


>


**目前仅仅研发需要执行，其他产品线不执行**


研发业务域 : sit环境

```
INSERT INTO obj_http
(id, http_name, http_code, http_desc, http_host, http_type, http_base_url, auth_type, auth_config, is_enabled, is_deleted, tenant_id, app_id, crt_user, crt_name, crt_time, crt_host, upd_user, upd_name, upd_time, upd_host, product_id, tags, todo_prefix)
VALUES('ee31f34422a046af8841cb7db7eea388', 'PDM', 'CPLM-OBJECT', '', 'cplmobjsit.gz.cvte.cn', 'https', NULL, 'iac', '{"appId":"2a1fe9c4-4f65-419c-bd4e-3aa18d3b6e58","appSecret":"836f4178-bbf6-47ba-905d-b1fc991138be"}', '1', '0', 'c9a8ac72-483a-44c5-892e-74428e7010c3', '2a1fe9c4-4f65-419c-bd4e-3aa18d3b6e58', 'yeduanwang', '叶端旺', '2025-01-21 16:15:23.803', '172.17.212.249', 'yeduanwang', '叶端旺', '2025-01-21 16:16:22.229', '172.17.212.249', NULL, 'integrated', 'https://cplmsit.gz.cvte.cn/pdm/flow/flow_center/oa');
```


不需该功能的业务域不执行

###### 数据初始化 - 菜单发布


研发业务域: sit

```
INSERT INTO obj_http
(id, http_name, http_code, http_desc, http_host, http_type, http_base_url, auth_type, auth_config, is_enabled, is_deleted, tenant_id, app_id, crt_user, crt_name, crt_time, crt_host, upd_user, upd_name, upd_time, upd_host, product_id, tags, todo_prefix)
VALUES('ee31f34422a046af8841cb7db7eea3881', 'PDM', 'MEMU_PUBLISH', '', 'cplmobjsit.gz.cvte.cn', 'https', NULL, 'iac', '{"appId":"2a1fe9c4-4f65-419c-bd4e-3aa18d3b6e58","appSecret":"836f4178-bbf6-47ba-905d-b1fc991138be"}', '1', '0', 'c9a8ac72-483a-44c5-892e-74428e7010c3', '2a1fe9c4-4f65-419c-bd4e-3aa18d3b6e58', 'yeduanwang', '叶端旺', '2025-01-21 16:15:23.803', '172.17.212.249', 'yeduanwang', '叶端旺', '2025-01-21 16:16:22.229', '172.17.212.249', NULL, 'menu_publish', NULL);
```


不需该功能的业务域不执行

##### 业务库


无

##### 配置库


### 程序发布


| 应用 | 分支 |
| --- | --- |
| lcp-app | 3.0.0-SNAPSHOT |
| lcp-data | 3.0.0-SNAPSHOT |
| lcp-hub | 3.0.0-SNAPSHOT |
| lcp-legox | 3.0.2-SNAPSHOT,重新构建,csb-sys-base(2.0.9.7-SNAPSHOT/3.0.2-SNAPSHOT)和csb-view(2.0.9.8-SNAPSHOT/3.0.2-SNAPSHOT)有改动;csb-data-dictionary(3.0.2-SNAPSHOT/2点几版本的不需要升级,仅仅研发系统使用) |


csb-legox的pom.xml建议增加poi的过滤，所有csb-sys-base相关的引用都需要

```
<dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-sys-base-admin</artifactId>
            <version>${csb.sys.base.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>httpclient</artifactId>
                    <groupId>org.apache.httpcomponents</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-sys-database</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-iac</artifactId>
                </exclusion>
                <!-- poi 过滤-->
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi-ooxml</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi-ooxml-schemas</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
```


##### csb-legox参考pom.xml


```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <groupId>com.cvte.csb</groupId>
    <artifactId>csb-legox</artifactId>
    <!-- 版本号 正式项目，必须引用release版本号 不可发布snapshot版本对外使用-->
    <version>2.0.3</version>
    <name>csb-legox</name>
    <packaging>jar</packaging>

    <developers>
        <developer>
            <name>wuhuashan</name>
            <email>wuhuashan@cvte.com</email>
        </developer>
    </developers>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <java.version>1.8</java.version>
        <project.location>${basedir}</project.location>
        <mapper.version>3.3.6</mapper.version>
        <csb.version>2.0.5</csb.version>
        <csb.snap.version>2.0.5-SNAPSHOT</csb.snap.version>
        <!-- 通用选择器组件版本号 -->
        <csb.select.version>2.2.2.4-SNAPSHOT</csb.select.version>
        <!-- 基础组件（含用户管理、组织管理、角色管理等模块）版本号 -->
        <csb.sys.base.version>2.0.9.12.CBA-SNAPSHOT</csb.sys.base.version>
        <!-- 视图组件版本号 -->
        <csb.view.version>2.0.9.8-SNAPSHOT</csb.view.version>
        <!-- 数据字典组件版本号 -->
        <csb.dictionary.version>2.0.9.4.1-SNAPSHOT</csb.dictionary.version>
        <csb.notice.version>0.0.0.1-SNAPSHOT</csb.notice.version>
        <csb.iac.version>1.1.7-SNAPSHOT</csb.iac.version>
        <zipkin.version>2.0.1-SNAPSHOT</zipkin.version>
    </properties>

    <!-- 依赖 -->
    <dependencies>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-iac</artifactId>
            <version>${csb.iac.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml-schemas</artifactId>
            <version>4.0.0</version>
        </dependency>

        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>4.0.0</version>
        </dependency>

        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi</artifactId>
            <version>4.0.0</version>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-sys-base-admin</artifactId>
            <version>${csb.sys.base.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>httpclient</artifactId>
                    <groupId>org.apache.httpcomponents</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-sys-database</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi-ooxml</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-sys-base-api</artifactId>
            <version>${csb.sys.base.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-sys-base-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-sys-base-sync</artifactId>
            <version>${csb.sys.base.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-sys-base-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-view-api</artifactId>
            <version>${csb.view.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-view-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-view-admin</artifactId>
            <version>${csb.view.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-web</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-sys-base-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-serial-admin</artifactId>
            <version>2.0.7.1-SNAPSHOT</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-web</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi-ooxml</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi-ooxml-schemas</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-jwt-sdk</artifactId>
            <version>${csb.version}</version>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-data-dictionary-admin</artifactId>
            <version>${csb.dictionary.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-data-dictionary-api</artifactId>
            <version>${csb.dictionary.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-wfp-admin</artifactId>
            <version>2.0.3</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-web</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-datapermission-admin</artifactId>
            <version>2.0.9.4-SNAPSHOT</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-web</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-datapermission-api</artifactId>
            <version>2.0.9.4-SNAPSHOT</version>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-file-admin</artifactId>
            <version>${csb.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-jetty</artifactId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-web</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-log-admin</artifactId>
            <version>${csb.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-web</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-dashboard-admin</artifactId>
            <version>${csb.snap.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-base</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-sys-base-core</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-log-core</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-web</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-validate</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.modelmapper</groupId>
                    <artifactId>modelmapper</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.projectlombok</groupId>
                    <artifactId>lombok</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.auth0</groupId>
                    <artifactId>java-jwt</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.oracle</groupId>
                    <artifactId>ojdbc6</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-config</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-jdbc</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-dashboard-api</artifactId>
            <version>${csb.snap.version}</version>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-select-admin</artifactId>
            <version>${csb.select.version}</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-select-api</artifactId>
            <version>${csb.select.version}</version>
        </dependency>

        <dependency>
            <groupId>com.cvte</groupId>
            <artifactId>portal-sdk</artifactId>
            <version>1.0</version>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-cloud</artifactId>
            <version>${csb.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-actuator</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-annotations</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-core</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-databind</artifactId>
                </exclusion>
                <exclusion>
                    <artifactId>httpclient</artifactId>
                    <groupId>org.apache.httpcomponents</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-zipkin</artifactId>
            <version>1.2.1.RELEASE</version>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb.trace</groupId>
            <artifactId>csb-zipkin</artifactId>
            <version>${zipkin.version}</version>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-dbmeta-admin</artifactId>
            <version>1.0.0</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-data-dictionary-core</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>tag-admin</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>tag-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <exclusions>
                <exclusion>
                    <artifactId>csb-jdbc</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
                <exclusion>
                    <artifactId>csb-log-core</artifactId>
                    <groupId>com.cvte.csb</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-notice-core</artifactId>
            <version>${csb.notice.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.cloud</groupId>
                    <artifactId>spring-cloud-starter-openfeign</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-jdbc</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-log-core</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-cache</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-notice-admin</artifactId>
            <version>${csb.notice.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.cloud</groupId>
                    <artifactId>spring-cloud-starter-openfeign</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-jdbc</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>com.cvte.csb</groupId>
            <artifactId>csb-notice-api</artifactId>
            <version>${csb.notice.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.cloud</groupId>
                    <artifactId>spring-cloud-starter-openfeign</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>com.cvte.csb</groupId>
                    <artifactId>csb-jdbc</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>Dalston.SR1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <repositories>
        <repository>
            <id>nexus</id>
            <name>nexus</name>
            <url>https://mvn.gz.cvte.cn/nexus/content/groups/public/</url>
        </repository>
        <repository>
            <id>nexus-releases</id>
            <name>CVTE-Release-Repository</name>
            <url>https://mvn.gz.cvte.cn/nexus/content/repositories/releases/</url>
            <releases>
                <updatePolicy>always</updatePolicy>
                <enabled>true</enabled>
            </releases>
        </repository>
        <repository>
            <id>nexus-snapshots</id>
            <name>CVTE-snapshots-Repository</name>
            <url>https://mvn.gz.cvte.cn/nexus/content/repositories/snapshots/</url>
            <snapshots>
                <updatePolicy>always</updatePolicy>
                <enabled>true</enabled>
            </snapshots>
        </repository>
        <repository>
            <id>jfrog-releases</id>
            <name>artifactory-releases</name>
            <url>https://artifactory.gz.cvte.cn:443/artifactory/SR_maven_releases_local/</url>
            <releases>
                <updatePolicy>daily</updatePolicy>
                <enabled>true</enabled>
            </releases>
        </repository>
        <repository>
            <id>jfrog-snapshots</id>
            <name>artifactory-snapshots</name>
            <url>https://artifactory.gz.cvte.cn:443/artifactory/SR_maven_snapshots_local</url>
        </repository>
    </repositories>

    <distributionManagement>
        <repository>
            <id>jfrog-releases</id>
            <name>artifactory-releases</name>
            <url>https://artifactory.gz.cvte.cn/artifactory/SR_maven_releases_local/</url>
        </repository>
        <snapshotRepository>
            <id>jfrog-snapshots</id>
            <name>artifactory-snapshots</name>
            <url>https://artifactory.gz.cvte.cn/artifactory/SR_maven_snapshots_local</url>
        </snapshotRepository>
    </distributionManagement>

    <build>
        <finalName>csb-legox</finalName>

        <resources>
            <resource>
                <targetPath>${project.build.directory}/classes</targetPath>
                <directory>src/main/resources</directory>
                <includes>
                    <include>**/*.properties</include>
                    <include>**/*.xml</include>
                    <include>**/*.yml</include>
                </includes>
            </resource>
            <resource>
                <targetPath>${project.build.directory}/classes</targetPath>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
                <includes>
                    <include>*.yml</include>
                </includes>
            </resource>
            <resource>
                <directory>src/main/resources/profiles/${profiles.active}</directory>
                <targetPath>${project.build.directory}/classes</targetPath>
                <excludes>
                    <exclude>profiles/**</exclude>
                </excludes>
                <filtering>true</filtering>
            </resource>
        </resources>

        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>

            <!-- Support our own plugin -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>2.0.1.RELEASE</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <mainClass>com.cvte.csb.legox.Bootstrap</mainClass>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>2.4.3</version>
                <configuration>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>

        </plugins>

    </build>

    <!-- 环境变量-->
    <profiles>
        <profile>

            <id>test</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <profiles.active>test</profiles.active>
            </properties>
        </profile>

        <profile>
            <id>dev</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <profiles.active>dev</profiles.active>
            </properties>
        </profile>

        <profile>

            <id>prod</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <profiles.active>prod</profiles.active>
            </properties>
        </profile>

    </profiles>

</project>

```


### 应用运维同步


```
   无
```

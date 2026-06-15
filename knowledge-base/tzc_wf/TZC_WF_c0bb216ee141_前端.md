---
source: MinDoc
project_name: TZC_WF
source_url: https://docs.cvte.com/docs/tzc_wf_integrate/tzc_wf_integrate-1gvpb6aqtjk46
normalized_url: https://docs.cvte.com/docs/tzc_wf_integrate/tzc_wf_integrate-1gvpb6aqtjk46
url_hash: c0bb216ee141
document_key: TZC_WF_c0bb216ee141
doc_id: tzc_wf_integrate-1gvpb6aqtjk46
title: 前端
md_hash: ffb7a473925aee94
version: 1773281540
image_count: 0
crawled_at: 2026-06-11 15:57:30
---

# 前端

### 前言：


- 1、我们提供了一个统一的集成流程的设计服务，第三方业务对接集成流程时，需要跳转到设计服务上做流程的设计。
- 2、在表单的详情上，需要展示当前流程的状态，为此我们提供了一个流程渲染组件。


下面文档就是说明了【跳转到集成流程设计器的地址拼接规则】和【如何集成审批流程图】


### 一、集成流程设计器地址：


>


{domain}/workspace/appId/feature/integrationWorkflow?featureType={featureType}&isHideInitTitle={isHideInitTitle}&title={title}&code={code}


参数表：


| 属性 | 必填 | 值 | 备注 |
| --- | --- | --- | --- |
| domain | 是 |  | 天舟云的域名 |
| featureType | 是 | INTEGRATION_WORKFLOW | featureType是流程类型，集成流程用INTEGRATION_WORKFLOW |
| code | 是 |  | 流程编码 |
| title | 否 |  | 流程名称 |
| isHideInitTitle | 否 | 1 | 是否隐藏默认标题，集成流程都需要isHideInitTitle=1 |


完整示例：
[https://cplmsit.gz.cvte.cn/workspace/appId/feature/integrationWorkflow?featureType=INTEGRATION_WORKFLOW&isHideInitTitle=1&title=IPS%20%E5%BC%95%E5%85%A5%E7%89%A9%E6%96%99%E5%BA%93_%E5%B7%A5%E4%BD%9C%E6%B5%81&code=WF_INPUT_PARTSPACE&ticket=3688ab33-db35-4299-bd87-bc9a50a8a114](https://cplmsit.gz.cvte.cn/workspace/appId/feature/integrationWorkflow?featureType=INTEGRATION_WORKFLOW&isHideInitTitle=1&title=IPS%20%E5%BC%95%E5%85%A5%E7%89%A9%E6%96%99%E5%BA%93_%E5%B7%A5%E4%BD%9C%E6%B5%81&code=WF_INPUT_PARTSPACE&ticket=3688ab33-db35-4299-bd87-bc9a50a8a114)


### 二、集成审批流程图


>


使用资源中心提供的资源进行对接：
资源名称：tz-render
导出点：workflowCharts


属性说明：


| 属性 | 必填 | 值 | 备注 |
| --- | --- | --- | --- |
| pageId | 是 |  | 流程实例id |
| apiConfig | 是 |  | { core?: string, path: string } ｜ core是服务名，path是获取流程图接口路径 |
| height | 否 |  | 流程图高度 |
| isNeedCardWrap | 否 |  | 是否需要外层容器，集成流程统一传false |


对接案例：


```
import React, { useEffect } from 'react';
import Loader, { ReactRemoteLoaderComponent } from '@cvte/resource-center-sdk';
// 天舟云审批日志
const TzcFlowChart = ReactRemoteLoaderComponent(
  new Loader({
    appName: 'tz-render',
    name: 'tz-render',
    env: document.getElementById('ENV')?.getAttribute('value')
  }),
  'workflowCharts',
  {
    useShared: true,
    mode: 'page'
  }
);

const FlowChart = (props) => {
  return (
    <div>
      <TzcFlowChart
        pageId={formInstanceId}
        height={400}
        isNeedCardWrap={false}
        apiConfig={{ core: '/pdm/api/process', path: '?_method_=TZC_WORKFLOW_CHART'}}
      />
    </div>
  );
};

export default observer(FlowChart);
```

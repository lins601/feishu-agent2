---
source: MinDoc
project_name: TZSERVER
source_url: https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame
normalized_url: https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame
url_hash: 7eae8341a955
document_key: TZSERVER_7eae8341a955
doc_id: mobile-lite-frame
title: 移动端轻量级框架
md_hash: 26fa44159efeb6d1
version: 1759988814
image_count: 1
crawled_at: 2026-06-11 16:20:08
---

# 移动端轻量级框架

- [集成移动端轻量级框架](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#ctv3sq)
- [安装依赖](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#6wj8wm)
- [cir-framework 版本为 3.x ?](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#cdjjrv)
- [cir-framework 版本为 4.x ?](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#23zuf0)
- [依赖说明](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#am9uaa)
- [工程改造](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#6cbjkj)
- [移除模块联邦配置，替换为资源中心提供运行时共享域](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#bktieq)
- [增加apollo配置，用于提供轻量级框架是否启动和必要的渲染参数](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#71kia)
- [增加apollo配置项](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#32l1l3)
- [更新server.js](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#e3yz9v)
- [增加middleware](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#9ju9sg)
- [ATzBootstrap.js](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#8mz1r5)
- [auth.js](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#t8at4)
- [增加框架入口引导启动文件](https://docs.cvte.com/docs/tzserver_v2/mobile-lite-frame#ah5tsl)


# 集成移动端轻量级框架


[图片1: null]


# 安装依赖


## cir-framework 版本为 3.x ?


`yarn add [@cvte](https://github.com/cvte)/cir-[framework@3.4.5](mailto:framework@3.4.5) [@cvte](https://github.com/cvte)/tz-[framework@0.1.0](mailto:framework@0.1.0) [@cvte](https://github.com/cvte)/resource-center-[sdk@1.7.42](mailto:sdk@1.7.42)`

## cir-framework 版本为 4.x ?


`yarn add [@cvte](https://github.com/cvte)/cir-[framework@4.1.4](mailto:framework@4.1.4) [@cvte](https://github.com/cvte)/tz-[framework@0.1.0](mailto:framework@0.1.0) [@cvte](https://github.com/cvte)/resource-center-[sdk@1.7.42](mailto:sdk@1.7.42)`

其中`[@cvte](https://github.com/cvte)/cir-[framework@4.1.4](mailto:framework@4.1.4)`需要依赖工程中的`elastic-apm-node`依赖，如果没有的话则需要安装：

`yarn add elastic-apm-[node@3.49.1](mailto:node@3.49.1)`

## 依赖说明


`[@cvte](https://github.com/cvte)/cir-framework`：CSB工程原有的框架套件
`[@cvte](https://github.com/cvte)/tz-framework`：天舟云新版框架套件
`[@cvte](https://github.com/cvte)/resource-center-sdk`：资源中心开发者套件

# 工程改造


## 移除模块联邦配置，替换为资源中心提供运行时共享域


原有的在`webpack_override.js`中，由于资源中心的机制，需要宿主增加「webpack模块联邦插件」用于共享依赖库；

现在资源中心SDK内部提供了运行时共享域的，所以可以将原有的「webpack模块联邦插件」声明移除；

```
// plugins.push(
  //   new webpack.container.ModuleFederationPlugin({
  //     name: 'csb',
  //     shared: {
  //       '@cvte/cir-framework': {
  //         eager: true,
  //         singleton: true,
  //       },
  //       '@cvte/wuli-antd': {
  //         eager: true,
  //         singleton: true,
  //       },
  //       'ag-grid-react': {
  //         eager: true,
  //         singleton: true,
  //       },
  //       '~modules/antd': {
  //         eager: true,
  //         shareKey: 'antd',
  //         // singleton: true,
  //         version: '4.23.1',
  //       },
  //       axios: {
  //         eager: true,
  //         singleton: true,
  //       },
  //       mobx: {
  //         eager: true,
  //         singleton: true,
  //       },
  //       react: {
  //         eager: true,
  //         singleton: true,
  //         strictVersion: true,
  //         requiredVersion: packageJson.dependencies.react,
  //       },
  //       'react-dom': {
  //         eager: true,
  //         singleton: true,
  //         strictVersion: true,
  //         requiredVersion: packageJson.dependencies.react,
  //       },
  //     },
  //   })
  // );
```


同时需要增加从资源中心SDK内部提供的运行时共享域的工具类，此处建议单独放置在一个文件中，我们建议取名为`rcShared.ts`，如下示例中我们提供了普通CSB工程需要用到的共享依赖库：

```
import * as React from 'react';
import * as ReactDOM from 'react-dom';
import * as ReactIntl from 'react-intl';
import * as antd from 'antd';
import axios from 'axios';
import * as mobx from 'mobx';
import { ShareScopeGenerator } from '@cvte/resource-center-sdk';

const scope = new ShareScopeGenerator({
  packerAppName: 'lcp-system', // 此处建议使用宿主应用资源的名称
});
scope.addDependency({
  key: 'react',
  version: '17.0.2',
  dependency: React,
  loaded: 1,
  eager: true,
});
scope.addDependency({
  key: 'react-dom',
  version: '17.0.2',
  dependency: ReactDOM,
  loaded: 1,
  eager: true,
});
scope.addDependency({
  key: 'react-intl',
  version: '3.12.1',
  dependency: ReactIntl,
  loaded: 1,
  eager: true,
});
scope.addDependency({
  key: 'antd',
  version: '4.24.8',
  dependency: antd,
  loaded: 1,
  eager: true,
});
scope.addDependency({
  key: 'axios',
  dependency: axios,
  loaded: 1,
  eager: true,
});
scope.addDependency({
  key: 'mobx',
  version: '5.0.3',
  dependency: mobx,
  loaded: 1,
  eager: true,
});

export default scope;

```


有了上面的`rcShared.ts`，我们在如下示例中展示如何来按需使用它：

```
import Loader, { ReactRemoteLoaderComponent } from '@cvte/resource-center-sdk';
import {
  CENV,
  lcpLoaderName,
  lcpAppName,
} from '~app/common/tools/injectConfig';
import shared from '~app/common/rcShared';

const CSBDetail = ReactRemoteLoaderComponent(
  new Loader({
    appName: lcpAppName,
    name: lcpLoaderName,
    env: CENV,
  }),
  'LCPDetailTemplate',
  {
    useShared: true,
    mode: 'page',
    // 将共享的依赖注入到资源组件的加载参数中
    sharedScope: shared.toSharedScope(),
  }
);

export default CSBDetail;

```


## 增加apollo配置，用于提供轻量级框架是否启动和必要的渲染参数


### 增加apollo配置项


我们建议在`tianzhou.env`命名空间（如果你有天舟云独立的命名空间请放在对应的空间下）中增加如下键值对配置项：

```
tzBootstrapConfig = {"isOpen":"1","entry":"/app/modules/FormTemplate/TZBootstrap.ts","cdn":[{"key":"react","type":"js","src":"https://cdn1.cvte.com/statics/tz-app/js/react@17.0.2.min.js"},{"key":"react-dom","type":"js","src":"https://cdn1.cvte.com/statics/tz-app/js/react-dom@17.0.2.min.js"},{"key":"react-intl","type":"js","src":"https://cdn1.cvte.com/statics/tz-app/js/react-intl@3.12.1.min.js"},{"key":"antd","type":"js","src":"https://cdn1.cvte.com/statics/tz-app/js/antd-with-locales@4.24.14.min.js"},{"key":"antdStyle","type":"css","src":"https://cdn1.cvte.com/statics/tz-app/js/antd@4.24.14.min.css"}, { "key":"axios", "type":"js","src":"https://cdn1.cvte.com/statics/tz-app/js/axios@0.17.1.min.js" }, {"key":"mobx", "type":"js","src":"https://cdn1.cvte.com/statics/tz-app/js/mobx@5.0.3.umd.min.js"},{"key": "moment","type": "js","src": "https://cdn1.cvte.com/statics/tz-app/js/moment-with-locales@2.29.1.min.js"} ] }
```


配置项参数说明：

```
tzBootstrapConfig: {
  isOpen: '1', // 是否开启天舟云轻量级框架，0关闭，1开启
  entry: 'src/index.tsx', // 框架入口引导文件路径
  cdn:[{ // 轻量级框架使用了cdn的方式来加载第三方依赖库
    key: '', // 依赖包key
    type: 'js', // 依赖包类型，js或css
    src: 'https://cdn/react.min.js', // cdn地址
  }]
}
```


### 更新server.js


由于apollo配置项与CSB工程的环境变量需要一一对应，所以还需要在`config/server.js`中做如下新增：

```
module.exports = {
    // 其他内容
    // 2024.11.11 增加天舟云轻量级框架配置数据 --yuanzihan
    /**
    * tzBootstrapConfig:{
    *  isOpen: '1', // 是否开启天舟云轻量级框架，0关闭，1开启
    *  entry: 'src/index.tsx', // 框架入口引导文件路径
    *  cdn:[{
    *    key: '', // 依赖包key
    *    type: 'js', // 依赖包类型，js或css
    *    src: 'https://cdn/react.min.js', // cdn地址
    *  }]
    * }
    */
    tzBootstrapConfig: process.env.tzBootstrapConfig,
};

```


### 增加middleware


#### ATzBootstrap.js


由于在访问的过程中我们在请求头增加了自定义参数，用来判断当前环境是否应该使用移动端框架来渲染，所以在工程中需要再增加一个移动端渲染适配器中间件，用于拦截请求判断是否应该使用移动端方式渲染

我们建议在`app/middleware`目录下增加`ATzBootstrap.js`文件，内容如下：
（为什么文件名要用`A`开头，是为了在cir-framework框架层遍历读取文件时能够保持在最早读取）

```
/* eslint-disable max-len */
const {
  tzBootstrapRenderUsedGenerator,
} = require('@cvte/tz-framework/middlewares/useTzBootstrapRender');

// 该文件命名为 AtzBootstrap.js 原因是koa中间件注入时按照字母排序，该文件需要在较早的位置执行，所以命名为 A 开头的文件
module.exports = tzBootstrapRenderUsedGenerator({
  canUseTzBootstrapRender: (ctx) => {
    const UA_MOBILE = [
      'AppleWebKit.*Mobile',
      'Android',
      'BlackBerry',
      'IEMobile',
      'iPhone',
      'iPad',
      'iPod',
      'MIDP',
      'SymbianOS',
      'NOKIA',
      'SAMSUNG',
      'LG',
      'NEC',
      'TCL',
      'Alcatel',
      'BIRD',
      'DBTEL',
      'Dopod',
      'PHILIPS',
      'HAIER',
      'LENOVO',
      'MOT-',
      'Nokia',
      'SonyEricsson',
      'SIE-',
      'Amoi',
      'ZTE',
    ];
    const isMobile = (ua) => new RegExp(UA_MOBILE.join('|')).test(ua);
    // 2024.12.19 健壮场景，在本地开发环境下，referer为空，所以需要使用ctx?.url，在线上环境下，referer不为空，所以需要使用ctx.request.header.referer，此处优先使用referer --yuanzihan
    const url = `${ctx.request.header.referer || ctx?.url}`;
    const [, searchParams] = url.split('?');
    // 在oms系统下，判断是否是低代码页面，如果是低代码页面，不使用tz-bootstrap-render
    // 判断低代码页面的条件是：url的search参数中包含appId或者classId，并且不包含apis则认为是低代码页面
    const result =
      // 2024.12.19 修复bug，在本地开发环境下，searchParams为空，所以需要使用`${searchParams}`，在线上环境下，searchParams不为空，所以需要使用`${searchParams}` --yuanzihan
      (`${searchParams}`.includes('appId') ||
        `${searchParams}`.includes('classId')) &&
      isMobile(ctx.header['user-agent']);
    console.log(
      '🚀 ~ ctx.header url, searchParams,  canUseTzBootstrapRender:',
      url,
      searchParams,
      result
    );
    return result;
  },
});

```


#### auth.js


由于轻量级框架会调用渲染用户登陆页，考虑到我们没有再依赖原有的框架加载登陆页面，所以为此我们单独增加了鉴权的中间件用于拦截轻量级登陆页的请求接口

我们建议在`app/middleware`目录下增加`auth.js`文件，内容如下：

```
const { request: requestHelper, redisHelper, iacService, messageHelper } = require('@cvte/cir-framework/server');
const { isDevelopment, needSecureAndSameSite } = require('@cvte/cir-framework/common/tools');
const appConfig = require('@cvte/cir-framework/config').default.app;
const { authMiddlewareGenerator } = require('@cvte/tz-framework/middlewares/auth');
const apiConfig = require('../../config/server');

// 如果开启了tzBootstrapConfig，且是移动端，且配置了轻量级框架entry，则拦截登录、登出、获取用户信息接口
module.exports = authMiddlewareGenerator({
  requestHelper,
  redisHelper,
  iacService,
  messageHelper,
  isDevelopment,
  needSecureAndSameSite,
  appConfig,
  apiConfig,
});

```


## 增加框架入口引导启动文件


在上面的apollo配置中，我们已经配置了`tzBootstrapConfig`内容，其中`entry`字段就是指定根目录下哪一个文件用来做为轻量级框架的引导启动，我们建议将该文件命名为`TZBootstrap.ts`，如下就是关于实现它的示例：

⏰ 关于登陆页面资源组件：
如果你的天舟云版本是2.9及其以上，请使用`tz-app`@`1.0.8`的资源版本；否则请使用`cir-login`@`1.0.1`的资源版本；

```
import '../App/style.less'; // 此处是宿主系统中所使用的基础样式，一般是在global.js中引入的样式，需要将其在此处引入
import GlobalRootApp from '@cvte/resource-center-sdk/src/loader/rootAppController'; // 资源全局根宿主应用管理实例
import { ShareScopeGenerator } from '@cvte/resource-center-sdk/src/lib/shareScopeGenerator'; // 资源运行时共享域管理类
import { Loader } from '@cvte/resource-center-sdk/src/loader'; // 资源加载类
// eslint-disable-next-line max-len
import { ReactRuntimeRemoteLoaderComponent } from '@cvte/resource-center-sdk/src/lib/components/ReactRuntimeRemoteLoaderComponent'; // 资源react远程加载组件的运行时版本
import { Bootstrap, Booter } from '@cvte/tz-framework'; // 天舟轻量级框架引导类和启动器类型
import {
  TZRenderContainer,
  RCRenderTemplate,
} from '@cvte/tz-framework/TZRenderContainer'; // 天舟轻量级框架渲染容器

const FrameworkBootstrap = new Bootstrap(); // 初始化框架启动器实例

// 启动执行内容
const tianzhou = (_booter?: Booter) => {
  // 从window中获取当前CDN加载的实例
  const depSource = globalThis;
  const { React, ReactDOM, ReactIntl, antd, mobx, axios } = depSource as any;

  if (!React || !ReactDOM) {
    throw new Error(
      '全局依赖中不包含React和ReactDOM，请确保全局CDN依赖中包含React和ReactDOM'
    );
  }

  if (!_booter) {
    throw new Error('框架启动器Booter实例未初始化！');
  }

  const scope = new ShareScopeGenerator({
    packerAppName: 'lcp-system',
  });

  // 在原有的逻辑中，需要有一层XHR的劫持修改api请求的地址，在其前面加上当前应用前缀
  const customizeOpen = (booter: Booter) => {
    const { routerPrefix } = booter.runtimeContext.getExtra() || {};
    if (!(XMLHttpRequest.prototype as any).nativeOpen) {
      (XMLHttpRequest.prototype as any).nativeOpen =
        XMLHttpRequest.prototype.open;
      const _customizeOpen = function (method, url, ...params) {
        if (
          routerPrefix !== '/pages' &&
          // 热更新不处理
          url.indexOf('hot-update.json') < 0 &&
          // 跨域直接请求不处理
          url.indexOf('https://') < 0 &&
          url.indexOf('http://') < 0 &&
          // apm 上报不处理
          url.indexOf('/rum/events') < 0
        ) {
          // eslint-disable-next-line no-param-reassign
          url = `${routerPrefix}_${url.slice(1)}`;
        }
        // url = `/cir_framework_${url.slice(1)}`
        this.nativeOpen(method, url, ...params);
        // this.setRequestHeader('x-forwarded-protocol', window.location.protocol);
      };

      XMLHttpRequest.prototype.open = _customizeOpen;
    }
  };
  customizeOpen(_booter);
  // 调用启动器提供的身份校验api
  const authMe = _booter.auth.init();

  // 天舟云渲染容器API
  TZRenderContainer(_booter, {
    // 准备第三方依赖库生命周期回调
    onPrepareThridDeps: () => {
      const deps = [
        {
          key: 'react',
          version: '17.0.2',
          dep: React,
          loaded: 1,
          eager: true,
        },
        {
          key: 'react-dom',
          version: '17.0.2',
          dep: ReactDOM,
          loaded: 1,
          eager: true,
        },
        {
          key: 'react-intl',
          version: '3.12.1',
          dep: ReactIntl,
          loaded: 1,
          eager: true,
        },
        {
          key: 'antd',
          version: '4.18.9',
          dep: antd,
          loaded: 1,
          eager: true,
        },
        {
          key: 'mobx',
          version: '5.0.3',
          dep: mobx,
          loaded: 1,
          eager: true,
        },
        {
          key: 'axios',
          dep: axios,
          loaded: 1,
          eager: true,
        },
      ] as any[];
      deps.forEach((item) => {
        const { key, dep, ...oItem } = item || {};
        scope.addDependency({
          ...(oItem || {}),
          key,
          dependency: dep,
        });
      });
      return {
        deps,
      };
    },
    // 准备第三方依赖库之后的装载生命周期回调
    onMount: (booter: Booter) => {
      const { lcpAppName } = booter.runtimeContext.getExtra() || {};
      // 2022.08.23 设置根宿主容器资源名称 --yuanzihan
      GlobalRootApp.setRootApp({
        name: lcpAppName || 'lcp-2-9-app',
        sharedScope: scope.toSharedScope(),
      });
    },
    // 装载之后的准备本地国际化内容生命周期回调
    onPrepareI18NLocale: () => {},
    // 准备本地国际化内容之后的准备内容渲染生命周期回调
    onPrepareContentRenderer: async (booter: Booter) => {
      const resp = await authMe.then();
      console.log('🚀 ~ onPrepareContentRenderer: ~ resp:', resp);

      /**
       * 主内容渲染逻辑
       *
       * @return {*}
       */
      const mainContent = () => {
        // 获取用户信息成功时，根据URL的资源参数跳转到不同的渲染模版
        const urlParams = {};
        new URLSearchParams(globalThis.location.search).forEach((v, k) => {
          urlParams[decodeURIComponent(k)] = decodeURIComponent(v);
        });
        const [pageTypeFlag] = `${globalThis.location.pathname}`
          .split('/')
          .slice(-1);

        const { resourceName, exposeName, ...props } = (urlParams || {}) as any;
        // 在search参数中找到对应的资源参数，根据资源参数渲染对应的页面
        if (resourceName && exposeName) {
          let [sourceName, exposesKey] = [resourceName, exposeName];
          if (
            [
              'lcp-form-components',
              'lcp-layout-components',
              'lcp-page-components',
              'cir-csb-common-view',
              'cir-csb-generator',
              'lcp-data-object',
              'cir-datart',
              'viewTable',
            ].includes(resourceName)
          ) {
            sourceName = 'tz-render';
          }
          if (
            ['cir-formtransfer', 'cir-state-flow-manager'].includes(sourceName)
          ) {
            const splitToCannal = (str: string): string => {
              const reg = /-(\w)/g;
              // eslint-disable-next-line no-param-reassign
              str = str.replace(reg, ($: string, $1: string) => {
                return $1.toUpperCase();
              });
              return str;
            };
            exposesKey = `${splitToCannal(sourceName)}.${exposeName}`;
            sourceName = 'tz-design';
          }
          return RCRenderTemplate(
            booter,
            {
              Loader,
              ReactRemoteLoaderComponent: ReactRuntimeRemoteLoaderComponent,
              resource: sourceName,
              expose: exposesKey,
              extra: {
                deps: {
                  react: React,
                  reactDom: ReactDOM,
                  antd,
                },
              },
            },
            {
              ...props,
              ref: {},
              booter,
            }
          );
        }
        // 在path中找到对应的资源参数，根据资源参数渲染对应的页面
        if (['detail', 'list'].includes(pageTypeFlag)) {
          const exposesKey = pageTypeFlag === 'detail' ? 'Detail' : 'List';
          return RCRenderTemplate(
            booter,
            {
              Loader,
              ReactRemoteLoaderComponent: ReactRuntimeRemoteLoaderComponent,
              resource: 'tz-render',
              expose: exposesKey,
              extra: {
                deps: {
                  react: React,
                  reactDom: ReactDOM,
                  antd,
                },
              },
            },
            {
              ...props,
              ref: {},
              booter,
            }
          );
        }

        // 如果URL中未能找到对应的资源参数，则返回空页面
        const { Empty } = booter.runtimeContext.getThridParty('antd') || {};
        return React.createElement(Empty, {
          description: `当前URL中未能找到对应的内容（${globalThis.location.href}）`,
          style: { marginTop: '25vh' },
        });
      };

      // 获取用户信息失败时，跳转到登录页
      if (!resp) {
        return () => {
          return RCRenderTemplate(
            booter,
            {
              Loader,
              ReactRemoteLoaderComponent: ReactRuntimeRemoteLoaderComponent,
              resource: 'tz-app', // 如果没有升级到天舟云2.9及其以上，需要使用cir-login@1.0.1的资源版本
              expose: 'MobileLiteLogin',
              extra: {
                deps: {
                  react: React,
                  reactDom: ReactDOM,
                  antd,
                },
              },
            },
            {
              booter,
              onAfterSuccessRender: () => {
                return mainContent();
              },
            }
          );
        };
      }

      return mainContent;
    },
  });
};
//启动器开始渲染启动执行内容
FrameworkBootstrap.render(tianzhou);

```

---

# 文档图片附录


---

## 图片1: null

![null](data:image/jpeg;base64,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7QEREBERAREQEREFbjOJSYZFTyRxsfwk2RzXvDNMjnbzoPe86g8bYXkCOkkJktwWeRrbm4BzXPI36X38yvJYYp2ZJo2SNvez2gi/1rF9LTycJngjdwoAfdgOe26/Sgo49ro5JWsFHI4SzBkIYbucwsY4uI5j7oNOgEpWbTvp6ShrG0wEdSHvdG+RofkEbnA3vYE5Veupad7szoI3G4dcsB1G4/UvH0tPJlzwROy2tdgNrbv4oKM7XwZnNbSyXucuZ7W6AuDi655J5DtDv067ejaGeVr5I6djI43SGznZnPa0xgaaZSQ+9uawV26kp3580ER4QgvuwcojcT0rGWhpZ3h8kDHOzZr23nTf07hv6Ag3ovUQF5Yb7L1EBERAREQEREFJsp8Fz+f1f371dqk2U+C5/P6v796u0BERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERBS418ObPeeyf00yulS418ObPeeyf00yukBERAREQEREBERAREQEREBERAREQEREBERAREQEREFHH8e6j6Mi+9kV4qOP491H0ZF97IrxAREQEREBERBSYd8b8Z83pf9xXapMO+N+M+b0v+4rtAREQEREBERAREQEREBERAREQEREBERAREQEREBERBSbKfBc/n9X9+9XapNlPgufz+r+/ertAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQUG0lQyjr8DrJmymCCseZHRxOkygwStBIaCd5A+tbON+DfKVfYJ/YV0lkFLxvwb5Sr7BP7Ccb8G+Uq+wT+wrqyWQUvG/BvlKvsE/sJxvwb5Sr7BP7CurJZBS8b8G+Uq+wT+wnG/BvlKvsE/sK6slkFLxvwb5Sr7BP7Ccb8G+Uq+wT+wrqyWQUvG/BvlKvsE/sJxvwb5Sr7BP7CurJZBS8b8G+Uq+wT+wvDtjggcGmaqzEXA7hnuf8As61d2XM1+C4nPtlSYlHiojiijIZT8GSMgLcwJvvdff1DoQTON+DfKVfYJ/YTjfg3ylX2Cf2FdWSyCl434N8pV9gn9hON+DfKVfYJ/YV1ZLIKXjfg3ylX2Cf2E434N8pV9gn9hXVksgpeN+DfKVfYJ/YTjfg3ylX2Cf2FdWSyCl434N8pV9gn9hON+DfKVfYJ/YV1ZLIKXjfg3ylX2Cf2E434N8pV9gn9hXVksg5zC66HFNsKqrpWzmBuHxRl8lPJGM3CPNhmAvoQukXi9QEREBERAREQcw7FKbCNrcTfWioYyenp+DcymkkDsvCX1a07rj0qZxvwb5Sr7BP7CuksgpeN+DfKVfYJ/YTjfg3ylX2Cf2FdWSyCl434N8pV9gn9hON+DfKVfYJ/YV1ZLIKXjfg3ylX2Cf2E434N8pV9gn9hXVksgpeN+DfKVfYJ/YTjfg3ylX2Cf2FdWSyCl434N8pV9gn9hON+DfKVfYJ/YV1ZLIKXjfg3ylX2Cf2F4NscEJIE1USDY2oZ9P8AsV04HKctr20vuXN7MYNiWG4niU1Xioq2VEt3s4IttJYHMNTYWNrdQ6EEvjfg3ylX2Cf2E434N8pV9gn9hXVksgpeN+DfKVfYJ/YTjfg3ylX2Cf2FdWSyCl434N8pV9gn9hON+DfKVfYJ/YV1ZLIKXjfg3ylX2Cf2E434N8pV9gn9hXVksgpeN+DfKVfYJ/YTjfg3ylX2Cf2FdWSyCl434N8pV9gn9hON+DfKVfYJ/YV1ZLIKTZIl+CvlySMbLWVMjBJG5hLXTPINnAEXBvqrxEQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBRZPhSn/YyfxYpSiyH/ilOL/8AJk0v1s6/9EEpERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAUKleyOsq4nua15kDw0nUtLQAfJcH0FTVqmpoKhuWaFkg1HLaDvFv4EoM8zfzh6Uzt/OHpUc4bQuvekhN735A1vf2j6Shw2hN70kJvv5A6/WfSgkZ2/nD0pnb+cPSo/e2h+aQ/5B/wC86d7aH5pD/kCCRnb+cPSmdv5w9KjjDaEWtSQi27kDq9Q9CDDaEWtSQi1rcgaWt7I9AQSM7fzh6Uzt/OHpUcYbQttajhFrW5A0ta38rfQEGG0LbWo4Ra1uQOa3sj0BBIzt/OHpTO384elRxhtCLWo4dN3IHV6h6E72UPzOH/IEEjO384elM7fzh6VH72UPzOH/ACBDhlCb3o4TffyB1+s+lBIzt/OHpTM384elRzhtC696SE3vfkDW9/aPpKHDaF170cJve/IGt73/AJnekoJGdv5w9KZ2/nD0qOcNoXXvSQm978gc9/aPpKHDaE3vSQ67+QOv1n0oJGdv5w9KyUU4bQnfRw6/oBahD3DVxcBn4GZxY6O5LWm1w4Dm3dQ1vvQT0REBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAUWQ/wDFKcX3wyaX62c1/wDQ/VzylFkP/FIBffDJpfrZzX/0PlHOEpERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAUSuF5KPS9qgc1/yXdR/0UtRK7+8o/OB/K5BLREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBRZD/xSAX/5Mml+tnX/AKejnlKJK62KUw15UUg/e3r/ANEEtERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQERVm0eKS4NgFXiMMTJZIGXax5IBNwNSPKgs0XEHaXaYEjubCPtJfUnGbaf5thH2kvqXF2hdd/u6crbbXbouI4zbT/ADbCPtJfUnGbaf5thH2kvqTtC67/AHMpb7XbouI4zbT/ADbCPtJfUnGbaf5thH2kvqTtC67/AHMpb7XbouI4zbT/ADbCPtJfUnGbaf5thH2kvqTtC67/AHMpb7XbouI4zbT/ADbCPtJfUtNZthtJRUc1VJSYU5kLC9zWyS3IHRopi/3aZwiv3RN1tojGaXeovGm7QekL1djnEREBERBBxTGcPwWCObEalsDJH5GEtJzOsTYAA8wKrOPezXhIfYyeyo225s/BbfPj9zIqmWdkEeeWTK3de539C8q+X+q72kURTjjDuu90i2omqZwX/HvZrwkPsZPZTj3s14SH2MnsrnW1sDmh3DtaCSAHOym436FDW07ZHxmpjDowC8GQDLc2F/rXJ2vXs9f+Ojs+nc6Lj3s14SH2MnsqNV7b7OvkpS3EL5Zg42gkNhld+iqV1fTsmdC+oax7ReznW5gT/EeleSV8EckLHTi8zS5hzaFoFyb9Go9Kdr1//P1/4dn07nSce9mvCQ+xk9lOPezXhIfYyeyue7rgsT3THYEAnhBoTu507qhIaRUMs69vdBrbfbVO2K9nqdn0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6yrxAREQEREFHH8e6j6Mi+9kV4qOP491H0ZF97IrxAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQcrtv7/BfPj9zIqxdXjOB0mOQwx1T52cBJwsb4JTG4OsW7x1Eqs4jYf4Qxbtz0FOiuOI2H+EMW7c9OI2H+EMW7c9BTorjiNh/hDFu3PTiNh/hDFu3PQU6K44jYf4Qxbtz04jYf4Qxbtz0FOiuOI2H+EMW7c9OI2H+EMW7c9Bz83wlg/wBJRfwcvog3LnqfYrDaesp6o1OITOp5BLG2are9uYXsSOfeuiQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERBS418ObPeeyf00yulS418ObPeeyf00yukBERAREQEREBERAREQEREBERAREQEREBERAREQEREFHH8e6j6Mi+9kV4qOP491H0ZF97IrxAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERBS418ObPeeyf00yulXYvhBxQ0kkdbPRzUkxlilhawm5Y5hBD2kWs48yi95cW8asR7PTfhILtFSd5cW8asR7PTfhJ3lxbxqxHs9N+Egu0VJ3lxbxqxHs9N+EneXFvGrEez034SC7RUneXFvGrEez034Sd5cW8asR7PTfhILtFSd5cW8asR7PTfhJ3lxbxqxHs9N+Egu0VJ3lxbxqxHs9N+EneXFvGrEez034SC7RUneXFvGrEez034Sd5cW8asR7PTfhILtFSd5cW8asR7PTfhJ3lxbxqxHs9N+Egu0VJ3lxbxqxHs9N+EneXFvGrEez034SC7RUneXFvGrEez034Sd5cW8asR7PTfhILtFSd5cW8asR7PTfhJ3lxbxqxHs9N+Egu0VJ3lxbxqxHs9N+EneXFvGrEez034SC7RUneXFvGrEez034Sd5cW8asR7PTfhIPI/j3UfRkX3sivFU4dgklFiUuIVOJ1NdPLC2G8zI2hrWku0DGt53HerZAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERB//9k=)



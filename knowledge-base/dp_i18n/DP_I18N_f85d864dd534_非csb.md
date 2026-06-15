---
source: MinDoc
project_name: DP_I18N
source_url: https://docs.cvte.com/docs/dp-i18n/dp-i18n-1h7ckr2iuf005
normalized_url: https://docs.cvte.com/docs/dp-i18n/dp-i18n-1h7ckr2iuf005
url_hash: f85d864dd534
document_key: DP_I18N_f85d864dd534
doc_id: dp-i18n-1h7ckr2iuf005
title: 非csb
md_hash: 020baa60dbbd4126
version: 1773740976
image_count: 0
crawled_at: 2026-06-11 15:57:19
---

# 非csb

# 安装包


- 根据自己域进行安装语言包，比如自己域是 xxx，安装的包是 `[@cvte](https://github.com/cvte)/dp-i18n-xxx`;
- 安装翻译sdk，`[@cvte](https://github.com/cvte)/intl-sdk`
- 安装国际化库，`react-intl`


# 前端入口调整


安装依赖 `react-intl`, 前端入口增加增加 `IntlProvider` 包裹


```
// 引入国际化
import { IntlProvider } from 'react-intl';
// 引入语言库，用自己的语言包
import { zhCN, enUS  } from '@cvte/dp-i18n-xxx';
// locale当前语言，messages是语言包集合
<IntlProvider locale={'zh-CN'} messages={{ 'zh-CN': zhCN, 'en_US': enUS }}> 你的根组件 </IntlProvider>
```


# 组件文本调整


```
import { createIntlByCache } from '@cvte/intl-sdk';

const intl = createIntlByCache();
// 比如原来固定 '展开'，改成用 intl.formatMessage的方式
console.log(intl.formatMessage({ id: '翻译的code', defaultMessage: '展开', }))
```


# 拦截请求头


在前端入口处执行一次，这个是为了让后台翻译可以返回翻译后的语言，如果请求有统一用某个插件，比如axios，可以做请求拦截，在头部设置 `Accept-Language`，就不用执行下面代码


```

/**
 * XHR拦截器 - 自动添加国际化语言请求头
 */
export function setupXhrInterceptor() {
  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;

  XMLHttpRequest.prototype.open = function (method, url, ...args) {
    this._url = url;
    this._method = method;
    return originalOpen.apply(this, [method, url, ...args]);
  };

  XMLHttpRequest.prototype.setRequestHeader = function (header, value) {
    if (!this._headers) {
      this._headers = {};
    }
    this._headers[header.toLowerCase()] = value;
    return originalSetRequestHeader.apply(this, [header, value]);
  };

  const originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function (...args) {
    // 获取本地的国际化语言
    const locale = 'zh-CN';

    // 如果存在locale且未手动设置accept-language，则添加该请求头
    if (locale && (!this._headers || !this._headers['accept-language'])) {
      originalSetRequestHeader.call(this, 'Accept-Language', locale);
    }

    return originalSend.apply(this, args);
  };
}
```

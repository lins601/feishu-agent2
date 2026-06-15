---
source: MinDoc
project_name: DP_I18N
source_url: https://docs.cvte.com/docs/dp-i18n/dp-i18n-1h5qs9dq6ntmh
normalized_url: https://docs.cvte.com/docs/dp-i18n/dp-i18n-1h5qs9dq6ntmh
url_hash: 6afef11dd6af
document_key: DP_I18N_6afef11dd6af
doc_id: dp-i18n-1h5qs9dq6ntmh
title: 前端集成
md_hash: dc0047a3ab1af2ea
version: 1773739263
image_count: 0
crawled_at: 2026-06-11 15:57:18
---

# 前端集成

# 阿波罗配置


- 新增配置：key为 `intl-resource`，value为后台打包后包，支持多个，逗号隔开，如 `[@cvte](https://github.com/cvte)/dp-i18n-tzc,[@cvte](https://github.com/cvte)/dp-i18n-oms`


举例1 “供应链天舟云”使用以下两个包：
`天舟云基础包: [@cvte](https://github.com/cvte)/dp-i18n-tzc`
`SCM自定义组件包(无自定义组件则忽略): [@cvte](https://github.com/cvte)/dp-i18n-scm`


 举例2 “GOMS”独立项目非天舟云使用一个包：
`[@cvte](https://github.com/cvte)/dp-i18n-oms`


 举例3 “”制造天舟云”项目使用两个包：
`天舟云基础包: [@cvte](https://github.com/cvte)/dp-i18n-tzc`
`CMS自定义组件包(无自定义组件则忽略): [@cvte](https://github.com/cvte)/dp-i18n-cms`


# 系统配置跳转


startup.js 增加打包前替换代码


```
const path = require('path');
const { execSync } = require('child_process');
/**
 * 更新语言文件
 * @param {string[]} packages - 包名列表
 */
function updateLocaleFiles(packages) {
  const localesDir = path.join(__dirname, 'app/modules/App/locales');

  // 生成导入语句和合并对象
  const generateImportsAndExports = (locale) => {
    const imports = [];
    const mergeObjects = [];

    packages.forEach((pkg) => {
      const varName = pkg.replace(/[@/-]/g, '').replace(/\./g, '') + locale.replace('_', '');
      imports.push(`import { ${locale === 'en_US' ? 'enUS' : 'zhCN'} as ${varName} } from '${pkg}';`);
      mergeObjects.push(`...${varName}`);
    });

    return {
      imports: imports.join('\n'),
      exports: `export default {\n  ${mergeObjects.join(',\n  ')}\n};`,
    };
  };

  // 更新 en_US.js
  const enUSFile = path.join(localesDir, 'en_US.js');
  const enUSContent = generateImportsAndExports('en_US');
  const enUSFileContent = `${enUSContent.imports}\n\n${enUSContent.exports}\n`;

  fs.writeFileSync(enUSFile, enUSFileContent, 'utf8');
  console.log('Updated en_US.js');

  // 更新 zh_CN.js
  const zhCNFile = path.join(localesDir, 'zh_CN.js');
  const zhCNContent = generateImportsAndExports('zh_CN');
  const zhCNFileContent = `${zhCNContent.imports}\n\n${zhCNContent.exports}\n`;

  fs.writeFileSync(zhCNFile, zhCNFileContent, 'utf8');
  console.log('Updated zh_CN.js');
}

/**
 * 处理国际化资源
 * @param {string} intlResourceValue - 逗号分隔的包名列表
 */
function processIntlResources(intlResourceValue) {
  if (!intlResourceValue || typeof intlResourceValue !== 'string') {
    console.log('No intl-resource found or invalid format');
    return;
  }

  const packages = intlResourceValue
    .split(',')
    .map((pkg) => pkg.trim())
    .filter((pkg) => pkg);

  if (packages.length === 0) {
    console.log('No valid packages found in intl-resource');
    return;
  }

  console.log('Installing intl packages:', packages);
  const successPackages = [];
  packages.forEach((pkg) => {
    console.log(`Installing ${pkg}...`);
    try {
      execSync(`yarn add ${pkg}`, { stdio: 'inherit' });
      successPackages.push(pkg);
    } catch (error) {
      console.error(`Error installing ${pkg}:`, error);
    }
  });
  try {
    successPackages.length > 0 && updateLocaleFiles(successPackages);
  } catch (error) {
    console.error('Error updating locale files:', error);
    throw error;
  }
  console.log('Intl resources processed successfully');
}

// 在读取阿波罗配置后的then回调函数里增加
// 处理国际化资源，intl-resource是阿波罗配置
  if (data['intl-resource'] && setEnvOnly) {
    processIntlResources(data['intl-resource']);
  }
```


locales/index.ts 的init增加代码


```
// 本地缓存
    const localCache = new CirCache({ storage: 'localStorage' });
    // 获取本地缓存中的国际化语言包名称
    const localI18nLanguage = localCache.getValue<{
      keys?: string;
      labels?: string;
      locale?: string;
    }>(i18nLanguageMemoName);
    const { locale: localI18nKey } = localI18nLanguage || ({} as any);
    // 调整 i18nLanguageKeyElm逻辑
    if (i18nLanguageKeyElm || localI18nKey) {
      i18nLanguage.key = localI18nKey || i18nLanguageKeyElm?.getAttribute('value');
      i18nLanguageKeyElm?.remove();
    }
```


新增文件
locales/xhrInterceptor.js


```
import CirCache from '@cvte/cir-cache';
import { i18nLanguageMemoName } from '@cvte/cir-lcp-sdk';

// 本地缓存
const localCache = new CirCache({ storage: 'localStorage' });

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
    // 获取本地缓存中的国际化语言
    const localI18nLanguage = localCache.getValue(i18nLanguageMemoName);
    const locale = localI18nLanguage?.locale;

    // 如果存在locale且未手动设置accept-language，则添加该请求头
    if (locale && (!this._headers || !this._headers['accept-language'])) {
      originalSetRequestHeader.call(this, 'Accept-Language', locale);
    }

    return originalSend.apply(this, args);
  };
}
```


在global.js里引入或者在locales/index.ts 的init里增加


```
import { setupXhrInterceptor } from './locales/xhrInterceptor';

// 设置XHR拦截器，自动添加国际化语言请求头
setupXhrInterceptor();
```


# 资源升级


升级资源 `tz-app 2.0.6` `tz-render 2.0.7` `tz-system 1.0.7` `tz-design 2.0.7`

---
source: MinDoc
project_name: COS
source_url: https://docs.cvte.com/docs/cos/cos-1h02p1660u0ck
normalized_url: https://docs.cvte.com/docs/cos/cos-1h02p1660u0ck
url_hash: f709a69b3752
document_key: COS_f709a69b3752
doc_id: cos-1h02p1660u0ck
title: 营销-sourceMap上传问题
md_hash: ea6795b5238a9adb
version: 1765512893
image_count: 0
crawled_at: 2026-06-11 15:58:00
---

# 营销-sourceMap上传问题

## 营销遇到的问题


### 前提


-

sourcemap匹配机制差异
sentry-ali平台和sentry旧平台sourceMap匹配机制有差异，需要文件注入debugId进行匹配

-

系统公共资源缓存匹配
营销域做过一版模块共享，要求ltc-cbp各子系统打包产物中共享模块的hash值一致
模块共享记录可参考链接：[https://kb.cvte.com/pages/viewpage.action?pageId=491951065](https://kb.cvte.com/pages/viewpage.action?pageId=491951065)


### 解决方案


-

debugId注入
依赖：[@sentry](https://github.com/sentry)/webpack-plugin版本需要满足大于等于2，实测2支持，其他版本暂未验证
代码实现


```
config.plugins.push(
  new SentryWebpackPlugin({
    authToken: getInjectConfig('sentryAuthToken').value,
    url: getInjectConfig('sentryUrl').value,
    org: getInjectConfig('sentryOrg').value,
    project: getInjectConfig('sentryProject').value,
    release: getInjectConfig('releaseVersion').value, // 这里的版本号，需要和客户端init的版本号一致才可以匹配
  })
);
```


这一步达成后，即可实现sourceMap匹配

-

模块共享hash值保持一致


现象：debugId是随机hash，打包产物因为注入了随机的debugId，hash值也不固定。目前根据官方文档及几轮验证：debugId与两者有关：**显性指定的版本号**以及**随机值算法**


处理思路


  -

跨系统打包产物版本号保持一致：可根据sprint版本，或者线上配置等思路，营销这边是按时间控制版本，精细度到日，待优化为共享版本配置。

  -

随机值算法逻辑修改为根据content生成：通过两个webpack实现


**exposeModuleContentHashPlugin**负责根据 splitChunk内容生成 固定hash
**SentryDebugIdPatchPlugin** 负责将上一个plugin生成的hash值注入到随机值生成函数消费。


代码片段如下：需要注意plugin注册顺序，**exposeModuleContentHashPlugin => sentryWebpackPlugin => SentryDebugIdPatchPlugin**


```
const crypto = require('crypto');

/**
 * ExposedModuleContentHashPlugin
 * 根据 splitChunks 配置的 chunk 中 node_modules 模块内容计算稳定的哈希值
 *
 * 数据存储在 compiler._customContentHash 上，供其他插件读取
 */
class ExposedModuleContentHashPlugin {
  constructor(options = {}) {
    this.options = {
      hashLength: 10,
      debug: false,
      ...options
    };
  }

  apply(compiler) {
    // 初始化 compiler 上的存储对象（全局单例，贯穿整个构建过程）
    if (!compiler._customContentHash) {
      compiler._customContentHash = {};
    }

    compiler.hooks.thisCompilation.tap('ExposedModuleContentHashPlugin', (compilation) => {
      // 在优化完所有 Chunk 后执行计算，此时模块和 Chunk 关系已稳定
      compilation.hooks.afterOptimizeChunks.tap('ExposedModuleContentHashPlugin', (chunks) => {
        /** [@type](https://github.com/type) {Record<string, {hash: string, modules: string[]}>} */
        const chunkHashMappings = {};

        for (const chunk of chunks) {
          // 只关注有名称且非运行时的 Chunk（通常对应 splitChunks 配置产生的 Chunk）
          if (!chunk.name || chunk.hasRuntime()) {
            continue;
          }

          const chunkGraph = compilation.chunkGraph;
          const moduleContents = [];

          // 递归收集模块内容的内部函数
          const collectModules = (module) => {
            // 处理复合模块（如 ConcatenatedModule）
            if (module.modules) {
              for (const innerModule of module.modules) {
                collectModules(innerModule);
              }
              return;
            }

            // 筛选出来自 node_modules 的模块
            const resource = module.resource;
            if (!resource || !resource.includes('node_modules')) {
              return;
            }

            // 获取源码内容
            let source = module.originalSource ? module.originalSource() : null;
            if (!source && module.source) {
              source = module.source();
            }
            if (!source) {
              return;
            }

            const content = source.source();
            const packageName = this._extractPackageName(resource);
            const identifier = packageName || resource || module.identifier();

            moduleContents.push({
              identifier,
              content,
            });
          };

          // 遍历当前 Chunk 的所有模块
          for (const module of chunkGraph.getChunkModulesIterable(chunk)) {
            collectModules(module);
          }

          // 如果捕获到模块，则计算并存储哈希映射
          if (moduleContents.length > 0) {
            // 排序以确保哈希计算的稳定性
            moduleContents.sort((a, b) => a.identifier.localeCompare(b.identifier));
            const combinedContent = moduleContents.map(m => `|${m.identifier}|${m.content}`).join('');
            const customHash = crypto
              .createHash('md5')
              .update(combinedContent)
              .digest('hex')
              .slice(0, this.options.hashLength);

            chunkHashMappings[chunk.name] = {
              hash: customHash,
              modules: moduleContents.map(m => m.identifier) // 记录包含的模块标识，便于调试
            };

            if (this.options.debug) {
              console.log(`[ExposedPlugin] 映射: "${chunk.name}" -> ${customHash}`);
            }
          }
        }

        // === 核心：将映射关系挂载到 compiler 对象上（全局存储） ===
        // 使用 Object.assign 合并，支持增量编译场景
        Object.assign(compiler._customContentHash, chunkHashMappings);

        // 同时也挂载到 compilation 上，方便同一编译周期内访问
        compilation.customContentHash = chunkHashMappings;

        if (this.options.debug) {
          console.log('[ExposedPlugin] 映射已挂载至 compiler._customContentHash');
        }
      });
    });
  }

  _extractPackageName(resourcePath) {
    const nodeModulesIndex = resourcePath.indexOf('node_modules');
    if (nodeModulesIndex === -1) return null;
    const parts = resourcePath.slice(nodeModulesIndex + 13).split(/[\\/]/);
    if (parts[0].startsWith('@')) {
      return parts.slice(0, 2).join('/');
    }
    return parts[0];
  }
}

module.exports = ExposedModuleContentHashPlugin;
```


```
 /**
  * SentryDebugIdPatchPlugin
  * 用于将自定义的 chunk 哈希值注入到 Sentry BannerPlugin 中
  *
  * 解决时序问题的关键：
  * 1. 在 compilation 钩子中包装 BannerPlugin 的 banner 函数
  * 2. banner 函数执行时（代码生成阶段）动态从 compiler 获取哈希映射
  * 3. compiler 是全局单例，数据存储更可靠
  */
 class SentryDebugIdPatchPlugin {
     /**
      * 包装 BannerPlugin 的 banner 函数
      * [@param](https://github.com/param) {Object} bannerPlugin - webpack BannerPlugin 实例
      * [@param](https://github.com/param) {Object} compiler - webpack compiler 对象，用于在 banner 执行时获取哈希映射
      */
     wrapBanner(bannerPlugin, compiler) {
       // 保存原始的 banner 函数
       const originalBanner = bannerPlugin.options.banner;

       // 标记已包装，避免重复包装
       if (bannerPlugin._wrappedBySentryPatch) {
         return;
       }
       bannerPlugin._wrappedBySentryPatch = true;

       // 包装 banner 函数，在执行时动态获取哈希映射
       bannerPlugin.banner = (arg) => {
         // 关键修复：在 banner 执行时从 compiler 获取哈希映射
         // compiler 是全局单例，数据存储更可靠
         const hashMap = compiler._customContentHash;

         if (arg?.chunk?.contentHash?.javascript) {
           let hash = arg.chunk.contentHash.javascript;

           // 如果存在自定义哈希映射，则使用自定义哈希
           if (
             hashMap &&
             hashMap[arg.chunk.name] &&
             hashMap[arg.chunk.name].hash
           ) {
             hash = hashMap[arg.chunk.name].hash;
           }

           return originalBanner({
             chunk: { hash: hash || arg.chunk.contentHash.javascript },
           });
         }
         return originalBanner(arg);
       };
     }

     apply(compiler) {
       // 初始化 compiler 上的存储对象
       if (!compiler._customContentHash) {
         compiler._customContentHash = {};
       }

       // 在 compilation 钩子中包装 BannerPlugin（比 thisCompilation 更晚，确保插件都已注册）
       // 找到 Sentry 的 BannerPlugin 并包装
       const bannerPlugin = compiler.options.plugins.find(
         (p) =>
           p.constructor.name === 'BannerPlugin' &&
           (p.options?.banner?.toString().includes('getDebugIdSnippet') ||
             p.banner?.toString().includes('getDebugIdSnippet'))
       );

       if (bannerPlugin) {
         // 传入 compiler 对象，让 banner 函数执行时能动态获取哈希映射
         this.wrapBanner(bannerPlugin, compiler);
       } else {
         console.warn('[SentryDebugIdPatchPlugin] 未找到 Sentry BannerPlugin');
       }
     }
   }

 module.exports = SentryDebugIdPatchPlugin;
```

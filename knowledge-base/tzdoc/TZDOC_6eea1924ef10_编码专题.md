---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fmkgv85vjsk8
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1fmkgv85vjsk8
url_hash: 6eea1924ef10
document_key: TZDOC_6eea1924ef10
doc_id: tzdoc_v2-1fmkgv85vjsk8
title: 编码专题
md_hash: bd4fe05932ac035e
version: 1733226788
image_count: 2
crawled_at: 2026-06-11 16:12:39
---

# 编码专题

# 4. 在线编码


## 4.1 在线编码说明


指在线写代码，写的内容会直接运行，全局已经定义了configs的变量，所有的数据通过configs获取。


| 变量 | 说明 | 例子 |
| --- | --- | --- |
| btnConfig | 按钮配置 | configs.btnConfig |
| context | 表单上下文 | configs.context |
| data | 表单数据 | configs.data |
| relation | 联动，可以增加、触发联动 | configs.relation |
| utils | 工具类，可以弹窗，加载组件等 | configs.utils |


通用参数解释


| 变量 | 说明 |
| --- | --- |
| formCode | 组件的formCode ， [图片1: null] |
| attrCode | 组件的code， [图片2: null] |


### 4.1.1 context 上下文


| 变量 | 说明 | 入参 | 例子 |
| --- | --- | --- | --- |
| addListener | 添加监听方法,主要是事件执行前触发，事件执行后触发以及值改变时触发 | { code: 唯一值即可; type: `'valueChange' \| 'beforeEvent' \| 'afterEvent'`; handler: 回调方法 } | configs.context.addListener({ code: ‘test’, type: ‘valueChange’，handler: ({ configs, extra, value }) => {} }) |
| config | 整体表单配置 |  | configs.context.config |
| dictionary | 表单数据字典，不包含明细表 |  | configs.context.dictionary |
| form | 表单，可用于校验值和获取值，具体参考antd form的api |  | configs.context.form.getFieldValue() |
| getBtnConfig | 获取按钮配置 | { formCode: 空字符串，attrCode：按钮所在容器的attrCode，btnCode：按钮的code } | configs.context.getBtnConfig({ formCode: ‘parentCode’, attrCode: ‘attrCode’, btnCode: ‘save’ }) |
| getCompParams | 获取由上下文传给组件的配置 | { formCode: 组件的formCode，attrCode：组件的attrCode } | configs.context.getCompParams({ formCode: ‘parentCode’, attrCode: ‘attrCode’}) |
| getCompRef | 获取组件对应api，比如获取明细表的数据字典，返回组件api，组件都有onChange的api | { formCode: 组件的formCode，attrCode：组件的attrCode } | configs.context.getCompRef({ formCode: ‘parentCode’, attrCode: ‘attrCode’}).getDictionary() |
| getConfig | 获取组件配置，返回IConfig类型 | { formCode: 组件的formCode，attrCode：组件的attrCode } | configs.context.getConfig({ formCode: ‘parentCode’, attrCode: ‘attrCode’}) |
| getDictionary | 获取数据字典 |  | configs.context.getDictionary() |
| getFormData | 获取表单数据 |  | configs.context.getFormData() |
| getFormId | 获取表单id，如果创建则返回空 |  | configs.context.getFormId() |
| refresh | 刷新数据 |  | configs.context.refresh() |
| refreshBtnConfigs | 刷新按钮配置 | { formCode: 空字符串，attrCode：按钮所在容器的attrCode } | configs.context.refresh({ formCode: ‘parentCode’, attrCode: ‘attrCode’}) |
| refreshConfigs | 刷新配置 | isDeep: 是否深度刷新，true的话比较整个配置会重新刷新 | configs.context.refreshConfigs(true) |
| reset | 编辑状态刷新为创建 |  | configs.context.reset() |
| setBaseConfig | 设置组件的基础配置，比如是否展示，是否编辑等，设置后需要调用refreshConfigs |  | configs.context.setBaseConfig({formCode:’’，attrCode:’’, attr: ‘改变属性的key’, value: ‘改变属性的value’, extra: ‘如果是明细的话，对单行操作则需要填对应信息{ id:’’ }’}) |
| setCompParams | 设置需要传给组件的参数 | { ‘对应组件的code’：{} } | configs.context.setCompParams({ test: { onSearch: () => false } }) |
| setFormData | 设置表单内容，该方法改变值后不会触发联动，需要触发联动则需用getCompRef获取组件api，然后调用onChange，如：configs.context.getCompRef({formCode:’’，attrCode:’’}).onChange(1) |  | configs.context.setFormData({a:12,b:21}) |
| setRuleConfig | 设置组件校验规则，比如必填等，设置后需要调用refreshConfigs |  | configs.context.setRuleConfig({formCode:’’，attrCode:’’, attr: ‘改变属性的key’, value: ‘改变属性的value’, extra: ‘如果是明细的话，对单行操作则需要填对应信息{ id:’’ }’}) |
| unlisten | 注销监听 | { code: 监听的值; type: ‘valueChange’ \| ‘beforeEvent’ \| ‘afterEvent’; } | configs.context.unlisten({ code:’’, type: ‘valueChange’}) |


### 4.1.2 data 数据


| 变量 | 说明 | 例子 |
| --- | --- | --- |
| allData | 表单上所有数据 | configs.data.allData |
| currentData | 当前容器数据，比如执行在card上，就只拿到card里面的内容 | configs.data.currentData |
| preData | 同一事件里，上一环节执行事件结果，比如事件中会拿到前置事件的结果，后置事件会拿到事件中的结果 | configs.data.preData |
| extraData | 额外的数据，如果是明细表的操作按钮，可以获取到选中结果，如果是明细表的行按钮，可以获取到行数据，具体可以打印 | configs.data.extraData |


### 4.1.3 relation 联动


| 变量 | 说明 | 入参 | 例子 |  |
| --- | --- | --- | --- | --- |
| on | 注册联动 | {attrCode: 组件attrCode, formCode: 组件formCode, actionCode: 执行类型，类型有：blur(失焦执行)、focus(聚焦执行)、change（值改变执行）、search（搜索时执行）、create（打开创建时执行）、edit（打开编辑时执行）、destroyOnCreate（创建表单结束离开时执行）、destroyOnEdit（编辑表单结束离开时执行}，{id:场景下唯一值, cb: 联动回调} | configs.relation.on({attrCode:’’,formCode:’’,actionCode:’change’},{id: ‘唯一值，避免重复’, cb: (ctx) => {}}) |  |
| emit | 执行联动 | {attrCode: 组件attrCode, formCode: 组件formCode, actionCode: 与on的actionCode相同}，{attrCode: ‘可选’,formCode:’可选’,index:’明细表需要的序号’，id:’明细表需要的id’} | configs.relation.emit({attrCode:’’,formCode:’’,actionCode:’change’}, {}) |  |


### 4.1.4 utils 工具类


| 变量 | 说明 | 例子 |
| --- | --- | --- |
| confirm | 参考antd confirm用法 | configs.utils.confirm({ title: ‘title’, content: ‘content’, onOk: () => {} }) |
| notification | 参考antd notification用法 | configs.utils.notification.warning({ message: ‘warning’, description: ‘warning’ }) |
| message | 参考antd message用法 | configs.utils.message.warning(‘message’) |
| useCompModal | 入参：Content：组件, modalConfigs：modal配置，参考antd Modal，contentConfig：组件配置 | configs.utils.useCompModal(Component, { title: ‘弹窗标题’}, {a: ‘组件入参1’, b: ‘组件入参2’}) |
| loadCmp | 拉取资源组件，入参：{sourceName: 资源名称，exposesKey：导出的资源} | configs.utils.loadCmp({ sourceName:’lcp-page-component’, exposesKey: ‘charts’ }) |
| fetch | 调用接口，入参参考[axios](https://axios-http.com/docs/req_config) | configs.utils.fetch({ url:’/apis/common/proxy/lcpGw/tz_api/服务编码/path路径’,method: ‘get’ ,params: {a:123}}) |
| cache | 缓存，可以获取以及存缓存，api有：setValue，getValue，clear；缓存实际挂载在window的天舟云缓存空间中 | configs.utils.cache.setValue(‘key’, 123)，configs.utils.cache.getValue(‘key’)，configs.utils.cache.clear(‘key’) |


### 4.1.5 extra 联动额外数据


| 变量 | 说明 | 例子 |
| --- | --- | --- |
| attrCode | 当前触发的字段的code |  |
| formCode | 当前触发的字段的formCode |  |
| id | 如果在明细行行中，存在id |  |
| index | 如果在明细行中，存在行下标，一般是数字，如果是树形明细表，下标是字符串 | 如果是1，表示第二行，如果是”1-2”，表示第1行的的第二个子行 |


## 4.2 示例


当请求成功后，我们希望按钮可以弹出对应提示信息，我们可以通过后置事件，配置在线编码


```
const preData = configs.data.preData;
if(preData.status!=="0"){
    configs.utils.notification.error({message:preData.message,content:preData.message});
}else{
    configs.utils.notification.success({message:"更新成功",content:"更新成功"});
}
const pageId = configs.context.globalContext.pageTools.getDetailPageId(configs.context.config);
configs.context.globalContext.pageTools.refresh(pageId);
```


### 获取元素/组件的值


方法一


```
const formData = configs.context.getFormData();
const aValue = formData?.['a'];
```


方法二


```
const aValue = configs.data.allData?.['a'];
```


### 设置元素/组件的值


仅改变值


```
configs.context.setFormData({ a: 123 });
configs.relation.emit({ attrCode: 'a', formCode: 'formCode', actionCode: 'change' }, { id: '如果是明细表，需要明细表行id', index: 0 });
```


仅改变值后会自动触发联动的做法


```
// 明细表的formCode一般是没有，所以formCode填空字符串
const aRef = configs.context.getCompRef({ formCode: `123`, attrCode: '12' });
aRef.onChange(123);
```


### 设置元素/组件可编辑状态


正常元素/组件


```
// 1 表示可编辑，0 表示不可编辑
configs.context.setBaseConfig({ formCode: `123`, attrCode: '12', attr: 'isEditable', value: '1' });
configs.context.refreshConfigs(true);
```


如果是明细表，需要加上id和index


```
configs.context.setBaseConfig({ formCode: `111`, attrCode: '12', attr: 'isEditable', value: '1', extra: { id: '123', index: 1} });
configs.context.refreshConfigs(true);
```


### 设置元素校验规则


```
// attr 有 | 'required' 'max' 'min' 'maxLength' 'minLength'  'len' 'special'  'compare' 'pattern' 'accuracy'
// 开启必填
configs.context.setRuleConfig({ formCode: '', attrCode: '', attr: 'required', value: '1', extra: { id: '如果是针对明细表某一行，需要写id，如果是主表对明细表操作，不写', index: '如果是针对明细表某一行，需要写index，如果是主表对明细表操作，不写' } })
```


### 设置元素/组件禁用状态


正常元素/组件


```
// 1 表示可用，0 表示禁用
configs.context.setBaseConfig({ formCode: `123`, attrCode: '12', attr: 'isEnabled', value: '1' });
configs.context.refreshConfigs(true);
```


如果是明细表，需要加上id和index


```
configs.context.setBaseConfig({ formCode: `111`, attrCode: '12', attr: 'isEnabled', value: '1', extra: { id: '123', index: 1} });
configs.context.refreshConfigs(true);
```


### 设置元素/组件隐藏显示状态


正常元素/组件


```
// 1 表示显示，0 表示隐藏
configs.context.setBaseConfig({ formCode: `123`, attrCode: '12', attr: 'isVisible', value: '1' });
configs.context.refreshConfigs(true);
```


如果是明细表，需要加上id和index


```
configs.context.setBaseConfig({ formCode: `111`, attrCode: '12', attr: 'isVisible', value: '1', extra: { id: '123', index: 1} });
configs.context.refreshConfigs(true);
```


### 设置按钮禁用状态


```
// formCode填按钮所在容器的formCode，attrCode填容器的code，btnCode填按钮的code，比如按钮所在容器是在card里面，点击设计器的card后，在设计配置可以看到对应的code，鼠标放上去可以看到对应的formCode。2.4版本后formCode可以在高级属性->表单组编码看到，如果没有，则填空字符串
// 如果是表单属性配置的表单按钮，formCode填空字符串，attrCode填'BTN_BLOCK_GLOBAL';
const btnConfig = configs.context.getBtnConfig({ formCode: ``, attrCode: '容器的code', btnCode: '123'});
if (!btnConfig.config.uiConfig) {
     btnConfig.config.uiConfig = {}
 }
 if (!btnConfig.config.uiConfig.attr) {
     btnConfig.config.uiConfig.attr = {}
 }
 // true 表示禁用，false 表示可用
btnConfig.config.uiConfig.attr.disabled = true;
configs.context.refreshBtnConfigs({ formCode: ``, attrCode: '12' });
```


### 设置按钮隐藏


```
// formCode填按钮所在容器的formCode，attrCode填容器的code，btnCode填按钮的code，比如按钮所在容器是在card里面，点击设计器的card后，在设计配置可以看到对应的code，鼠标放上去可以看到对应的formCode。2.4版本后formCode可以在高级属性->表单组编码看到，如果没有，则填空字符串
// 如果是表单属性配置的表单按钮，formCode填空字符串，attrCode填'BTN_BLOCK_GLOBAL';
const btnConfig = configs.context.getBtnConfig({ formCode: ``, attrCode: '容器的code', btnCode: '123'});
// 1 表示显示，0 表示隐藏
btnConfig.config.baseConfig.isVisible = '1';
configs.context.refreshBtnConfigs({ formCode: ``, attrCode: '12' });
```


### 明细表


- 明细表是表单的一种元素，数据格式是数组，每一行数组都是一个对象，每一组对象都有ID的key，如果是新增一行数据，在当行数据需要加__add: true。
- 如果是个树形的明细表，每一行的数据中都有children的key，children表示是子行，值是一个数组，数据格式明细表一样。
- 如果有子行或者行数据中有children字段，说明是树形明细表。
新增一行数据，利用context.getCompRef的能力实现新增一行并可以执行联动

#### 明细表新增一行


```
const { context, data } = configs;
const tableRef = context.getCompRef({ formCode: 'formCode', attrCode: 'attrCode' });
let tableData = data?.allData?.['tableAttrCode'];
if (!Array.isArray(tableData)) {
tableData = [];
}
const id = +new Date()
tableData.push({ ID: id, __add: true });
// onChange可以改变值和触发联动
tableRef?.onChange?.([...tableData], { id, formCode: 'formCode', attrCode: 'attrCode', index: tableData.length })
```


#### 明细表新增一行，并在子行插入其他元素的数据


```
const { context, data } = configs;
const tableRef = context.getCompRef({ formCode: 'formCode', attrCode: 'attrCode' });
let tableData = data?.allData?.['tableAttrCode'];
let aValue = data?.allData?.['aValue'];
if (!Array.isArray(tableData)) {
tableData = [];
}
const id = +new Date()
const newRow = { ID: id, __add: true, children: [] };
if (!Array.isArray(tableData)) {
aValue = []
}
aValue?.forEach((_a) => {
newRow.children.push({ ID: Math.random(), __add: true, aValue: _a})
})
tableData.push(newRow);
// onChange可以改变值和触发联动
tableRef?.onChange?.([...tableData], { id, formCode: 'formCode', attrCode: 'attrCode', index: tableData.length })
```


### 弹窗点击确定


```
const { context } = configs;

// 通过弹窗等待用户确认
const isConfirmed = await new Promise(rs => {
configs.utils.confirm({
    title: '确认是否做什么事情',
    content: '',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      // 确认
      rs(true);
    },
    onCancel() {
      // 取消
      rs(false)
    }
  })
});
if (isConfirmed) {
  // 做一些事情
}
```


### 弹窗提示


```
configs.utils.message.warning('message');
```


### 获取表单数据


例子1：


```
const formData = configs.context.getFormData();
```


例子2：


```
// allData 是表单上所有数据
const { allData } = configs.data;
```


### 获取用户信息


```
const userInfo = context.getContext()?.session?.user ?? {};
// 一般有账号，邮箱，用户id，名称，电话
const { account, email, id, name, telephone } = userInfo;
```


### 校验，可阻止后续事件执行


```
const { context } = configs;
const formData = context.getFormData();

const a = formData?.['a'];

if (a === 1) {
    // 后续事件可以拿到response内容，后续事件configs?.data的preData就是response
    response = { success: true, data1: { ttt: 1111 } }
} else {
    // success为false时，后续事件不继续执行
    response = { success: false }
}
```


### 跳转到对应tab


```
// 获取tab的ref，tab是没有formCode，所以填空字符串即可
const tabRef = context.getCompRef({ formCode: '', attrCode: 'TAB_62136AC9079FC'});
// 跳转到对应tab签
tabRef?.setTabKey?.('TABPANE_9AE31CDE5A86E');
```


### 调用天舟云内置保存事件


```
const saveHandler = configs.utils?.cache?.getCache?.()?.['FORMREGISTER']?.["__commonSave"]?.eventHandler;
console.log('--saveHandler', saveHandler);
const currentData = configs?.context?.getFormData?.();
const resp = await saveHandler?.({
    context: configs.context,
    data:{
        currentData,
        allData: currentData,
    },
    btnConfig: {
        scope: ['__GLOBAL_SCOPE'],
    }
})
```


### 组件调用天舟云内置保存事件


```
import CirCache from '@cvte/cir-cache';

const Page = () => {
  const cirCacheRef = useRef(new CirCache({ storage: 'memory' }));
  const detailRef = useRef()

  const handleSave = async () => {
    const context = detailRef.current?.RenderRef?.current?.getContext() ?? {};
    const saveHandler = (cirCacheRef.current?.getCache?.() as any)?.FORMREGISTER
      ?.__commonSave?.eventHandler;
    if (!saveHandler) {
      throw new Error('unexpected savehandler');
    }
    if (!context) {
      throw new Error('unexpected context');
    }
    const currentData = context.getFormData?.();
    const resp = await saveHandler({
      context,
      data: {
        currentData,
        allData: currentData
      },
      btnConfig: {
        scope: ['__GLOBAL_SCOPE']
      }
    });
    return resp;
  };

  return <DetailTemp ref={detailRef} {...其他参数} />

}
```


### 调用天舟云内置校验事件


```
const validateFn = configs.utils?.cache?.getCache?.()?.['FORMREGISTER']?.["__commonGetFormDetailData"]?.eventHandler;
// 会先校验，再返回保存的格式数据
const validatedFormData = await validateFn?.(configs);
// 如果能拿到数据，说明校验成功，如果失败，返回undefined
```


### 储存外部工具


```
// 系统里面储存内容
import CirCache from '@cvte/cir-cache';
const cache = new CirCache({ storage: 'memory' })
cache.setValue('your tool name', 123);

// 在线代码消费内容
const tool = configs.utils.cache.getValue('your tool name');
// tool()
```


### 调用history


```
// 前端2.4版本前
const { cache } = configs.utils || {};
cache.getValue('history-cache').push('/your/url');
// 前端2.4版本后内置history
const { extra } = configs.utils || {};
extra.getValue('history').push('/your/url');
```


### 调用搜索


```
// 说明是谁调用事件，方便排查链路
const owner = 'test';
// 获取事件总线
const subjectEventBus = configs.utils?.getSubjectEventBus?.();
// 调用搜索事件，
subjectEventBus.publishEvent({ owner, eventName: 'form:onSearch' ， data: { name: '模糊查询的值，和code二选一', code: '精确查询的值，和name二选一', formCode: '目标字段的formCode', attrCode: '目标字段的attrCode', afterSearchOperate: 'firstOption或者noValue, 搜索后连接器判断noValue的话，不会执行连接器配置的联动，firstOption暂时没支持', id: '如果目标在明细表中，需要行id', index: '如果目标在明细表中，需要行序号'  }});
```


### 模板调用渲染上下文


```
// 列表和详情调用不一样
// 详情
const context = ref.current?.RenderRef?.current?.getContext() ?? {};
// 列表
const context = ref.current?.getRef?.()?.getRenderRef?.()?.getContext?.() ?? {};
```


### 请求映射


```
// 表单按钮接口请求
{
    // 表单数据
    data: {},
    // 表单配置
    page: {
        classId,
        layout: [],
        code,
        id,
    },
    // 获取组织和用户信息
    session: {
        org: {
            orgCode,
            id,
            orgName
        },
        user: {
            account,
            name,
        },
    },
    // 获取url上的query
    url: {}
    // 前置事件数据
    preData: {}
}
// 例子：${data.字段}：${page.id}
// 联动请求
{
response,
formData
}
// 列表
{
    extraData: {
        entity,
        ref: {
            selectedKeys,
            selectedRows
        }
    },
    page: {
        layout: [],
        pageCode,
        id,
    },
    preData,
    session: {
        org: {
            orgCode,
            id,
            orgName
        },
        user: {
            account,
            name,
        },
        url: {

        }
    }
}
```


### 明细表获取选择行数据


```
const tableRef = configs.context.getCompRef({ formCode: '', attrCode: '明细表code' });
const { selectedRows, selects } = tableRef?.getSelects?.() || {}
```


### 触发联动


```
configs.relation.emit({ formCode: '', attrCode: '', actionCode: 'change' })
```


### 监听联动


```
relation?.on?.(
      {
        // 监听当前设置选项映射的属性，发生选项值改变时
        // 则将配置了返回字段的属性值，改为该选项中映射字段的值
        actionCode: 'change',
        attrCode: '', // 属性字段编码；如果是监听明细表，那么此处要写明细表编码
        formCode: '', // 主表/明细表编码；如果是监听明细表，那么此处要写undefined
      },
      {
        // 内置联动id如果每次不唯一，那么每次都会增加无效的联动
        id: relationId,
        cbParams: {
            // 传给回调方法的参数
        },
        cb: async (relationCtx: IRelationClassOnConfigsFnCtx) => {
            // TODO
            const {
                cbParams, // 传给回调方法的参数
                id: relatedRowId, // 当前联动的行id
                index: relatedRowIndex, // 当前联动的行索引
                value: changedValue, // 当前触发联动的值内容
            } = relationCtx.extra || ({} as any);
        }
      },
    );
```


### 获取内置方法


```
// 前端tz-render@1.0.1.10版本后内置history,download,upload
const { extra } = configs.utils || {};
extra.getValue('history').push('/your/url');
extra.getValue('download')({ fileName: '文件名称', noTime: 是否加上时间, blob: 文件流 });
 const files = await extra.getValue('upload')({ fileType: 'file', fileAccept: '.xlsx,.xls,.et' });

```


### 调用模板


```
const { loadCmp } = configs?.utils || {};
// 调用列表模板
const Page = loadCmp({ sourceName: 'tz-render', exposesKey: 'Page'  });
// 调用表单模板
const Detail = loadCmp({ sourceName: 'tz-render', exposesKey: 'Detail'  });
// 参数参考：https://docs.cvte.com/docs/tzv16/216
```

---

# 文档图片附录


---

## 图片1: null

![null](data:image/jpeg;base64,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)


---

## 图片2: null

![null](data:image/jpeg;base64,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HOABcDuHRlS2koqWgjfHSQMhY97pHNYMAuO8nylYUOm7JT15r4bTSMqtov5YRDa2jxOelBmVldSW6nNRW1MVNC3i+V4aPOVBr/dO32mNm09RfKohIHPucwdHFTuB4sO4ud5OnvgqcVtvo7lAIK6liqYg4PDJWBzcjgcFXWRsiYI42NYxowGtGAAggNmrZ+x6x9tv1GXUckrpBd6drniRzjxlG8h3ez//AFSK6T1t8srDpe50jTNIGvqs7ewz+sW4/rDduP4Leua17S1wDmkYIIyCsajt9HbYnQ0NLFTRueXlkTA0Fx4nAQQa7aZu9h0zdae2XCkbbfksj5mywF08x5Puy5+d7jvx0buheWrTl3vmmLXTXSvpH2t1LE5jI4C2aPuBsFr87nDdv7+/pU9qIIqmCSnnjbJFKwsexwyHNIwQVRDBFS08dPBG2OKJgYxjRua0DAAQauxUl1obb8mu9bHWzRvIjma0guj/AKu10u6f+fE8o19+utf/AHf+m1drcuKa/wD11uH93/ptQR1ERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQEREBERAREQUx/MCqVMfzAqkBERB9MoiICx5v6ZT/wDF+5ZCtTQ8rskOLHtOWuCC6ix+RqeterCcjU9a9WEGQix+RqeterCcjU9a9WEGQix+RqeterCcjU9a9WEGQvFY5Gp616sJyNT1r1YQX14rHI1PWvVhORqeterCC+qSrPI1PWvVheGGp616sILxVBVsw1PWvVhUmGp616sIKyFHL/oq1agqhVVPLRThoaXwuA2gOGcghb4w1HWvVhUGCo616sIIYexdZR/+VX+kZ8Cp5sLN1qv9Iz4VMjBUdZ9WFSaeo6z6sIIdzY2brVd6RnwpzY2frVd6RnwqYfJ6jrPqwnIVHWfVhBD+bGzdarvSM+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGz9arvvs+FObGz9arvvs+FS/kKjrPqwnIVHWfVhBEObGzdarvSM+FObGz9arvSM+FS/kKjrPqwnyeoO41O7+wEELpuxnZ5KdrjU12Tng9nT/AGVe5sLN1qv9Iz4VNI4RHGGN4BVhqCFDsXWU/wD5Vf6RnwK/H2MLC1uHSVjz0ulb/wAmqYBqrwgzEREBERAREQEREBERAXi9RB4iIg8Xi9QoKSvCFUVSgpIVJCrKpQUELwhVrwoKNleYVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTZVaIKNlNlVogo2U2VWiCjZTCrRBTsr0Ber0IPAF7her1B/9k=)



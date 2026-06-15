---
source: MinDoc
project_name: TZDOC
source_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1g1etajrrranb
normalized_url: https://docs.cvte.com/docs/tzdoc_v2/tzdoc_v2-1g1etajrrranb
url_hash: b0a0354eb90e
document_key: TZDOC_b0a0354eb90e
doc_id: tzdoc_v2-1g1etajrrranb
title: 如何获取上传文件的内容进行校验
md_hash: 3dd4609d9bf9f1d0
version: 1731636578
image_count: 0
crawled_at: 2026-06-11 16:13:59
---

# 如何获取上传文件的内容进行校验

### 场景：


[测试链接](https://lcptest.gz.cvte.cn/workspace/323876ac260c4caabbaa61083168b45a/moduleId/73961b2d34be4a57a0c64cffe4347dd8)
在明细表导入时，需要比较导入数据与原数据是否冲突，进行筛选或终止导入。


```
const { context, data, utils = {} } = configs;
// 触发上传文件，并获取到上传的文件对象
const files = await utils.extra.getValue('upload')({ fileType: 'file', fileAccept: '.xlsx,.xls,.et' });
const formData = new FormData();
formData.append('file', files[0]);

// 将文件发送后端，获取解析数据，其中包括行内联动产生的数据
const importRes = await utils.fetch({
    url:'/apis/common/proxy/lcpApp/excel/import/biz',
    method: 'post',
    params: {
        classId: 'classId',
        bizApiName: '明细表编码',
    },
    data: formData,
});
console.log("查看返回数据格式，准备校验...", importRes)

```

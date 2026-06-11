# -*- coding: utf-8 -*-
import re, json

# Extract sidebar links from the fetched HTML
sidebar_links = [
    ("https://docs.cvte.com/docs/mfg/mfg-1gabr49bn55co", "系统概述", "mfg-1gabr49bn55co", "root"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabr8ucf1u06", "快速入门", "mfg-1gabr8ucf1u06", "cat"),
    ("https://docs.cvte.com/docs/mfg/mes", "生产管理", "mes", "cat"),
    ("https://docs.cvte.com/docs/mfg/wms", "仓储管理", "wms", "cat"),
    ("https://docs.cvte.com/docs/mfg/qms", "质量管理", "qms", "cat"),
    ("https://docs.cvte.com/docs/mfg/sop", "功能手册", "sop", "cat"),
    ("https://docs.cvte.com/docs/mfg/mes-sop", "生产管理", "mes-sop", "cat"),
    ("https://docs.cvte.com/docs/mfg/wms-sop", "仓储管理", "wms-sop", "cat"),
    ("https://docs.cvte.com/docs/mfg/qms-sop", "质量管理", "qms-sop", "cat"),
    ("https://docs.cvte.com/docs/mfg/ch", "质量策划", "ch", "cat"),
    ("https://docs.cvte.com/docs/mfg/khzl", "客户质量管理", "khzl", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gvrb6d769k4s", "售后维修费用转嫁（客责）", "mfg-1gvrb6d769k4s", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabrrm6v1hjv", "研发设计质量", "mfg-1gabrrm6v1hjv", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabrrult334o", "物料质量管理", "mfg-1gabrrult334o", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabrtmj784v7", "供应商质量管理", "mfg-1gabrtmj784v7", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs089knlde", "供方技术交流管理", "mfg-1gabs089knlde", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs0ikcnrjr", "供方技术交流管理-供应商", "mfg-1gabs0ikcnrjr", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gebvqg3ktkt7", "供方技术交流-供应商版本", "mfg-1gebvqg3ktkt7", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs0tv2hvku", "物料参数案例库", "mfg-1gabs0tv2hvku", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs3ofatcm1", "物料参数管理", "mfg-1gabs3ofatcm1", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs5pkqd922", "供应商测试数据导入", "mfg-1gabs5pkqd922", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1ge440a81qohn", "供应商测试数据导入(供应商版本)", "mfg-1ge440a81qohn", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs6jv840p1", "供应商测试数据对接接口", "mfg-1gabs6jv840p1", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gns2cesl4qp3", "器件原始测试数据对接接口文档", "mfg-1gns2cesl4qp3", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gclhqug28amo", "物料预警", "mfg-1gclhqug28amo", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gclja7obhh53", "物料统计查询", "mfg-1gclja7obhh53", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs4hbui39b", "基础配置", "mfg-1gabs4hbui39b", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs41r4idpe", "供应商参数管理", "mfg-1gabs41r4idpe", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabs54dhhcig", "物料参数导入模版", "mfg-1gabs54dhhcig", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gclbi30glq48", "器件预警规则", "mfg-1gclbi30glq48", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gclcbkjcfcjj", "物料控制图规则", "mfg-1gclcbkjcfcjj", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gkpo42d4lg90", "供应商数据对接实施标准", "mfg-1gkpo42d4lg90", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gdjm418vt23r", "物料检验", "mfg-1gdjm418vt23r", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gdjm4lph5uq5", "进料检验尺寸SPC分析", "mfg-1gdjm4lph5uq5", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gabrscf2pki2", "制程质量管理", "mfg-1gabrscf2pki2", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gah4p6duuq56", "生产异常处理", "mfg-1gah4p6duuq56", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1ghqd21b0suv7", "制程稽核问题", "mfg-1ghqd21b0suv7", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gidfk54askr4", "质量保证", "mfg-1gidfk54askr4", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gidfkj0bcvaj", "质量责任处理闭环", "mfg-1gidfkj0bcvaj", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gidhqvh69ka1", "质量责任处理单", "mfg-1gidhqvh69ka1", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gidhrg34umf7", "质量保证金&质量保证金支付单", "mfg-1gidhrg34umf7", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gidhse7vuo1d", "暂停支付货款通知单&解除暂停支付货款通知单", "mfg-1gidhse7vuo1d", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gidhso1jutcv", "赠品入库&特殊折扣订单抵扣", "mfg-1gidhso1jutcv", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm8v5bcqlnrg", "市场质量管理", "mfg-1gm8v5bcqlnrg", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm8v6bjd08cf", "EWP分析-研发质量", "mfg-1gm8v6bjd08cf", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm9bb914db45", "EWP分析-生产质量", "mfg-1gm9bb914db45", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm9bbv50rujh", "EWP分析-市场质量", "mfg-1gm9bbv50rujh", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm9bcptkorj9", "EWP分析-客户质量", "mfg-1gm9bcptkorj9", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm8v6gtd5d38", "整改组分析", "mfg-1gm8v6gtd5d38", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm8v74vl8eqa", "基础配置", "mfg-1gm8v74vl8eqa", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm8v784nqtnr", "EWP分析规则", "mfg-1gm8v784nqtnr", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm8v7fcvcdrs", "整改组分析规则", "mfg-1gm8v7fcvcdrs", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gm8v7mhp4bf5", "原因分类", "mfg-1gm8v7mhp4bf5", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h6he8pnbo9l9", "问题管理", "mfg-1h6he8pnbo9l9", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gae1kma472ld", "标签云V2.0", "mfg-1gae1kma472ld", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gae1kvrnemkm", "数据中心", "mfg-1gae1kvrnemkm", "doc"),
    ("https://docs.cvte.com/docs/mfg/csb-sop", "系统管理", "csb-sop", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gb2cu71c8qa8", "新增试图页面", "mfg-1gb2cu71c8qa8", "doc"),
    ("https://docs.cvte.com/docs/mfg/jjfa", "场景解决方案", "jjfa", "cat"),
    ("https://docs.cvte.com/docs/mfg/qms-jjfa", "质量管理", "qms-jjfa", "cat"),
    ("https://docs.cvte.com/docs/mfg/wms-solution", "仓储管理", "wms-solution", "cat"),
    ("https://docs.cvte.com/docs/mfg/faq", "常见问题-FAQ", "faq", "cat"),
    ("https://docs.cvte.com/docs/mfg/qms-faq", "质量管理", "qms-faq", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gah4lqvt62g3", "IQC\\IPQC\\OQC是什么？", "mfg-1gah4lqvt62g3", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gjibc40j7em1", "质量策划", "mfg-1gjibc40j7em1", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gjibcahmbni1", "客户质量管理", "mfg-1gjibcahmbni1", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gjibchernv1b", "研发质量管理", "mfg-1gjibchernv1b", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gjibcoo1spev", "物料质量管理", "mfg-1gjibcoo1spev", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gjibcua0g4n5", "制程质量管理", "mfg-1gjibcua0g4n5", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gjibe4r3hm2l", "包装采集报检验批号为空", "mfg-1gjibe4r3hm2l", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gutrs4ak5kip", "接口问题", "mfg-1gutrs4ak5kip", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gutrsk78t113", "API接口调用", "mfg-1gutrsk78t113", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gs62r8182m0t", "OA", "mfg-1gs62r8182m0t", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gs62ri3utv7a", "知识中心", "mfg-1gs62ri3utv7a", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h37gs0s24hvs", "知识中心权限申请受理", "mfg-1h37gs0s24hvs", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1gs62sc13fmao", "知识中心已有文件查询权限开通", "mfg-1gs62sc13fmao", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h37fhlqiigat", "文件作废", "mfg-1h37fhlqiigat", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h37flr0iukuc", "文件版本升级", "mfg-1h37flr0iukuc", "doc"),
    ("https://docs.cvte.com/docs/mfg/wms-faq", "仓储管理", "wms-faq", "cat"),
    ("https://docs.cvte.com/docs/mfg/wms-faq-rcv", "入库管理", "wms-faq-rcv", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h2n10gp9g66h", "条码重复入库", "mfg-1h2n10gp9g66h", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9gn2nj5g2iu", "单据不允许重复接收", "mfg-1h9gn2nj5g2iu", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9gpct9q9obr", "存储周期未配置", "mfg-1h9gpct9q9obr", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9gqemv8otac", "禁用料不允许接收", "mfg-1h9gqemv8otac", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9h4kdvn71f7", "前置存储周期为空", "mfg-1h9h4kdvn71f7", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9h581chsmfh", "非CKD物料超期不能接收", "mfg-1h9h581chsmfh", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9h7cu2o3njf", "未配置首次安全周期", "mfg-1h9h7cu2o3njf", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9h8edort1pj", "供应商编码为空", "mfg-1h9h8edort1pj", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9h905tcoeoq", "DC解析错误", "mfg-1h9h905tcoeoq", "doc"),
    ("https://docs.cvte.com/docs/mfg/wms-faq-warehouseMan", "库内管理", "wms-faq-warehouseMan", "cat"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9hll6qd9ifv", "创建任务后不允许合单", "mfg-1h9hll6qd9ifv", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9hmbt2se77o", "PDA重复扫码", "mfg-1h9hmbt2se77o", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9j2g5qj0a43", "拣料下架组织不匹配", "mfg-1h9j2g5qj0a43", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9j3balg6srl", "扫描下架子库不匹配", "mfg-1h9j3balg6srl", "doc"),
    ("https://docs.cvte.com/docs/mfg/mfg-1h9j5b0en9kvs", "扫描条码不存在", "mfg-1h9j5b0en9kvs", "doc"),
]

with open('/Users/linlin/IdeaProjects/myapp/scripts/crawled_docs.json') as f:
    crawled = json.load(f)

crawled_doc_ids = set()
crawled_urls = set()
for doc in crawled:
    url = doc.get('url', '').split('?')[0]
    crawled_urls.add(url)
    crawled_doc_ids.add(doc.get('doc_id', ''))

print("=" * 70)
print("  SIDEBAR vs CRAWLED DOCS COMPARISON")
print("=" * 70)
print()

# Separate categories and documents
cats = [(u, t, d, ty) for u, t, d, ty in sidebar_links if ty == "cat"]
docs = [(u, t, d, ty) for u, t, d, ty in sidebar_links if ty == "doc"]

print(f"Sidebar total links: {len(sidebar_links)}")
print(f"  Category/folder pages: {len(cats)}")
print(f"  Content document pages: {len(docs)}")
print(f"Crawled docs: {len(crawled)}")
print()

# Find missing documents
missing_docs = []
for url, title, doc_id, dtype in sidebar_links:
    url_clean = url.split('?')[0]
    if url_clean not in crawled_urls:
        missing_docs.append((url, title, doc_id, dtype))

missing_content = [(u, t, d, ty) for u, t, d, ty in missing_docs if ty == "doc"]
missing_cats = [(u, t, d, ty) for u, t, d, ty in missing_docs if ty == "cat"]

print(f"NOT crawled - Content docs: {len(missing_content)}")
print(f"NOT crawled - Category pages: {len(missing_cats)}")
print()

print("--- MISSING CONTENT DOCUMENTS ---")
for url, title, doc_id, dtype in missing_content:
    print(f"  [MISSING] {title} | {doc_id}")
    print(f"            {url}")

print()
print("--- MISSING CATEGORY PAGES ---")
for url, title, doc_id, dtype in missing_cats:
    print(f"  [CAT-MISS] {title} | {doc_id}")

# Also check if crawled has docs NOT in sidebar
print()
print("--- CRAWLED BUT NOT IN SIDEBAR ---")
sidebar_doc_ids = set(d for _, _, d, _ in sidebar_links)
for doc in crawled:
    did = doc.get('doc_id', '')
    if did not in sidebar_doc_ids:
        print(f"  [EXTRA] {doc.get('title', '?')} | {did}")

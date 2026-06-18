package com.example.myapp.model;

/**
 * MinDoc 文档映射表实体（PDR V8.1 US-05）。
 * <p>
 * 对应数据库表 {@code mindoc_document_mapping}，用于追踪每篇 MinDoc 页面
 * 与 WISE 文件之间的对应关系，及同步状态。
 *
 * <pre>
 * document_key = project_name + "_" + url_hash
 * url_hash     = SHA256(normalized_url)[:12]
 * md_hash      = SHA256(markdown_content)[:8]
 * </pre>
 */
public class MindocDocumentMapping {

    /** 映射记录 ID（数据库自增主键） */
    private Long mappingId;

    /** MinDoc 空间或项目名称，如 MFG / QMS / MES / WMS */
    private String projectName;

    /** 文档唯一键: project_name + "_" + url_hash */
    private String documentKey;

    /** 原始 MinDoc URL，用于追溯来源 */
    private String sourceUrl;

    /** 规范化后的 URL，用于稳定匹配 */
    private String normalizedUrl;

    /** normalized_url 的 Hash (SHA256 前 12 位) */
    private String urlHash;

    /** MinDoc 文档 ID（可选，可为 null） */
    private String docId;

    /** 文档标题 */
    private String title;

    /** 转换后 Markdown 内容的 Hash (SHA256 前 8 位) */
    private String mdHash;

    /** 当前生效的 WISE 文件 ID */
    private String wiseFileId;

    /** 上一个版本的 WISE 文件 ID，用于回滚或下线 */
    private String oldWiseFileId;

    /** 同步状态:
     *  discovered | crawling | converted | uploading | processing |
     *  completed | skipped | failed | network_unreachable |
     *  auth_failed | convert_failed | upload_failed | parse_failed | expired */
    private String syncStatus;

    /** WISE 解析状态: processing / completed / failed */
    private String parseStatus;

    /** 最近一次在 MinDoc 中成功访问到的时间 */
    private String lastSeenTime;

    /** 最近同步时间 */
    private String lastSyncTime;

    /** 失败原因 */
    private String errorMessage;

    /** 是否已在本轮 MinDoc 巡检中失效 */
    private Boolean expired;

    // ─── Constructors ─────────────────────────────────────

    public MindocDocumentMapping() {}

    // ─── Builder-style factory ─────────────────────────────

    public static MindocDocumentMapping fromCrawlResult(String projectName, String sourceUrl,
                                                         String normalizedUrl, String urlHash,
                                                         String docId, String title, String mdHash) {
        MindocDocumentMapping m = new MindocDocumentMapping();
        m.projectName = projectName;
        m.documentKey = projectName + "_" + urlHash;
        m.sourceUrl = sourceUrl;
        m.normalizedUrl = normalizedUrl;
        m.urlHash = urlHash;
        m.docId = docId;
        m.title = title;
        m.mdHash = mdHash;
        m.syncStatus = "pending";
        return m;
    }

    // ─── Getters / Setters ─────────────────────────────────

    public Long getMappingId() { return mappingId; }
    public void setMappingId(Long mappingId) { this.mappingId = mappingId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDocumentKey() { return documentKey; }
    public void setDocumentKey(String documentKey) { this.documentKey = documentKey; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getNormalizedUrl() { return normalizedUrl; }
    public void setNormalizedUrl(String normalizedUrl) { this.normalizedUrl = normalizedUrl; }

    public String getUrlHash() { return urlHash; }
    public void setUrlHash(String urlHash) { this.urlHash = urlHash; }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMdHash() { return mdHash; }
    public void setMdHash(String mdHash) { this.mdHash = mdHash; }

    public String getWiseFileId() { return wiseFileId; }
    public void setWiseFileId(String wiseFileId) { this.wiseFileId = wiseFileId; }

    public String getOldWiseFileId() { return oldWiseFileId; }
    public void setOldWiseFileId(String oldWiseFileId) { this.oldWiseFileId = oldWiseFileId; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }

    public String getLastSeenTime() { return lastSeenTime; }
    public void setLastSeenTime(String lastSeenTime) { this.lastSeenTime = lastSeenTime; }

    public String getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(String lastSyncTime) { this.lastSyncTime = lastSyncTime; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean expired) { this.expired = expired; }
}

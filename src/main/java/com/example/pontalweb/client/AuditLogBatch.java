package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditLogBatch(
        Long batchId,
        String periodStart,
        String periodEnd,
        String status,
        Integer recordCount) {

    // JSP は getXxx() を呼ぶので、画面で使う分の getter を足す
    public Long getBatchId() { return batchId; }
    public String getStatus() { return status; }
    public String getPeriodStart() { return periodStart; }
    public String getPeriodEnd() { return periodEnd; }

    public Integer getRecordCount() { return recordCount; }

    // 画面に出す期間(例: 2026/10/05 〜 2026/10/11)
    public String getPeriodText() {
        return periodStart.replace("-", "/") + " 〜 " + periodEnd.replace("-", "/");
    }
}

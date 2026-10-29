package com.example.pontalweb.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//PontalのAPI GET /api/announcements のレスポンス(お知らせ配列、総件数、次のページ番号、前のページ番号)
//社員一覧(EmployeeListResponse)と同じ形
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnouncementListResponse(
        List<AnnouncementItem> items,
        int totalCount,
        Integer next,
        Integer prev) {
}

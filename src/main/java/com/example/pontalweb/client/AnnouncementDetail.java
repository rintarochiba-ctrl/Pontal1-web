package com.example.pontalweb.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

//PontalのAPI GET /api/announcements/{id} のレスポンス(お知らせ1件の詳細。一覧の項目 + 本文・更新日時・操作できるか)
//一覧の1件分(AnnouncementItem)と、本文などが加わった形。recordは、ほかのクラスを継承できないので、項目を、全部書いている
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnouncementDetail(
        Long id,
        String title,
        String category,
        @JsonProperty("isImportant") boolean important,
        String senderDepartment,
        String targetType,
        String targetLabel,
        String createdAt,
        String body,
        String updatedAt,
        //ログイン中の人が、編集・削除できるか(画面のボタンを出すかの判断に使う。実際の権限チェックは、API側が行う)
        boolean canEdit,
        boolean canDelete) {

    private static final DateTimeFormatter TEXT_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    //JSPのEL(${announcement.title} など)は、getXxx()を呼ぶので、recordでも、getterを書いておく
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public boolean isImportant() { return important; }
    public String getSenderDepartment() { return senderDepartment; }
    public String getTargetType() { return targetType; }
    public String getTargetLabel() { return targetLabel; }
    public String getBody() { return body; }
    public boolean isCanEdit() { return canEdit; }
    public boolean isCanDelete() { return canDelete; }

    //画面に出す日時(2026/10/22 10:30)。読めない形のときは、届いた文字のまま出す
    public String getCreatedAtText() {
        if (createdAt == null || createdAt.isBlank()) {
            return "";
        }
        try {
            return LocalDateTime.parse(createdAt).format(TEXT_FORMAT);
        } catch (DateTimeParseException e) {
            return createdAt;
        }
    }
}

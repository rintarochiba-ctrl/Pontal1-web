package com.example.pontalweb.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

//PontalのAPI GET /api/announcements の items の1件分(お知らせ一覧の1行。本文は含まない)
//APIのJSONの項目名は isImportant なので、@JsonProperty で、その名前に合わせる(MeResponseと同じ)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnouncementItem(
        Long id,
        String title,
        String category,
        @JsonProperty("isImportant") boolean important,
        String senderDepartment,
        String targetType,
        //宛先の表示名(全員向けなら「全員」、部署宛てなら部署名、個人宛てなら社員名)
        String targetLabel,
        //APIからは「2026-10-22T10:30:00」のような文字列で届く
        String createdAt) {

    private static final DateTimeFormatter TEXT_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    //JSPのEL(${a.title} など)は、getXxx()を呼ぶので、recordでも、getterを書いておく
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public boolean isImportant() { return important; }
    public String getSenderDepartment() { return senderDepartment; }
    public String getTargetType() { return targetType; }
    public String getTargetLabel() { return targetLabel; }

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

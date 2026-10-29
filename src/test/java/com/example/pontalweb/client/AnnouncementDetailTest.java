package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

//AnnouncementDetail のテスト。APIのJSONを読み取れることと、画面に出す日時の形を確認する
class AnnouncementDetailTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String JSON = """
            {"id":7,"title":"年末調整の書類提出のお願い","category":"対応依頼","isImportant":true,
             "senderDepartment":"総務部","targetType":"DEPARTMENT","targetLabel":"開発部",
             "createdAt":"2026-10-22T10:30:00","body":"1行目\\n2行目","updatedAt":"2026-10-23T09:00:00",
             "canEdit":true,"canDelete":false,"unknownField":"無視される"}
            """;

    @Test
    void readsAllFields_fromApiJson() throws Exception {
        AnnouncementDetail d = mapper.readValue(JSON, AnnouncementDetail.class);

        assertThat(d.getId()).isEqualTo(7L);
        assertThat(d.getTitle()).isEqualTo("年末調整の書類提出のお願い");
        assertThat(d.getCategory()).isEqualTo("対応依頼");
        assertThat(d.isImportant()).isTrue(); //JSONの名前は isImportant
        assertThat(d.getSenderDepartment()).isEqualTo("総務部");
        assertThat(d.getTargetLabel()).isEqualTo("開発部");
        assertThat(d.getBody()).isEqualTo("1行目\n2行目"); //本文の改行は、そのまま残る
        assertThat(d.isCanEdit()).isTrue();
        assertThat(d.isCanDelete()).isFalse();
    }

    @Test
    void createdAtText_isFormattedForScreen() throws Exception {
        assertThat(mapper.readValue(JSON, AnnouncementDetail.class).getCreatedAtText()).isEqualTo("2026/10/22 10:30");
    }

    @Test
    void createdAtText_isEmpty_whenMissing() throws Exception {
        AnnouncementDetail d = mapper.readValue(JSON.replace("\"createdAt\":\"2026-10-22T10:30:00\",", ""),
                AnnouncementDetail.class);

        assertThat(d.getCreatedAtText()).isEmpty();
    }
}

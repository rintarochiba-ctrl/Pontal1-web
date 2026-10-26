package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

//AnnouncementItem / AnnouncementListResponse のテスト。APIのJSONを読み取れることと、画面に出す日時の形を確認する
class AnnouncementItemTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String ITEM_JSON = """
            {"id":7,"title":"年末調整の書類提出のお願い","category":"対応依頼","isImportant":true,
             "senderDepartment":"総務部","targetType":"DEPARTMENT","targetLabel":"開発部",
             "createdAt":"2026-10-22T10:30:00","unknownField":"無視される"}
            """;

    // ---------- JSONの読み取り ----------

    @Test
    void readsAllFields_fromApiJson_andIgnoresUnknownFields() throws Exception {
        AnnouncementItem item = mapper.readValue(ITEM_JSON, AnnouncementItem.class);

        assertThat(item.getId()).isEqualTo(7L);
        assertThat(item.getTitle()).isEqualTo("年末調整の書類提出のお願い");
        assertThat(item.getCategory()).isEqualTo("対応依頼");
        assertThat(item.isImportant()).isTrue(); //JSONの名前は isImportant
        assertThat(item.getSenderDepartment()).isEqualTo("総務部");
        assertThat(item.getTargetType()).isEqualTo("DEPARTMENT");
        assertThat(item.getTargetLabel()).isEqualTo("開発部");
    }

    @Test
    void notImportant_whenFalse() throws Exception {
        AnnouncementItem item = mapper.readValue(ITEM_JSON.replace("\"isImportant\":true", "\"isImportant\":false"),
                AnnouncementItem.class);

        assertThat(item.isImportant()).isFalse();
    }

    @Test
    void readsListResponse_withPaging() throws Exception {
        String json = "{\"items\":[" + ITEM_JSON + "],\"totalCount\":12,\"next\":1,\"prev\":null}";

        AnnouncementListResponse response = mapper.readValue(json, AnnouncementListResponse.class);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalCount()).isEqualTo(12);
        assertThat(response.next()).isEqualTo(1);
        assertThat(response.prev()).isNull();
    }

    // ---------- 画面に出す日時 ----------

    @Test
    void createdAtText_isFormattedForScreen() {
        assertThat(item("2026-10-22T10:30:00").getCreatedAtText()).isEqualTo("2026/10/22 10:30");
    }

    @Test
    void createdAtText_ignoresSecondsAndFraction() {
        //DBの時刻は、小数点以下の秒がついて届くことがある
        assertThat(item("2026-10-22T10:30:45.123456").getCreatedAtText()).isEqualTo("2026/10/22 10:30");
    }

    @Test
    void createdAtText_isZeroPadded() {
        assertThat(item("2026-05-01T09:05:00").getCreatedAtText()).isEqualTo("2026/05/01 09:05");
    }

    @Test
    void createdAtText_isEmpty_whenMissing() {
        assertThat(item(null).getCreatedAtText()).isEmpty();
        assertThat(item("").getCreatedAtText()).isEmpty();
    }

    @Test
    void createdAtText_showsRawText_whenUnreadable() {
        assertThat(item("昨日").getCreatedAtText()).isEqualTo("昨日");
    }

    private AnnouncementItem item(String createdAt) {
        return new AnnouncementItem(1L, "t", "技術", false, "総務部", "ALL", "全員", createdAt);
    }
}

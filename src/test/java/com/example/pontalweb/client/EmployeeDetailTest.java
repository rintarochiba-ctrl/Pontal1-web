package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

//EmployeeDetailのテスト。PontalのAPI(GET /api/employees/{id})のJSONの読み取りと、画面用の表示文字を確認する
class EmployeeDetailTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EmployeeDetail withSelfQa(String selfQa) {
        return new EmployeeDetail(1L, "山田太郎", "test@example.com", "開発部", "エンジニア",
                "2018-04-01", "男性", 38, "東京都", null, null, selfQa, null, false, false);
    }

    @Test
    void parsesPontalApiJson() throws Exception {
        String json = "{\"id\":1,\"name\":\"山田太郎\",\"email\":\"test@example.com\",\"department\":\"開発部\","
                + "\"position\":\"エンジニア\",\"joinDate\":\"2018-04-01\",\"age\":38,\"isSystemAdmin\":true}";

        EmployeeDetail detail = objectMapper.readValue(json, EmployeeDetail.class);

        assertThat(detail.getName()).isEqualTo("山田太郎");
        assertThat(detail.getDepartment()).isEqualTo("開発部");
        assertThat(detail.getAgeText()).isEqualTo("38歳");
        assertThat(detail.getInitial()).isEqualTo("山");
    }

    @Test
    void parsesImageAndRoleFields() throws Exception {
        //編集で、全項目を送り直すときに、画像と権限を落とさないため、読み取れていること
        String json = "{\"id\":1,\"name\":\"山田太郎\",\"imageUrl\":\"employees/1.png\",\"isSystemAdmin\":true,\"isHrAdmin\":false}";

        EmployeeDetail detail = objectMapper.readValue(json, EmployeeDetail.class);

        assertThat(detail.getImageUrl()).isEqualTo("employees/1.png");
        assertThat(detail.isSystemAdmin()).isTrue();
        assertThat(detail.isHrAdmin()).isFalse();
    }

    @Test
    void showsDashWhenAgeAndJoinDateAreMissing() {
        EmployeeDetail detail = new EmployeeDetail(1L, "山田太郎", null, null, null, null, null, null, null, null, null, null, null, false, false);

        assertThat(detail.getAgeText()).isEqualTo("-");
        assertThat(detail.getJoinDateText()).isEqualTo("-");
    }

    @Test
    void joinDateTextHasYearsOfService() {
        assertThat(withSelfQa(null).getJoinDateText()).startsWith("2018年4月1日 (勤続").endsWith("年)");
    }

    @Test
    void parsesSelfQaItems() {
        String selfQa = "[{\"question\":\"休日の過ごし方は?\",\"answer\":\"キャンプです\"},"
                + "{\"question\":\"ハマっていることは?\",\"answer\":\"コーヒー\"}]";

        assertThat(withSelfQa(selfQa).getSelfQaItems())
                .containsExactly(new SelfQaItem("休日の過ごし方は?", "キャンプです"),
                        new SelfQaItem("ハマっていることは?", "コーヒー"));
    }

    @Test
    void selfQaItemsAreLimitedToThree() {
        String selfQa = "[{\"question\":\"1\",\"answer\":\"a\"},{\"question\":\"2\",\"answer\":\"b\"},"
                + "{\"question\":\"3\",\"answer\":\"c\"},{\"question\":\"4\",\"answer\":\"d\"}]";

        assertThat(withSelfQa(selfQa).getSelfQaItems()).hasSize(3);
    }

    @Test
    void selfQaItemsIgnoreEmptyPairs() {
        String selfQa = "[{\"question\":\"\",\"answer\":\"\"},{\"question\":\"1\",\"answer\":\"a\"}]";

        assertThat(withSelfQa(selfQa).getSelfQaItems()).containsExactly(new SelfQaItem("1", "a"));
    }

    @Test
    void selfQaItemsAreEmptyWhenNotJson() {
        //JSONでない、ふつうの文字や、空のときは、空のリスト(画面は、文字をそのまま出す)
        assertThat(withSelfQa("休日はキャンプです").getSelfQaItems()).isEmpty();
        assertThat(withSelfQa("").getSelfQaItems()).isEmpty();
        assertThat(withSelfQa(null).getSelfQaItems()).isEmpty();
    }

    @Test
    void hasProfileTextWhenOnlySelfQaExists() {
        assertThat(withSelfQa("休日はキャンプです").isHasProfileText()).isTrue();
        assertThat(withSelfQa(null).isHasProfileText()).isFalse();
    }
}

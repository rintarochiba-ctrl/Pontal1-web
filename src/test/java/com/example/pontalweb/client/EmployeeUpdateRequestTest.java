package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

//EmployeeUpdateRequestのテスト。PontalのAPI(PUT /api/employees/{id})に送るJSONの項目名が、APIの決まりと一致することを確認する
//(名前がずれると、権限が外れたり、画像・Q&Aが消えたりする。APIは、全項目を丸ごと置き換えるため)
class EmployeeUpdateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesWithApiFieldNames() throws Exception {
        EmployeeUpdateRequest request = new EmployeeUpdateRequest("山田太郎", "男性", 38, "東京都", "2018-04-01",
                "開発部", "エンジニア", "employees/3.png", "自己PR", "キャンプ", "[{\"question\":\"Q\",\"answer\":\"A\"}]",
                true, false);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("name").asText()).isEqualTo("山田太郎");
        assertThat(json.get("gender").asText()).isEqualTo("男性");
        assertThat(json.get("age").asInt()).isEqualTo(38);
        assertThat(json.get("birthplace").asText()).isEqualTo("東京都");
        assertThat(json.get("joinDate").asText()).isEqualTo("2018-04-01");
        assertThat(json.get("department").asText()).isEqualTo("開発部");
        assertThat(json.get("position").asText()).isEqualTo("エンジニア");
        assertThat(json.get("image").asText()).isEqualTo("employees/3.png");
        assertThat(json.get("bio").asText()).isEqualTo("自己PR");
        assertThat(json.get("hobby").asText()).isEqualTo("キャンプ");
        assertThat(json.get("selfQa").asText()).isEqualTo("[{\"question\":\"Q\",\"answer\":\"A\"}]");
        assertThat(json.get("isSystemAdmin").asBoolean()).isTrue();
        assertThat(json.get("isHrAdmin").asBoolean()).isFalse();
        //Javaの項目名のまま、送ってはいけない
        assertThat(json.has("systemAdmin")).isFalse();
        assertThat(json.has("hrAdmin")).isFalse();
    }
}

package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

//EmployeeCreateRequestのテスト。PontalのAPI(POST /api/employees)に送るJSONの項目名が、APIの決まりと一致することを確認する
//(名前がずれると、権限が、黙って false のまま登録されてしまう)
class EmployeeCreateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesWithApiFieldNames() throws Exception {
        EmployeeCreateRequest request = new EmployeeCreateRequest("鈴木三郎", "s.suzuki@example.com", "Temp#Pass123",
                "2026-04-01", "開発部", "メンバー", true, false);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("name").asText()).isEqualTo("鈴木三郎");
        assertThat(json.get("email").asText()).isEqualTo("s.suzuki@example.com");
        assertThat(json.get("initialPassword").asText()).isEqualTo("Temp#Pass123");
        assertThat(json.get("joinDate").asText()).isEqualTo("2026-04-01");
        assertThat(json.get("department").asText()).isEqualTo("開発部");
        assertThat(json.get("position").asText()).isEqualTo("メンバー");
        assertThat(json.get("isSystemAdmin").asBoolean()).isTrue();
        assertThat(json.get("isHrAdmin").asBoolean()).isFalse();
        //Javaの項目名(systemAdmin / hrAdmin)のまま、送ってはいけない
        assertThat(json.has("systemAdmin")).isFalse();
        assertThat(json.has("hrAdmin")).isFalse();
    }

    @Test
    void toStringDoesNotShowPassword() {
        EmployeeCreateRequest request = new EmployeeCreateRequest("鈴木三郎", "s.suzuki@example.com", "Temp#Pass123",
                "2026-04-01", "開発部", null, false, false);

        assertThat(request.toString()).doesNotContain("Temp#Pass123");
    }
}

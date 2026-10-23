package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

//EmployeeListResponse / EmployeeSummary のテスト。PontalのAPI(GET /api/employees)のJSONを、正しく読み取れることを確認する
class EmployeeListResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesPontalApiJson() throws Exception {
        String json = """
                {"items":[
                  {"id":1,"name":"山田太郎","email":"test@example.com","department":"開発部","position":"部長"},
                  {"id":2,"name":"佐藤花子","email":"sato@example.com","department":"人事部","position":"課長"}],
                 "totalCount":9,"next":1,"prev":null}
                """;

        EmployeeListResponse response = objectMapper.readValue(json, EmployeeListResponse.class);

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(9);
        assertThat(response.next()).isEqualTo(1);
        assertThat(response.prev()).isNull();
        EmployeeSummary first = response.items().get(0);
        assertThat(first.id()).isEqualTo(1L);
        assertThat(first.name()).isEqualTo("山田太郎");
        assertThat(first.department()).isEqualTo("開発部");
        assertThat(first.position()).isEqualTo("部長");
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        String json = "{\"items\":[],\"totalCount\":0,\"next\":null,\"prev\":null,\"extra\":\"x\"}";

        assertThat(objectMapper.readValue(json, EmployeeListResponse.class).totalCount()).isZero();
    }

    @Test
    void nullTotalCount_becomesZero() throws Exception {
        String json = "{\"items\":[],\"totalCount\":null,\"next\":null,\"prev\":null}";

        assertThat(objectMapper.readValue(json, EmployeeListResponse.class).totalCount()).isZero();
    }

    // ---------- JSPのEL式が使う getter(recordの accessor は、EL式から見えないため、getterを別に持つ) ----------

    @Test
    void summary_hasGettersForJsp() {
        EmployeeSummary e = new EmployeeSummary(5L, "山田太郎", "a@example.com", "開発部", "部長");

        assertThat(e.getId()).isEqualTo(5L);
        assertThat(e.getName()).isEqualTo("山田太郎");
        assertThat(e.getDepartment()).isEqualTo("開発部");
        assertThat(e.getPosition()).isEqualTo("部長");
        assertThat(e.getInitial()).isEqualTo("山");
    }

    @Test
    void summary_initial_isEmpty_whenNameIsNullOrEmpty() {
        assertThat(new EmployeeSummary(1L, null, null, null, null).getInitial()).isEmpty();
        assertThat(new EmployeeSummary(1L, "", null, null, null).getInitial()).isEmpty();
    }
}

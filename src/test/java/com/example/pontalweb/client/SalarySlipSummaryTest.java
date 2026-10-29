package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

//SalarySlipSummaryのテスト。PontalのAPI(給与明細一覧)のJSONの読み取りと、画面用の表示文字を確認する
class SalarySlipSummaryTest {

    @Test
    void parsesPontalApiJsonList() throws Exception {
        String json = "[{\"id\":3,\"payMonth\":\"2024-09\"},{\"id\":2,\"payMonth\":\"2024-08\",\"extra\":1}]";

        List<SalarySlipSummary> slips = new ObjectMapper().readValue(json, new TypeReference<>() { });

        assertThat(slips).containsExactly(new SalarySlipSummary(3L, "2024-09"), new SalarySlipSummary(2L, "2024-08"));
        assertThat(slips.get(0).getId()).isEqualTo(3L);
        assertThat(slips.get(0).getPayMonth()).isEqualTo("2024-09");
    }

    @Test
    void payMonthText_formatsYearAndMonth() {
        assertThat(new SalarySlipSummary(1L, "2024-09").getPayMonthText()).isEqualTo("2024年09月支給分");
        assertThat(new SalarySlipSummary(1L, "2025-12").getPayMonthText()).isEqualTo("2025年12月支給分");
    }

    @Test
    void payMonthText_showsAsIsWhenFormatIsUnexpected_andDashWhenMissing() {
        assertThat(new SalarySlipSummary(1L, "2024/09").getPayMonthText()).isEqualTo("2024/09");
        assertThat(new SalarySlipSummary(1L, null).getPayMonthText()).isEqualTo("-");
    }
}

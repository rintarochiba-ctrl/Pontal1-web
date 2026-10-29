package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//PontalのAPI GET /api/employees/{employeeId}/salary-slips のレスポンスの、1件分(給与明細の、id と、対象年月)
//JSP(EL式)は getXxx() で値を取るので、画面で使う分は getter を持たせる(record の id() では取れない)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalarySlipSummary(Long id, String payMonth) {

    public Long getId() { return id; }
    public String getPayMonth() { return payMonth; }

    //画面に出す、支給年月("2024-09" → "2024年09月支給分")。読めない形なら、そのまま出す
    public String getPayMonthText() {
        if (payMonth != null && payMonth.matches("\\d{4}-\\d{2}")) {
            return payMonth.substring(0, 4) + "年" + payMonth.substring(5) + "月支給分";
        }
        return payMonth == null ? "-" : payMonth;
    }
}

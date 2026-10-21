package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//PontalのAPI POST /api/salary-slips(給与明細のアップロード)の成功のレスポンス
//(登録・上書きした明細の id、対象の社員のid、対象年月)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalarySlipUploadResult(Long id, Long employeeId, String payMonth) {
}

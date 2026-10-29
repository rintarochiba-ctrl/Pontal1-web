package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//PontalのAPI GET /api/salary-slips/history(アップロード履歴)の、1件分
//誰が(uploadedBy)・いつ(uploadedAt)・誰の分(employeeId)の・何年月(payMonth)を、アップロードしたか
//社員は、名前ではなく、id だけで返る(画面で、名前に直す)。uploadedAt は、"2026-10-21T12:34:56" のような文字
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalarySlipUploadHistory(Long slipId, Long employeeId, String payMonth, Long uploadedBy, String uploadedAt) {
}

package com.example.pontalweb.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import com.example.pontalweb.client.AuditLogBatch;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;

@Controller
public class AuditLogController {
    private final PontalApiClient api;

    public AuditLogController(PontalApiClient api) {
        this.api = api;
    }

    @GetMapping("/audit-logs")
    public String index() {
        return "audit-history";
    }

    @GetMapping("/audit-logs/exports")
    public String exports(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {
        // 年月が指定されていなければ、今月にする
        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();

        List<AuditLogBatch> batches = api.get(
                "/api/audit-log-batches?year={year}&month={month}",
                new ParameterizedTypeReference<List<AuditLogBatch>>() {
                },
                y, m);

        int thisYear = today.getYear();
        model.addAttribute("years", List.of(thisYear, thisYear - 1, thisYear - 2));
        model.addAttribute("batches", batches);
        model.addAttribute("year", y);
        model.addAttribute("month", m);
        return "audit-exports";
    }

    @GetMapping("/audit-logs/exports/{batchId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long batchId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        byte[] csv = api.getBytes("/api/audit-log-batches/{batchId}/download", batchId);
        if (csv == null) {
            throw new ApiServerException("監査ログのCSVを取得できませんでした");
        }

        // ファイル名: 期間が正しい形なら期間つき、そうでなければ id で代用する
        String fileName = isDate(start) && isDate(end)
                ? "audit-log_" + start + "_" + end + ".csv"
                : "audit-log_" + batchId + ".csv";

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }

    // "2026-10-05" のような、年-月-日の形かを確かめる(ファイル名に、変な文字を入れさせないため)
    private static boolean isDate(String s) {
        return s != null && s.matches("\\d{4}-\\d{2}-\\d{2}");
    }

}

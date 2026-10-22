package com.example.pontalweb.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import com.example.pontalweb.client.AuditLogBatch;
import com.example.pontalweb.client.PontalApiClient;


@Controller
public class AuditLogController {
    private final PontalApiClient api;

    public AuditLogController(PontalApiClient api){
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
                new ParameterizedTypeReference<List<AuditLogBatch>>() { },
                y, m);

        int thisYear = today.getYear();
        model.addAttribute("years", List.of(thisYear, thisYear - 1, thisYear - 2));
        model.addAttribute("batches", batches);
        model.addAttribute("year", y);
        model.addAttribute("month", m);
        return "audit-exports";
    }
}

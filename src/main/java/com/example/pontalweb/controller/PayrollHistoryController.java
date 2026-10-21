package com.example.pontalweb.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.client.EmployeeOptionPage;
import com.example.pontalweb.client.EmployeeOptions;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipSummary;
import com.example.pontalweb.client.SalarySlipUploadHistory;
import com.example.pontalweb.model.UploadHistoryRow;

//給与明細のアップロード履歴(HR管理者だけ。/payroll/** は、SecurityConfigで制限)
//誰が・いつ・誰の分の・何年月を、アップロードしたか。新しい順
@Controller
public class PayrollHistoryController {

    private static final int PAGE_SIZE = 20;
    private static final String HISTORY_URI = "/api/salary-slips/history?page={page}&size={size}";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final PontalApiClient api;

    public PayrollHistoryController(PontalApiClient api) {
        this.api = api;
    }

    @GetMapping("/payroll/history")
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        int current = Math.max(page, 0);
        List<SalarySlipUploadHistory> items = fetch(current);

        //APIは、「次のページがあるか」を教えない(ただの配列)。1ページ分いっぱいに返ってきたときだけ、
        //次のページを、1回取ってみて、1件でもあれば、「次へ」を出す(ちょうど割り切れる件数でも、空のページに進まない)
        boolean hasNext = items.size() >= PAGE_SIZE && !fetch(current + 1).isEmpty();

        //APIは、社員を、名前ではなく、id で返す。社員の一覧(全員分)から、名前に直す(履歴が空のときは、取らない)
        Map<Long, String> names = items.isEmpty() ? Map.of() : nameById(EmployeeOptions.loadAll(api));
        List<UploadHistoryRow> rows = items.stream()
                .map(h -> new UploadHistoryRow(h.employeeId(), nameOf(names, h.employeeId()),
                        payMonthText(h.payMonth()), nameOf(names, h.uploadedBy()), formatDateTime(h.uploadedAt())))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("page", current);
        model.addAttribute("hasPrev", current > 0);
        model.addAttribute("hasNext", hasNext);
        return "payroll-history";
    }

    private List<SalarySlipUploadHistory> fetch(int page) {
        List<SalarySlipUploadHistory> items = api.get(HISTORY_URI,
                new ParameterizedTypeReference<List<SalarySlipUploadHistory>>() { }, page, PAGE_SIZE);
        return items == null ? List.of() : items;
    }

    private static Map<Long, String> nameById(List<EmployeeOptionPage.Option> options) {
        Map<Long, String> names = new HashMap<>();
        for (EmployeeOptionPage.Option option : options) {
            names.put(option.id(), option.name());
        }
        return names;
    }

    //社員の名前。一覧にいない(削除された、など)ときは、id を添えて出す。idが無いときは、「-」
    static String nameOf(Map<Long, String> names, Long id) {
        if (id == null) {
            return "-";
        }
        String name = names.get(id);
        return name != null ? name : "不明な社員(ID: " + id + ")";
    }

    //日時の表示("2026-10-21T12:34:56.789" → "2026/10/21 12:34")。読めない形なら、そのまま出す
    static String formatDateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "-";
        }
        try {
            return LocalDateTime.parse(iso).format(DATE_TIME);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    //対象年月の表示("2024-09" → "2024年09月支給分")。給与明細の一覧と、同じ表し方(SalarySlipSummaryの表示を使い回す)
    private static String payMonthText(String payMonth) {
        return new SalarySlipSummary(null, payMonth).getPayMonthText();
    }
}

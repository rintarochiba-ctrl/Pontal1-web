package com.example.pontalweb.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.client.EmployeeOptions;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipSummary;

//給与管理(HR管理者だけ。SecurityConfigで制限)。社員を選んで、その人の給与明細の一覧を見る
//  ・上の「社員の選択」: 社員の一覧を、APIから全員分取って、プルダウンにする
//  ・「年月の絞り込み」: APIは、その人の全明細を返す(多くても、1年に12件)ので、画面の側(ここ)で絞る
@Controller
public class PayrollController {

    private final PontalApiClient api;

    public PayrollController(PontalApiClient api) {
        this.api = api;
    }

    @GetMapping("/payroll")
    public String payroll(@RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "") String year,
            @RequestParam(defaultValue = "") String month,
            Model model) {
        model.addAttribute("options", EmployeeOptions.loadAll(api));
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("year", year);
        model.addAttribute("month", month);

        //社員が選ばれていなければ、一覧は出さない
        if (employeeId != null) {
            List<SalarySlipSummary> all = api.get("/api/employees/{id}/salary-slips",
                    new ParameterizedTypeReference<List<SalarySlipSummary>>() { }, employeeId);
            if (all == null) {
                all = List.of();
            }
            model.addAttribute("hasSlips", !all.isEmpty());
            model.addAttribute("years", yearsOf(all));
            model.addAttribute("slips", filter(all, year, month));
        }
        return "payroll";
    }

    //その人の明細にある、年の一覧(新しい年が先。絞り込みのプルダウンの候補)
    static List<String> yearsOf(List<SalarySlipSummary> slips) {
        return slips.stream()
                .map(SalarySlipSummary::payMonth)
                .filter(m -> m != null && m.matches("\\d{4}-\\d{2}"))
                .map(m -> m.substring(0, 4))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    //年・月で、絞り込む。空のときは、その条件で絞らない。形が正しくない値(YYYY、01〜12以外)も、無視する
    static List<SalarySlipSummary> filter(List<SalarySlipSummary> slips, String year, String month) {
        boolean byYear = year.matches("\\d{4}");
        boolean byMonth = month.matches("0[1-9]|1[0-2]");
        return slips.stream()
                .filter(s -> s.payMonth() != null)
                .filter(s -> !byYear || s.payMonth().startsWith(year + "-"))
                .filter(s -> !byMonth || s.payMonth().endsWith("-" + month))
                .toList();
    }
}

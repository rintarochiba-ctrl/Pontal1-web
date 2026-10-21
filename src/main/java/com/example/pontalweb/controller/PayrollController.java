package com.example.pontalweb.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.client.EmployeeOptionPage;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipSummary;

//給与管理(HR管理者だけ。SecurityConfigで制限)。社員を選んで、その人の給与明細の一覧を見る
//  ・上の「社員の選択」: 社員の一覧を、APIから全員分取って、プルダウンにする
//  ・「年月の絞り込み」: APIは、その人の全明細を返す(多くても、1年に12件)ので、画面の側(ここ)で絞る
@Controller
public class PayrollController {

    private static final int PAGE_SIZE = 100; //社員一覧APIの、1回の最大件数
    private static final int MAX_PAGES = 20;  //全員を取るときに、ページをたどる回数の上限(無限に繰り返さないため)

    private final PontalApiClient api;

    public PayrollController(PontalApiClient api) {
        this.api = api;
    }

    @GetMapping("/payroll")
    public String payroll(@RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "") String year,
            @RequestParam(defaultValue = "") String month,
            Model model) {
        model.addAttribute("options", loadAllEmployees());
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

    //社員を、全員分取る(1回100件ずつ、次のページが無くなるまで)
    private List<EmployeeOptionPage.Option> loadAllEmployees() {
        List<EmployeeOptionPage.Option> options = new ArrayList<>();
        int page = 0;
        for (int i = 0; i < MAX_PAGES; i++) {
            EmployeeOptionPage result = api.get("/api/employees?page={page}&size={size}",
                    EmployeeOptionPage.class, page, PAGE_SIZE);
            if (result == null || result.items() == null) {
                break;
            }
            options.addAll(result.items());
            if (result.next() == null) {
                break;
            }
            page = result.next();
        }
        return options;
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

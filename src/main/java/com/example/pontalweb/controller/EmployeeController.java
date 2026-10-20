package com.example.pontalweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.client.EmployeeDetail;
import com.example.pontalweb.client.EmployeeListResponse;
import com.example.pontalweb.client.PontalApiClient;

@Controller 
public class EmployeeController {
    
    private static final int PAGE_SIZE = 8;

    private final PontalApiClient api;

    public EmployeeController(PontalApiClient api){
        this.api = api;
    }

    @GetMapping("/employees")
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String keyword,
            Model model){
        String uri = "/api/employees?page={page}&size={size}";
        EmployeeListResponse response;
        if (keyword.isBlank()) {
            response = api.get(uri, EmployeeListResponse.class, page, PAGE_SIZE);
        } else {
            //keyword は、入力があるときだけ、APIに渡す
            response = api.get(uri + "&keyword={keyword}", EmployeeListResponse.class,
                    page, PAGE_SIZE, keyword.trim());
        }

        model.addAttribute("employees", response.items());
        model.addAttribute("totalCount", response.totalCount());
        model.addAttribute("next", response.next());
        model.addAttribute("prev", response.prev());
        model.addAttribute("page", page);
        //総ページ数(切り上げ)。総件数9、1ページ8なら、2ページ
        model.addAttribute("totalPages", (response.totalCount() + PAGE_SIZE - 1) / PAGE_SIZE);
        model.addAttribute("keyword", keyword);
        return "employees";
    }

    //社員詳細。存在しない社員なら、APIが404を返し、共通のエラー処理で404の画面になる
    @GetMapping("/employees/{id}")
    public String detail(@PathVariable Long id, Model model){
        EmployeeDetail employee = api.get("/api/employees/{id}", EmployeeDetail.class, id);
        model.addAttribute("employee", employee);
        return "employee-detail";
    }
}

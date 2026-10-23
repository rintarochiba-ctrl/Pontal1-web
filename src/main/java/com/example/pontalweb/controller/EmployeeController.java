package com.example.pontalweb.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.pontalweb.client.EmployeeCreateRequest;
import com.example.pontalweb.client.EmployeeDetail;
import com.example.pontalweb.client.EmployeeListResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;

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

    //新規社員登録の入力画面(システム管理者だけ。SecurityConfigで制限)
    //"/employees/new" は、"/employees/{id}" より具体的なので、こちらが先に選ばれる
    @GetMapping("/employees/new")
    public String registerForm(){
        return "employee-register";
    }

    //新規社員登録の送信(システム管理者だけ。SecurityConfigで制限)
    //入力を API に送って、登録できたら、その人の詳細画面に移動する
    //  ・チェックボックスは、チェックしたときだけ送られてくるので、送られなければ false にする
    //  ・役職は、空なら送らない(null)。初期パスワードは、前後の空白も、パスワードの一部なので、そのまま送る
    @PostMapping("/employees")
    public String register(@RequestParam String name,
            @RequestParam String email,
            @RequestParam String initialPassword,
            @RequestParam String joinDate,
            @RequestParam String department,
            @RequestParam(defaultValue = "") String position,
            @RequestParam(defaultValue = "false") boolean isSystemAdmin,
            @RequestParam(defaultValue = "false") boolean isHrAdmin,
            Model model,
            RedirectAttributes redirectAttributes){
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                name.trim(), email.trim(), initialPassword, joinDate, department.trim(),
                position.isBlank() ? null : position.trim(), isSystemAdmin, isHrAdmin);

        try {
            EmployeeDetail created = api.post("/api/employees", request, EmployeeDetail.class);
            //移動先の画面で、1回だけ表示するお知らせ(リロードしたら、消える)
            //登録の API には、性別・年齢・出身地などの項目がないので、編集から入れてもらうよう、案内する
            redirectAttributes.addFlashAttribute("successMessage",
                    "社員を登録しました。性別・年齢・出身地などのプロフィールは、「編集」から入力できます");
            return "redirect:/employees/" + created.getId();
        } catch (ConflictException | BadRequestException e) {
            //メールの重複(409)・パスワードのルール違反(400)など、入力し直せば通るもの
            //エラー画面ではなく、入力画面に、メッセージつきで戻す。入力した内容は残す(初期パスワードだけは、残さない)
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("name", request.name());
            form.put("email", request.email());
            form.put("joinDate", request.joinDate());
            form.put("department", request.department());
            form.put("position", request.position());
            form.put("isSystemAdmin", request.systemAdmin());
            form.put("isHrAdmin", request.hrAdmin());
            model.addAttribute("form", form);
            model.addAttribute("errorMessage", friendlyMessage(e));
            return "employee-register";
        }
    }

    //画面に出すメッセージ。APIの400のうち、入力の形の間違いは、"email: must be ..." のような英語の文なので、
    //そのまま出さず、一般的な文にする(日本語のメッセージ、409の文は、そのまま出す)
    private static String friendlyMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank() || message.matches("^\\w+: .*")) {
            return "入力内容に誤りがあります。各項目を確認してください";
        }
        return message;
    }

    //社員詳細。存在しない社員なら、APIが404を返し、共通のエラー処理で404の画面になる
    @GetMapping("/employees/{id}")
    public String detail(@PathVariable Long id, Model model){
        EmployeeDetail employee = api.get("/api/employees/{id}", EmployeeDetail.class, id);
        model.addAttribute("employee", employee);
        return "employee-detail";
    }
}

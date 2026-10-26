package com.example.pontalweb.controller;

import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.pontalweb.client.AnnouncementDetail;
import com.example.pontalweb.client.EmployeeListResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.model.AnnouncementForm;

//お知らせの作成・編集の画面(システム管理者・HR管理者だけ。SecurityConfigでも、この画面のURLを、管理者に絞っている)
@Controller
public class AnnouncementFormController {

    //宛先の個人の候補は、社員一覧のAPIの、1回の上限(100)まで
    private static final String EMPLOYEES_URI = "/api/employees?page=0&size=100";

    private final PontalApiClient api;

    public AnnouncementFormController(PontalApiClient api) {
        this.api = api;
    }

    // ---------- 作成 ----------

    //作成画面を開く
    @GetMapping("/announcements/new")
    public String newForm(Model model) {
        //空のフォーム(宛先は、全員から始まる)を、form という名前で、画面に渡す
        model.addAttribute("form", new AnnouncementForm());
        return showForm(model, null);
    }

    //作成を送信する。フォームの値は、AnnouncementForm に入って届く(@ModelAttribute)
    @PostMapping("/announcements/new")
    public String create(@ModelAttribute("form") AnnouncementForm form, Model model,
            RedirectAttributes redirectAttributes) {
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            //入力にまちがいがある。redirectせず、同じフォームを、入力した内容つきで、出し直す
            model.addAttribute("errors", errors);
            return showForm(model, null);
        }
        try {
            //APIに送る内容は、フォームから作る
            AnnouncementDetail created = api.post("/api/announcements", form.toRequest(), AnnouncementDetail.class);
            redirectAttributes.addFlashAttribute("successMessage", "お知らせを作成しました");
            //作ったお知らせの詳細に移動する。APIが返した、作ったお知らせの番号を使う
            return "redirect:/announcements/" + created.getId();
        } catch (BadRequestException e) {
            //APIが、入力のまちがいを返した(画面のチェックでは見つけられなかったもの)。APIのメッセージを、フォームの上に出す
            model.addAttribute("errorMessage", e.getMessage());
            return showForm(model, null);
        }
    }

    // ---------- 編集 ----------

    //編集画面を開く。いまの内容を、フォームに入れて出す
    @GetMapping("/announcements/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AnnouncementDetail detail = api.get("/api/announcements/{id}", AnnouncementDetail.class, id);
        //編集できない人(APIが、canEdit=false で返した)は、フォームを出さない
        if (!detail.isCanEdit()) {
            throw new ForbiddenException("このお知らせを編集する権限がありません");
        }
        //いまのお知らせの内容を、フォームに入れて、画面に渡す
        model.addAttribute("form", AnnouncementForm.from(detail));
        return showForm(model, id);
    }

    //編集を送信する。APIには、全項目を丸ごと置き換える形で、PUTで送る
    @PostMapping("/announcements/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("form") AnnouncementForm form, Model model,
            RedirectAttributes redirectAttributes) {
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return showForm(model, id);
        }
        try {
            //APIの「編集」は、全項目を置き換えるHTTPの種類(PUT)で送る
            api.put("/api/announcements/{id}", form.toRequest(), AnnouncementDetail.class, id);
            redirectAttributes.addFlashAttribute("successMessage", "お知らせを更新しました");
            return "redirect:/announcements/" + id;
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return showForm(model, id);
        }
    }

    // ---------- フォームを出す(作成・編集で共通) ----------

    //フォームの選択肢(種類・部署・社員)を、Modelに入れて、画面を返す。announcementId は、編集のときだけ入る(作成は null)
    private String showForm(Model model, Long announcementId) {
        List<String> departments = api.get("/api/departments", new ParameterizedTypeReference<List<String>>() {
        });
        EmployeeListResponse employees = api.get(EMPLOYEES_URI, EmployeeListResponse.class);

        model.addAttribute("categories", AnnouncementForm.CATEGORIES);
        model.addAttribute("departments", departments == null ? List.of() : departments);
        model.addAttribute("employees", employees == null || employees.items() == null ? List.of() : employees.items());
        model.addAttribute("announcementId", announcementId);
        return "announcement-form";
    }
}

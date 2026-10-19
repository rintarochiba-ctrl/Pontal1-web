package com.example.pontalweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.model.LoginUser;

//共通レイアウトと、部品(CSS)の見た目を確認するための仮の画面。ログインができたら、削除する
//ログインがまだないので、権限(role)をURLで指定して、メニューの出し分けを確認する
@Controller
public class SamplePageController {

    @GetMapping("/sample")
    public String sample(@RequestParam(defaultValue = "member") String role, Model model) {
        LoginUser user = switch (role) {
            case "admin" -> new LoginUser(1L, "山田 太郎", true, false);
            case "hr" -> new LoginUser(2L, "佐藤 花子", false, true);
            default -> new LoginUser(3L, "鈴木 一郎", false, false);
        };
        model.addAttribute("loginUser", user);
        return "sample"; // /WEB-INF/jsp/sample.jsp が使われる
    }

    //以下は、エラー画面の確認用。わざと例外を投げて、GlobalExceptionHandlerが、エラー画面にすることを確かめる

    @GetMapping("/sample/forbidden")
    public String forbidden() {
        throw new ForbiddenException("この画面を見る権限がありません(確認用)");
    }

    @GetMapping("/sample/notfound")
    public String notFound() {
        throw new NotFoundException("社員が見つかりません(確認用)");
    }

    @GetMapping("/sample/server-error")
    public String serverError() {
        throw new IllegalStateException("予期しないエラー(確認用)");
    }

    //URLの数字の部分に、文字(例: /sample/items/abc)を入れると、400の画面になる
    @GetMapping("/sample/items/{id}")
    public String item(@PathVariable Long id) {
        return "sample";
    }
}

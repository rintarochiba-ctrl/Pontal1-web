package com.example.pontalweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
}

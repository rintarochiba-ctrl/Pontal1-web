package com.example.pontalweb.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.security.LoginFailureReason;

//ログイン画面。ログインの処理そのもの(POST /login)は、Spring Securityが行う
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Authentication authentication,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String passwordset,
            Model model) {
        //すでにログインしている人が、ログイン画面を開いたら、ホームに移動する
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }

        //URLのコードから、画面に出すメッセージを決める(知らないコードなら、一般的なメッセージ)
        if (error != null) {
            model.addAttribute("errorMessage", LoginFailureReason.fromCode(error)
                    .orElse(LoginFailureReason.UNAVAILABLE).getMessage());
        }
        if (expired != null) {
            model.addAttribute("infoMessage", "ログインの有効期限が切れました。もう一度ログインしてください");
        }
        if (passwordset != null) {
            model.addAttribute("infoMessage", "新しいパスワードを設定しました。新しいパスワードで、ログインしてください");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "ログアウトしました");
        }
        return "login";
    }
}

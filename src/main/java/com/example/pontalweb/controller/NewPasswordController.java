package com.example.pontalweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.security.CognitoAuthClient;
import com.example.pontalweb.security.NewPasswordFailedException;

import jakarta.servlet.http.HttpSession;

//初回ログイン(初期パスワード)の、新しいパスワードの設定画面
//  1. 初期パスワードでログイン → Cognitoが「新しいパスワードが必要」と返す → ここに移動(LoginFailureHandler)
//  2. 新しいパスワードを2回入力して送信 → Cognitoに設定する
//  3. 成功したら、ログイン画面に戻って、新しいパスワードで、ログインし直してもらう
@Controller
public class NewPasswordController {

    private final CognitoAuthClient cognitoAuthClient;

    public NewPasswordController(CognitoAuthClient cognitoAuthClient) {
        this.cognitoAuthClient = cognitoAuthClient;
    }

    @GetMapping("/new-password")
    public String form(HttpSession session) {
        //初回ログインの途中でなければ(URLを直接開いたなど)、ログイン画面へ
        if (!isPending(session)) {
            return "redirect:/login";
        }
        return "new-password";
    }

    @PostMapping("/new-password")
    public String submit(@RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            Model model) {
        if (!isPending(session)) {
            return "redirect:/login";
        }

        //入力チェック。画面に戻すときは、同じ画面を、メッセージつきで出し直す
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "確認用のパスワードが、一致しません");
            return "new-password";
        }

        String email = (String) session.getAttribute(SessionKeys.NEW_PASSWORD_EMAIL);
        String cognitoSession = (String) session.getAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION);
        try {
            cognitoAuthClient.completeNewPassword(email, newPassword, cognitoSession);
        } catch (NewPasswordFailedException e) {
            if (e.isSessionExpired()) {
                //入力し直しても通らないので、最初からやり直してもらう
                clear(session);
                return "redirect:/login?error=newpasswordexpired";
            }
            model.addAttribute("errorMessage", e.getMessage());
            return "new-password";
        }

        clear(session);
        return "redirect:/login?passwordset";
    }

    private boolean isPending(HttpSession session) {
        return session.getAttribute(SessionKeys.NEW_PASSWORD_EMAIL) != null
                && session.getAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION) != null;
    }

    private void clear(HttpSession session) {
        session.removeAttribute(SessionKeys.NEW_PASSWORD_EMAIL);
        session.removeAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION);
    }
}

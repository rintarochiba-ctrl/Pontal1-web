package com.example.pontalweb.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.pontalweb.client.SessionKeys;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

//ログイン成功のとき、アクセストークンと、ログイン中の人の情報を、セッションに入れる(以降のAPI呼び出しと、画面の表示で使う)
//そのあと、ログイン前に開こうとしていた画面(なければ、ホーム)に移動する
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public LoginSuccessHandler() {
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        PontalPrincipal principal = (PontalPrincipal) authentication.getPrincipal();

        //この時点で、セッションIDは、新しいものに変わっている(ログイン前のIDを、使い回させないため)
        HttpSession session = request.getSession();
        session.setAttribute(SessionKeys.ACCESS_TOKEN, principal.getAccessToken());
        session.setAttribute(SessionKeys.LOGIN_USER, principal.getLoginUser());

        super.onAuthenticationSuccess(request, response, authentication);
    }
}

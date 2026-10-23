package com.example.pontalweb.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//ログイン失敗のとき、理由のコードを付けて、ログイン画面に戻す(/login?error=credentials など)
//入力されたメールアドレスやパスワードは、URLにも、ログにも、出さない
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        LoginFailureReason reason = exception instanceof LoginFailedException failed
                ? failed.getReason()
                : LoginFailureReason.UNAVAILABLE; //想定外の失敗は、詳しい理由を出さない
        log.warn("ログインに失敗しました reason={}", reason.name());
        getRedirectStrategy().sendRedirect(request, response, "/login?error=" + reason.getCode());
    }
}

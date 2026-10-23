package com.example.pontalweb.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.example.pontalweb.client.SessionKeys;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

//ログイン失敗のとき、理由のコードを付けて、ログイン画面に戻す(/login?error=credentials など)
//入力されたメールアドレスやパスワードは、URLにも、ログにも、出さない
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        //初回ログイン(初期パスワード)は、失敗ではなく、新しいパスワードの設定画面に進む
        //メールアドレスとCognitoのセッションは、セッションに預ける(URLには出さない)
        if (exception instanceof NewPasswordRequiredException required) {
            HttpSession session = request.getSession();
            session.setAttribute(SessionKeys.NEW_PASSWORD_EMAIL, required.getEmail());
            session.setAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION, required.getCognitoSession());
            getRedirectStrategy().sendRedirect(request, response, "/new-password");
            return;
        }

        LoginFailureReason reason = exception instanceof LoginFailedException failed
                ? failed.getReason()
                : LoginFailureReason.UNAVAILABLE; //想定外の失敗は、詳しい理由を出さない
        log.warn("ログインに失敗しました reason={}", reason.name());
        getRedirectStrategy().sendRedirect(request, response, "/login?error=" + reason.getCode());
    }
}

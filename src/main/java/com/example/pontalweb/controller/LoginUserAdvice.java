package com.example.pontalweb.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.model.LoginUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

//すべての画面に、ログイン中の人の情報(loginUser)を渡す。共通レイアウトが、名前と、メニューの出し分けに使う
@ControllerAdvice
public class LoginUserAdvice {

    @ModelAttribute("loginUser")
    public LoginUser loginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false); //セッションが無ければ、作らない
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SessionKeys.LOGIN_USER);
        return value instanceof LoginUser user ? user : null;
    }
}

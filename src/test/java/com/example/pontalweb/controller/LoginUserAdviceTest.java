package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.model.LoginUser;

//LoginUserAdviceのテスト。セッションの中のログイン中の人の情報が、画面(Model)に渡されることを確認する
class LoginUserAdviceTest {

    private final LoginUserAdvice advice = new LoginUserAdvice();

    @Test
    void returnsLoginUserStoredInSession() {
        LoginUser user = new LoginUser(1L, "山田太郎", true, false);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER, user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        assertThat(advice.loginUser(request)).isSameAs(user);
    }

    @Test
    void returnsNull_whenSessionHasNoLoginUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        assertThat(advice.loginUser(request)).isNull();
    }

    @Test
    void returnsNull_andDoesNotCreateSession_whenThereIsNoSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(advice.loginUser(request)).isNull();
        //ログインしていない人のために、セッションを、勝手に作らない
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void returnsNull_whenValueIsNotALoginUser() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER, "山田太郎");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        assertThat(advice.loginUser(request)).isNull();
    }
}

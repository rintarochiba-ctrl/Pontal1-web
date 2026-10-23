package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

//SessionAccessTokenProviderのテスト。リクエストと、セッションを、疑似のもの(Mock)で用意して、トークンの取り出しを確認する
class SessionAccessTokenProviderTest {

    private final SessionAccessTokenProvider provider = new SessionAccessTokenProvider();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes(); //他のテストに影響しないよう、後片付け
    }

    private void currentRequestWithSession(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void currentToken_returnsTokenStoredInSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.ACCESS_TOKEN, "abc.def.ghi");
        currentRequestWithSession(session);

        assertThat(provider.currentToken()).isEqualTo("abc.def.ghi");
    }

    @Test
    void currentToken_isNull_whenSessionHasNoToken() {
        currentRequestWithSession(new MockHttpSession());

        assertThat(provider.currentToken()).isNull();
    }

    @Test
    void currentToken_isNull_whenValueIsNotAString() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.ACCESS_TOKEN, 123);
        currentRequestWithSession(session);

        assertThat(provider.currentToken()).isNull();
    }

    @Test
    void currentToken_isNull_outsideOfARequest() {
        RequestContextHolder.resetRequestAttributes();

        assertThat(provider.currentToken()).isNull();
    }

    @Test
    void currentToken_isSeparatedPerSession() {
        MockHttpSession alice = new MockHttpSession();
        alice.setAttribute(SessionKeys.ACCESS_TOKEN, "alice-token");
        MockHttpSession bob = new MockHttpSession();
        bob.setAttribute(SessionKeys.ACCESS_TOKEN, "bob-token");

        currentRequestWithSession(alice);
        assertThat(provider.currentToken()).isEqualTo("alice-token");

        currentRequestWithSession(bob);
        assertThat(provider.currentToken()).isEqualTo("bob-token"); //別の人のリクエストでは、その人のトークン
    }
}

package com.example.pontalweb.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.model.LoginUser;

import jakarta.servlet.http.HttpSession;

//ログイン成功・失敗のハンドラーのテスト。リクエストとレスポンスを、疑似のもの(Mock)で用意して、結果を確認する
class LoginHandlersTest {

    private Authentication successfulAuthentication(LoginUser user, String token) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(user, token), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ---------- LoginSuccessHandler ----------

    @Test
    void success_putsTokenAndLoginUserInSession_andRedirectsToHome() throws Exception {
        LoginSuccessHandler handler = new LoginSuccessHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginUser user = new LoginUser(1L, "山田太郎", true, false);

        handler.onAuthenticationSuccess(request, response, successfulAuthentication(user, "token-abc"));

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("token-abc");
        assertThat(session.getAttribute(SessionKeys.LOGIN_USER)).isSameAs(user);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    // ---------- LoginFailureHandler ----------

    @Test
    void failure_withReason_redirectsToLoginWithReasonCode() throws Exception {
        LoginFailureHandler handler = new LoginFailureHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new LoginFailedException(LoginFailureReason.CREDENTIALS));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=credentials");
    }

    @Test
    void failure_eachReason_hasItsOwnCode() throws Exception {
        LoginFailureHandler handler = new LoginFailureHandler();
        for (LoginFailureReason reason : LoginFailureReason.values()) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new LoginFailedException(reason));
            assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=" + reason.getCode());
        }
    }

    @Test
    void failure_unexpectedException_redirectsAsUnavailable_withoutDetails() throws Exception {
        LoginFailureHandler handler = new LoginFailureHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new BadCredentialsException("内部の詳細"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=unavailable");
    }

    @Test
    void failure_doesNotPutEmailOrPasswordInUrl() throws Exception {
        LoginFailureHandler handler = new LoginFailureHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("email", "secret@example.com");
        request.setParameter("password", "secret-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new LoginFailedException(LoginFailureReason.CREDENTIALS));

        assertThat(response.getRedirectedUrl()).doesNotContain("secret");
    }

    // ---------- LoginFailureReason ----------

    @Test
    void reason_fromCode_findsKnownCode_andIgnoresUnknown() {
        assertThat(LoginFailureReason.fromCode("credentials")).contains(LoginFailureReason.CREDENTIALS);
        assertThat(LoginFailureReason.fromCode("notregistered")).contains(LoginFailureReason.NOT_REGISTERED);
        assertThat(LoginFailureReason.fromCode("nonsense")).isEmpty();
        assertThat(LoginFailureReason.fromCode(null)).isEmpty();
    }

    @Test
    void reason_codesAreUnique() {
        long distinct = java.util.Arrays.stream(LoginFailureReason.values()).map(LoginFailureReason::getCode).distinct().count();
        assertThat(distinct).isEqualTo(LoginFailureReason.values().length);
    }
}

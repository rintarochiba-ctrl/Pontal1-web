package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.security.CognitoAuthClient;
import com.example.pontalweb.security.NewPasswordFailedException;

//NewPasswordController(初回ログインの、新しいパスワード設定)のテスト。Cognitoは疑似のもの(Mock)にする
//(認証のフィルターは外す。「ログイン前でも開ける」「CSRF」は、SecurityConfigTestで確認する)
@WebMvcTest(NewPasswordController.class)
@AutoConfigureMockMvc(addFilters = false)
class NewPasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CognitoAuthClient cognitoAuthClient;

    //初回ログインの途中(メールアドレスと、Cognitoのセッションを、持っている)のセッション
    private MockHttpSession pendingSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.NEW_PASSWORD_EMAIL, "new@example.com");
        session.setAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION, "cognito-session-abc");
        return session;
    }

    // ---------- 画面の表示 ----------

    @Test
    void form_whenFirstLoginIsInProgress_showsForm() throws Exception {
        mockMvc.perform(get("/new-password").session(pendingSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("new-password"));
    }

    @Test
    void form_whenNotInProgress_redirectsToLogin() throws Exception {
        //URLを直接開いたなど。初回ログインの途中でなければ、ログイン画面へ
        mockMvc.perform(get("/new-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void form_whenOnlyEmailIsInSession_redirectsToLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.NEW_PASSWORD_EMAIL, "new@example.com");

        mockMvc.perform(get("/new-password").session(session))
                .andExpect(redirectedUrl("/login"));
    }

    // ---------- 設定の送信:成功 ----------

    @Test
    void submit_success_setsPasswordInCognito_clearsSession_andRedirectsToLogin() throws Exception {
        MockHttpSession session = pendingSession();

        mockMvc.perform(post("/new-password").session(session)
                .param("newPassword", "NewPass123!").param("confirmPassword", "NewPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordset"));

        verify(cognitoAuthClient).completeNewPassword("new@example.com", "NewPass123!", "cognito-session-abc");
        //途中の情報は、消える(使い回されないように)
        assertThat(session.getAttribute(SessionKeys.NEW_PASSWORD_EMAIL)).isNull();
        assertThat(session.getAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION)).isNull();
    }

    // ---------- 設定の送信:失敗 ----------

    @Test
    void submit_whenConfirmationDiffers_showsMessage_withoutCallingCognito() throws Exception {
        MockHttpSession session = pendingSession();

        mockMvc.perform(post("/new-password").session(session)
                .param("newPassword", "NewPass123!").param("confirmPassword", "Different1!"))
                .andExpect(status().isOk())
                .andExpect(view().name("new-password"))
                .andExpect(model().attribute("errorMessage", "確認用のパスワードが、一致しません"));

        verifyNoInteractions(cognitoAuthClient);
        //入力し直せるよう、途中の情報は、残る
        assertThat(session.getAttribute(SessionKeys.NEW_PASSWORD_EMAIL)).isEqualTo("new@example.com");
    }

    @Test
    void submit_whenPasswordRulesNotMet_showsCognitoMessage_andKeepsSessionForRetry() throws Exception {
        MockHttpSession session = pendingSession();
        doThrow(new NewPasswordFailedException("パスワードが、ルールを満たしていません", false, null))
                .when(cognitoAuthClient).completeNewPassword(any(), any(), any());

        mockMvc.perform(post("/new-password").session(session)
                .param("newPassword", "short").param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("new-password"))
                .andExpect(model().attribute("errorMessage", "パスワードが、ルールを満たしていません"));

        assertThat(session.getAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION)).isEqualTo("cognito-session-abc");
    }

    @Test
    void submit_whenCognitoSessionExpired_clearsSession_andRedirectsToLoginWithError() throws Exception {
        MockHttpSession session = pendingSession();
        doThrow(new NewPasswordFailedException("時間が経ちすぎました", true, null))
                .when(cognitoAuthClient).completeNewPassword(any(), any(), any());

        mockMvc.perform(post("/new-password").session(session)
                .param("newPassword", "NewPass123!").param("confirmPassword", "NewPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=newpasswordexpired"));

        //入力し直しても通らないので、途中の情報は、消える
        assertThat(session.getAttribute(SessionKeys.NEW_PASSWORD_EMAIL)).isNull();
        assertThat(session.getAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION)).isNull();
    }

    @Test
    void submit_whenNotInProgress_redirectsToLogin_withoutCallingCognito() throws Exception {
        mockMvc.perform(post("/new-password")
                .param("newPassword", "NewPass123!").param("confirmPassword", "NewPass123!"))
                .andExpect(redirectedUrl("/login"));

        verify(cognitoAuthClient, never()).completeNewPassword(any(), any(), any());
    }

    // ---------- パスワードを、画面にも、URLにも、残さない ----------

    @Test
    void submit_doesNotPutPasswordsInModelOrUrl() throws Exception {
        MockHttpSession session = pendingSession();

        var result = mockMvc.perform(post("/new-password").session(session)
                .param("newPassword", "Secret-New-1!").param("confirmPassword", "Secret-New-2!"))
                .andReturn();

        //確認用が違って、画面に戻すとき、入力したパスワードを、Modelに入れない(画面に、出し直さない)
        assertThat(result.getModelAndView().getModel().values().toString()).doesNotContain("Secret-New");
    }

    @Test
    void submit_success_redirectUrlDoesNotContainPassword() throws Exception {
        var result = mockMvc.perform(post("/new-password").session(pendingSession())
                .param("newPassword", "Secret-New-1!").param("confirmPassword", "Secret-New-1!"))
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("Secret");
    }
}

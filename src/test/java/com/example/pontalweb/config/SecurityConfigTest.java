package com.example.pontalweb.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.controller.HomeController;
import com.example.pontalweb.controller.LoginController;
import com.example.pontalweb.controller.NewPasswordController;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthClient;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.LoginFailedException;
import com.example.pontalweb.security.LoginFailureHandler;
import com.example.pontalweb.security.LoginFailureReason;
import com.example.pontalweb.security.LoginSuccessHandler;
import com.example.pontalweb.security.PontalPrincipal;

//SecurityConfigのテスト。実際のセキュリティの設定(SecurityConfig)を使って、
//「ログインが必要か」「権限で見られる画面が分かれるか」「CSRF対策が効いているか」「ログイン・ログアウトの動き」を確認する
//Cognitoでの確認(CognitoAuthenticationProvider)だけは、疑似のもの(Mock)に差し替える
@WebMvcTest(controllers = { LoginController.class, HomeController.class, NewPasswordController.class,
        SecurityConfigTest.ProbeController.class })
@Import({ SecurityConfig.class, ClockConfig.class, LoginSuccessHandler.class, LoginFailureHandler.class, SecurityConfigTest.ProbeController.class })
class SecurityConfigTest {

    //権限のテスト用に、URLだけを受ける、テスト用のController
    @Controller
    static class ProbeController {
        @GetMapping({ "/employees", "/payroll", "/payroll/upload", "/audit-logs" })
        String probe() {
            return "probe";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CognitoAuthenticationProvider authenticationProvider;

    //NewPasswordControllerが使う(このテストでは、呼ばれない)
    @MockBean
    private CognitoAuthClient cognitoAuthClient;

    //HomeControllerが、お知らせを取るために使う(このテストでは、お知らせの中身は見ない)
    @MockBean
    private PontalApiClient api;

    @BeforeEach
    void setUp() {
        //モックのProviderを、「この種類の認証を扱える」ことにする(これがないと、Spring Securityが、使ってくれない)
        when(authenticationProvider.supports(any())).thenReturn(true);
    }

    // ---------- ログインが必要か ----------

    @Test
    void unauthenticated_isRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void unauthenticated_otherPagesAreRedirectedToLogin_too() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void loginPage_isOpenToEveryone() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void css_isOpenToEveryone() throws Exception {
        //ログイン画面のスタイルが、ログイン前でも読み込めること(ログイン画面に飛ばされない)
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void loggedInUser_canOpenOrdinaryPages() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk());
    }

    // ---------- 権限ごとに見られる画面 ----------

    @Test
    @WithMockUser(roles = "USER")
    void generalUser_cannotOpenPayroll_orAuditLogs() throws Exception {
        mockMvc.perform(get("/payroll")).andExpect(status().isForbidden());
        mockMvc.perform(get("/payroll/upload")).andExpect(status().isForbidden());
        mockMvc.perform(get("/audit-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "HR_ADMIN" })
    void hrAdmin_canOpenPayroll_butNotAuditLogs() throws Exception {
        mockMvc.perform(get("/payroll")).andExpect(status().isOk());
        mockMvc.perform(get("/payroll/upload")).andExpect(status().isOk());
        mockMvc.perform(get("/audit-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "SYSTEM_ADMIN" })
    void systemAdmin_canOpenAuditLogs_butNotPayroll() throws Exception {
        mockMvc.perform(get("/audit-logs")).andExpect(status().isOk());
        mockMvc.perform(get("/payroll")).andExpect(status().isForbidden());
    }

    // ---------- ログイン ----------

    @Test
    void login_withoutCsrfToken_isRejected() throws Exception {
        //他のサイトから、勝手にログインのフォームを送られないよう、確認用のトークンが無いと、403になる
        mockMvc.perform(post("/login").param("email", "a@example.com").param("password", "pass"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_success_redirectsToHome_andStoresTokenAndUserInSession() throws Exception {
        LoginUser user = new LoginUser(1L, "山田太郎", true, false);
        when(authenticationProvider.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(user, "token-abc"), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(post("/login").with(csrf()).param("email", "a@example.com").param("password", "pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(request().sessionAttribute(SessionKeys.ACCESS_TOKEN, "token-abc"))
                .andExpect(request().sessionAttribute(SessionKeys.LOGIN_USER, user));
    }

    @Test
    void login_failure_redirectsBackToLogin_withReason() throws Exception {
        when(authenticationProvider.authenticate(any())).thenThrow(new LoginFailedException(LoginFailureReason.CREDENTIALS));

        mockMvc.perform(post("/login").with(csrf()).param("email", "a@example.com").param("password", "bad"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=credentials"));
    }

    @Test
    void login_callsAuthenticationProviderExactlyOnce() throws Exception {
        //1回のログインで、Cognitoへの問い合わせが、2回行われないこと(2回だと、失敗が続いたときに、ロックされやすくなる)
        when(authenticationProvider.authenticate(any())).thenThrow(new LoginFailedException(LoginFailureReason.CREDENTIALS));

        mockMvc.perform(post("/login").with(csrf()).param("email", "a@example.com").param("password", "bad"));

        verify(authenticationProvider, times(1)).authenticate(any());
    }

    // ---------- 初回ログインの、新しいパスワード設定(ログイン前に開く画面)----------

    @Test
    void newPasswordPage_isNotBlockedByLoginCheck() throws Exception {
        //ログイン前でも、この画面には、入れる(ログイン画面への移動は、画面側が、「途中でなければ」と判断して行う)
        //セキュリティの移動先(http://localhost/login)ではなく、画面側の移動先(/login)になること
        mockMvc.perform(get("/new-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void newPasswordPage_whenFirstLoginIsInProgress_isShownWithoutLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.NEW_PASSWORD_EMAIL, "new@example.com");
        session.setAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION, "s");

        mockMvc.perform(get("/new-password").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void newPasswordSubmit_withoutCsrfToken_isRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.NEW_PASSWORD_EMAIL, "new@example.com");
        session.setAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION, "s");

        //他のサイトから、勝手に、パスワードの設定のフォームを送られないよう、確認用のトークンが無いと、403
        mockMvc.perform(post("/new-password").session(session)
                .param("newPassword", "NewPass123!").param("confirmPassword", "NewPass123!"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newPasswordFailure_inLogin_redirectsToNewPasswordPage() throws Exception {
        //ログインで、初期パスワードだと分かったら、失敗(エラー)ではなく、設定画面に進む
        when(authenticationProvider.authenticate(any()))
                .thenThrow(new com.example.pontalweb.security.NewPasswordRequiredException("new@example.com", "cognito-session"));

        mockMvc.perform(post("/login").with(csrf()).param("email", "new@example.com").param("password", "initial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/new-password"))
                .andExpect(request().sessionAttribute(SessionKeys.NEW_PASSWORD_EMAIL, "new@example.com"))
                .andExpect(request().sessionAttribute(SessionKeys.NEW_PASSWORD_COGNITO_SESSION, "cognito-session"));
    }

    // ---------- ログアウト ----------

    @Test
    @WithMockUser
    void logout_redirectsToLogin_andInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.ACCESS_TOKEN, "token-abc");

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        //トークンが入っていたセッションは、捨てられている
        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @WithMockUser
    void logout_withoutCsrfToken_isRejected() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }
}

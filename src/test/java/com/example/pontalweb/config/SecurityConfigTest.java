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
import org.springframework.web.bind.annotation.PostMapping;

import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.controller.HomeController;
import com.example.pontalweb.controller.LoginController;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.LoginFailedException;
import com.example.pontalweb.security.LoginFailureHandler;
import com.example.pontalweb.security.LoginFailureReason;
import com.example.pontalweb.security.LoginSuccessHandler;
import com.example.pontalweb.security.PontalPrincipal;

//SecurityConfigのテスト。実際のセキュリティの設定(SecurityConfig)を使って、
//「ログインが必要か」「権限で見られる画面が分かれるか」「CSRF対策が効いているか」「ログイン・ログアウトの動き」を確認する
//Cognitoでの確認(CognitoAuthenticationProvider)だけは、疑似のもの(Mock)に差し替える
@WebMvcTest(controllers = { LoginController.class, HomeController.class, SecurityConfigTest.ProbeController.class })
@Import({ SecurityConfig.class, LoginSuccessHandler.class, LoginFailureHandler.class, SecurityConfigTest.ProbeController.class })
class SecurityConfigTest {

    //権限のテスト用に、URLだけを受ける、テスト用のController
    @Controller
    static class ProbeController {
        @GetMapping({ "/employees", "/employees/new", "/employees/{id}/edit", "/payroll", "/payroll/upload", "/audit-logs" })
        String probe() {
            return "probe";
        }

        @PostMapping({ "/employees", "/employees/{id}", "/employees/{id}/delete" })
        String probePost() {
            return "probe";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CognitoAuthenticationProvider authenticationProvider;

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

    @Test
    @WithMockUser(roles = "USER")
    void generalUser_cannotOpenEmployeeRegister_orSubmitIt() throws Exception {
        mockMvc.perform(get("/employees/new")).andExpect(status().isForbidden());
        mockMvc.perform(post("/employees").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "HR_ADMIN" })
    void hrAdmin_cannotOpenEmployeeRegister() throws Exception {
        mockMvc.perform(get("/employees/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "SYSTEM_ADMIN" })
    void systemAdmin_canOpenEmployeeRegister_andSubmitIt() throws Exception {
        mockMvc.perform(get("/employees/new")).andExpect(status().isOk());
        mockMvc.perform(post("/employees").with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "USER", "SYSTEM_ADMIN" })
    void employeeRegister_submitWithoutCsrfToken_isRejected() throws Exception {
        mockMvc.perform(post("/employees")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void generalUser_cannotOpenEmployeeEdit() throws Exception {
        mockMvc.perform(get("/employees/1/edit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "HR_ADMIN" })
    void hrAdmin_cannotOpenEmployeeEdit() throws Exception {
        mockMvc.perform(get("/employees/1/edit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "SYSTEM_ADMIN" })
    void systemAdmin_canOpenEmployeeEdit() throws Exception {
        mockMvc.perform(get("/employees/1/edit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void generalUser_cannotSubmitEmployeeEdit() throws Exception {
        mockMvc.perform(post("/employees/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "HR_ADMIN" })
    void hrAdmin_cannotSubmitEmployeeEdit() throws Exception {
        mockMvc.perform(post("/employees/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "SYSTEM_ADMIN" })
    void systemAdmin_canSubmitEmployeeEdit_butNotWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/employees/1").with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post("/employees/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void generalUser_cannotDeleteEmployee() throws Exception {
        mockMvc.perform(post("/employees/1/delete").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "HR_ADMIN" })
    void hrAdmin_cannotDeleteEmployee() throws Exception {
        mockMvc.perform(post("/employees/1/delete").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "USER", "SYSTEM_ADMIN" })
    void systemAdmin_canDeleteEmployee_butNotWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/employees/1/delete").with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post("/employees/1/delete")).andExpect(status().isForbidden());
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

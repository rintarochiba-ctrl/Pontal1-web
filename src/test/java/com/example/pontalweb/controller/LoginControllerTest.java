package com.example.pontalweb.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.config.ClockConfig;

//LoginController と HomeController のテスト。画面の名前と、画面に出すメッセージを確認する
//(認証のフィルターは外す。ログインが必要かどうかは、SecurityConfigTestで確認する)
@WebMvcTest(controllers = { LoginController.class, HomeController.class })
@Import(ClockConfig.class) //HomeControllerが使う、現在の時刻(Clock)
@AutoConfigureMockMvc(addFilters = false)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    //HomeControllerが、お知らせを取るために使う(このテストでは、お知らせの中身は見ない)
    @MockBean
    private PontalApiClient api;

    @Test
    void login_showsLoginView_withoutMessages() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeDoesNotExist("errorMessage", "infoMessage"));
    }

    @Test
    void login_withCredentialsError_showsThatMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "credentials"))
                .andExpect(view().name("login"))
                .andExpect(model().attribute("errorMessage", "メールアドレスまたはパスワードが違います"));
    }

    @Test
    void login_withNotRegisteredError_showsThatMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "notregistered"))
                .andExpect(model().attribute("errorMessage",
                        "このアカウントは、Pontalに登録されていません。管理者に問い合わせてください"));
    }

    @Test
    void login_withUnknownErrorCode_showsGenericMessage() throws Exception {
        //URLに、勝手な文字を入れられても、画面には、決まったメッセージだけが出る(入力した文字は、出さない)
        mockMvc.perform(get("/login").param("error", "<script>alert(1)</script>"))
                .andExpect(model().attribute("errorMessage", "ログインできませんでした。しばらくしてからもう一度お試しください"));
    }

    @Test
    void login_afterNewPasswordIsSet_showsThatMessage() throws Exception {
        mockMvc.perform(get("/login").param("passwordset", ""))
                .andExpect(model().attribute("infoMessage", "新しいパスワードを設定しました。新しいパスワードで、ログインしてください"));
    }

    @Test
    void login_whenNewPasswordSessionExpired_showsThatMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "newpasswordexpired"))
                .andExpect(model().attribute("errorMessage", "時間が経ちすぎました。もう一度、初期パスワードでログインしてください"));
    }

    @Test
    void login_afterLogout_showsLogoutMessage() throws Exception {
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(model().attribute("infoMessage", "ログアウトしました"));
    }

    @Test
    void login_whenSessionExpired_showsExpiredMessage() throws Exception {
        mockMvc.perform(get("/login").param("expired", ""))
                .andExpect(model().attribute("infoMessage", "ログインの有効期限が切れました。もう一度ログインしてください"));
    }

    @Test
    void login_whenAlreadyLoggedIn_redirectsToHome() throws Exception {
        var authenticated = UsernamePasswordAuthenticationToken.authenticated(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/login").principal(authenticated))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void login_whenAnonymous_staysOnLoginView() throws Exception {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/login").principal(anonymous))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void home_showsHomeView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));
    }
}

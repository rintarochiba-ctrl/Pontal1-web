package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.example.pontalweb.security.CognitoAuthClient;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.NewPasswordFailedException;
import com.example.pontalweb.security.NewPasswordRequiredException;

//初回ログイン(初期パスワード)の流れ全体を、実際に起動したアプリ(Tomcat + JSP)で確認する結合テスト
//  初期パスワードでログイン → 新しいパスワードの設定画面 → 設定 → ログイン画面に戻る
//Cognitoは、疑似のもの(Mock)にする
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class NewPasswordFlowIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private CognitoAuthenticationProvider authenticationProvider;

    @MockBean
    private CognitoAuthClient cognitoAuthClient;

    @BeforeEach
    void setUp() {
        when(authenticationProvider.supports(any())).thenReturn(true);
    }

    // ---------- 流れを進める部品 ----------

    private String csrfOf(String html) {
        Matcher matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("CSRFトークン").isTrue();
        return matcher.group(1);
    }

    private String cookieOf(ResponseEntity<String> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    private HttpHeaders formHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    //初期パスワードで、ログインを送って、新しいパスワードの設定画面に進んだ状態にする。使うセッションのCookieを返す
    private String loginWithInitialPassword() {
        when(authenticationProvider.authenticate(any()))
                .thenThrow(new NewPasswordRequiredException("new@example.com", "cognito-session"));

        ResponseEntity<String> loginPage = rest.getForEntity("/login", String.class);
        String cookie = cookieOf(loginPage);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrfOf(loginPage.getBody()));
        form.add("email", "new@example.com");
        form.add("password", "initial-password");

        ResponseEntity<String> response = rest.postForEntity("/login", new HttpEntity<>(form, formHeaders(cookie)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/new-password");
        return cookie;
    }

    private ResponseEntity<String> openNewPasswordPage(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange("/new-password", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> submitNewPassword(String cookie, String csrf, String newPassword, String confirm) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrf);
        form.add("newPassword", newPassword);
        form.add("confirmPassword", confirm);
        return rest.postForEntity("/new-password", new HttpEntity<>(form, formHeaders(cookie)), String.class);
    }

    // ---------- 流れ ----------

    @Test
    void initialPasswordLogin_leadsToNewPasswordForm_withFieldsAndToken() {
        String cookie = loginWithInitialPassword();

        ResponseEntity<String> page = openNewPasswordPage(cookie);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("name=\"newPassword\"").contains("name=\"confirmPassword\"");
        assertThat(page.getBody()).contains("name=\"_csrf\"");
        assertThat(page.getBody()).contains("パスワードの設定");
    }

    @Test
    void newPasswordPage_withoutFirstLogin_endsUpOnLoginPage() {
        //URLを直接開いた(初回ログインの途中ではない)。ログイン画面に移動する
        ResponseEntity<String> response = rest.getForEntity("/new-password", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("name=\"email\"").doesNotContain("name=\"newPassword\"");
    }

    @Test
    void settingNewPassword_succeeds_andReturnsToLoginWithMessage() {
        String cookie = loginWithInitialPassword();
        String csrf = csrfOf(openNewPasswordPage(cookie).getBody());

        ResponseEntity<String> response = submitNewPassword(cookie, csrf, "NewPass123!", "NewPass123!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).endsWith("/login?passwordset");
        verify(cognitoAuthClient).completeNewPassword("new@example.com", "NewPass123!", "cognito-session");

        //ログイン画面に、完了のメッセージが出る
        ResponseEntity<String> loginPage = rest.getForEntity("/login?passwordset", String.class);
        assertThat(loginPage.getBody()).contains("新しいパスワードを設定しました");
    }

    @Test
    void settingNewPassword_afterSuccess_cannotBeRepeated() {
        String cookie = loginWithInitialPassword();
        String csrf = csrfOf(openNewPasswordPage(cookie).getBody());
        submitNewPassword(cookie, csrf, "NewPass123!", "NewPass123!");

        //設定が終わったあとは、同じセッションで、設定画面を開いても、ログイン画面になる(途中の情報は、消えている)
        ResponseEntity<String> again = openNewPasswordPage(cookie);

        assertThat(again.getBody()).doesNotContain("name=\"newPassword\"");
    }

    @Test
    void whenConfirmationDiffers_showsMessage_andKeepsForm() {
        String cookie = loginWithInitialPassword();
        String csrf = csrfOf(openNewPasswordPage(cookie).getBody());

        ResponseEntity<String> response = submitNewPassword(cookie, csrf, "NewPass123!", "Different1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("確認用のパスワードが、一致しません");
        assertThat(response.getBody()).contains("name=\"newPassword\"");
        //入力したパスワードは、画面に出し直さない
        assertThat(response.getBody()).doesNotContain("NewPass123!").doesNotContain("Different1!");
    }

    @Test
    void whenPasswordRulesNotMet_showsCognitoMessage_andAllowsRetry() {
        String cookie = loginWithInitialPassword();
        String csrf = csrfOf(openNewPasswordPage(cookie).getBody());
        doThrow(new NewPasswordFailedException("パスワードが、ルールを満たしていません", false, null))
                .when(cognitoAuthClient).completeNewPassword(any(), any(), any());

        ResponseEntity<String> response = submitNewPassword(cookie, csrf, "short", "short");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("パスワードが、ルールを満たしていません");
        //入力し直せるよう、設定画面は、そのまま開ける
        assertThat(openNewPasswordPage(cookie).getBody()).contains("name=\"newPassword\"");
    }

    @Test
    void whenCognitoSessionExpired_returnsToLoginWithMessage() {
        String cookie = loginWithInitialPassword();
        String csrf = csrfOf(openNewPasswordPage(cookie).getBody());
        doThrow(new NewPasswordFailedException("時間が経ちすぎました", true, null))
                .when(cognitoAuthClient).completeNewPassword(any(), any(), any());

        ResponseEntity<String> response = submitNewPassword(cookie, csrf, "NewPass123!", "NewPass123!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).endsWith("/login?error=newpasswordexpired");
        assertThat(rest.getForEntity("/login?error=newpasswordexpired", String.class).getBody())
                .contains("時間が経ちすぎました");
    }

    @Test
    void submit_withoutCsrfToken_isRejected() {
        String cookie = loginWithInitialPassword();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("newPassword", "NewPass123!");
        form.add("confirmPassword", "NewPass123!");

        ResponseEntity<String> response = rest.postForEntity("/new-password", new HttpEntity<>(form, formHeaders(cookie)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

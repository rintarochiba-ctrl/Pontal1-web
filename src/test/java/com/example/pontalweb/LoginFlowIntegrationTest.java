package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

//アプリ全体を、実際に起動して(Tomcat + JSP)、HTTPで確認する結合テスト
//MockMvcは、JSPへの内部の転送(FORWARD)を実行しないので、「ログイン画面が、実際に表示されること」は、ここでしか確認できない
//(Cognitoにはつながない。ログインの送信は、メールとパスワードが空のときの動きだけを確認する)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LoginFlowIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void unauthenticated_root_endsUpOnLoginPage() {
        //TestRestTemplateは、GETのリダイレクトを、自動で追いかける。移動した先が、ログイン画面であること
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("name=\"email\"").contains("name=\"password\"");
    }

    @Test
    void loginPage_isRenderedAsHtml_withFormAndCsrfToken() {
        ResponseEntity<String> response = rest.getForEntity("/login", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/html");
        String body = response.getBody();
        assertThat(body).contains("name=\"email\"").contains("name=\"password\"");
        assertThat(body).contains("name=\"_csrf\"");
        assertThat(body).contains("ログイン");
    }

    @Test
    void css_isServedWithoutLogin() {
        ResponseEntity<String> response = rest.getForEntity("/css/app.css", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(".login-card");
    }

    @Test
    void protectedPages_whenNotLoggedIn_endUpOnLoginPage() {
        //存在しないURLも、権限が要るURL(給与管理・監査ログ)も、未ログインなら、ログイン画面になる
        for (String path : List.of("/no-such-page", "/payroll", "/audit-logs", "/employees")) {
            ResponseEntity<String> response = rest.getForEntity(path, String.class);

            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(path).contains("name=\"email\"");
        }
    }

    @Test
    void loginPost_withoutCsrfToken_isRejected() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", "a@example.com");
        form.add("password", "pass");

        ResponseEntity<String> response = rest.postForEntity("/login", new HttpEntity<>(form, formHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loginPost_withEmptyCredentials_goesBackToLoginWithError() {
        //ログイン画面を開いて、CSRFトークンと、セッションのCookieを受け取る
        ResponseEntity<String> page = rest.getForEntity("/login", String.class);
        String csrf = extractCsrf(page.getBody());
        String cookie = page.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrf);
        form.add("email", "");
        form.add("password", "");
        HttpHeaders headers = formHeaders();
        if (cookie != null) {
            headers.add(HttpHeaders.COOKIE, cookie.split(";", 2)[0]);
        }

        ResponseEntity<String> response = rest.postForEntity("/login", new HttpEntity<>(form, headers), String.class);

        //空欄は、Cognitoに問い合わせず、「メールアドレスまたはパスワードが違います」として、ログイン画面に戻る
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).contains("/login?error=credentials");
    }

    @Test
    void loginPageWithErrorCode_showsMessage() {
        ResponseEntity<String> response = rest.getForEntity("/login?error=credentials", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("メールアドレスまたはパスワードが違います");
    }

    //ログイン画面のHTMLから、CSRFトークンの値を取り出す
    private String extractCsrf(String html) {
        Matcher matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpHeaders formHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        return headers;
    }
}

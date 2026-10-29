package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//画面を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テストの、共通の土台
//  ・Cognito(ログインの確認)と、PontalのAPIは、疑似のもの(Mock)にする
//  ・loginAs で、指定した権限の人として、ログインして、そのセッションのCookieを返す
//(JSPのEL式が、DTOの値を取れているか、は、MockMvcでは確認できない。この土台を使った結合テストで確認する)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
abstract class LoggedInPageTestBase {

    @Autowired
    protected TestRestTemplate rest;

    @MockBean
    protected CognitoAuthenticationProvider authenticationProvider;

    @MockBean
    protected PontalApiClient api;

    @BeforeEach
    void setUpAuthentication() {
        when(authenticationProvider.supports(any())).thenReturn(true);
    }

    //例: loginAs(new LoginUser(7L, "山田太郎", false, false), "ROLE_USER", "ROLE_HR_ADMIN")
    protected String loginAs(LoginUser user, String... roles) {
        when(authenticationProvider.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(user, "token-abc"), null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));

        ResponseEntity<String> page = rest.getForEntity("/login", String.class);
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.getBody());
        assertThat(csrf.find()).isTrue();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrf.group(1));
        form.add("email", "a@example.com");
        form.add("password", "pass");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookieOf(page));

        ResponseEntity<String> login = rest.postForEntity("/login", new HttpEntity<>(form, headers), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        //ログイン成功で、セッションIDは、新しいものに変わる
        return cookieOf(login);
    }

    private String cookieOf(ResponseEntity<String> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies.stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    //ログインしたセッションで、画面を開く(ステータスは、そのまま返す)
    protected ResponseEntity<String> get(String cookie, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    //ログインしたセッションで、画面を開いて、HTMLを返す(200でなければ、失敗。HTMLを、失敗の説明に出す)
    protected String openAs(String cookie, String path) {
        ResponseEntity<String> response = get(cookie, path);
        assertThat(response.getStatusCode()).as(path + "\n" + response.getBody()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}

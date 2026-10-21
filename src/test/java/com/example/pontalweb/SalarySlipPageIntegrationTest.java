package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
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
import com.example.pontalweb.client.SalarySlipSummary;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//給与明細の一覧を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
//(JSPのEL式が、DTOの値を取れているか、は、MockMvcでは確認できない)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SalarySlipPageIntegrationTest {

    private static final String LIST_URI = "/api/employees/{id}/salary-slips";

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private CognitoAuthenticationProvider authenticationProvider;

    @MockBean
    private PontalApiClient api;

    @BeforeEach
    void setUp() {
        when(authenticationProvider.supports(any())).thenReturn(true);
    }

    //一般ユーザー(社員ID=7)で、ログインして、そのセッションのCookieを返す(疑似のCognitoで成功させる)
    private String loginAsMember() {
        LoginUser user = new LoginUser(7L, "山田太郎", false, false);
        when(authenticationProvider.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(user, "token-abc"), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

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
        return cookieOf(login);
    }

    private String cookieOf(ResponseEntity<String> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    private String openSalarySlips(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> response = rest.exchange("/salary-slips", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_showsPayMonthsWithPdfLinks_inGivenOrder() {
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L)))
                .thenReturn(List.of(new SalarySlipSummary(3L, "2024-09"), new SalarySlipSummary(2L, "2024-08")));

        String body = openSalarySlips(loginAsMember());

        assertThat(body).contains("2024年09月支給分", "2024年08月支給分");
        assertThat(body.indexOf("2024年09月支給分")).isLessThan(body.indexOf("2024年08月支給分")); //新しい順のまま
        assertThat(body).contains("/salary-slips/3/download?payMonth=2024-09");
        assertThat(body).contains("/salary-slips/2/download?payMonth=2024-08");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_whenNoSlips_showsMessage_withoutTable() {
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L))).thenReturn(List.of());

        String body = openSalarySlips(loginAsMember());

        assertThat(body).contains("給与明細は、まだありません");
        assertThat(body).doesNotContain("<table");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_highlightsSalaryMenu() {
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L))).thenReturn(List.of());

        String body = openSalarySlips(loginAsMember());

        assertThat(body).containsPattern("nav-item active\"\\s+href=\"/salary-slips\"");
    }
}

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

import com.example.pontalweb.client.EmployeeListResponse;
import com.example.pontalweb.client.EmployeeSummary;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//社員一覧の画面を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
//ログインは、疑似のCognito(Mock)で成功させて、ログインしたあとのセッションで、一覧を開く
//(JSPのEL式が、DTOの値を取れているか、文字が正しく出力(エスケープ)されるか、は、MockMvcでは確認できない)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EmployeeListPageIntegrationTest {

    private static final String URI = "/api/employees?page={page}&size={size}";
    private static final String URI_WITH_KEYWORD = "/api/employees?page={page}&size={size}&keyword={keyword}";

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

    // ---------- ログイン(疑似のCognitoで成功させる) ----------

    //指定した権限で、ログインして、そのセッションのCookie(JSESSIONID=...)を返す
    private String loginAs(LoginUser user) {
        when(authenticationProvider.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(user, "token-abc"), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        ResponseEntity<String> page = rest.getForEntity("/login", String.class);
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.getBody());
        assertThat(csrf.find()).isTrue();
        String beforeLogin = cookieOf(page);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", csrf.group(1));
        form.add("email", "a@example.com");
        form.add("password", "pass");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, beforeLogin);

        ResponseEntity<String> login = rest.postForEntity("/login", new HttpEntity<>(form, headers), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        //ログイン成功で、セッションIDは、新しいものに変わる(ログイン前のIDを、使い回させないため)
        return cookieOf(login);
    }

    private String cookieOf(ResponseEntity<String> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    private String openAs(String cookie, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private LoginUser admin() {
        return new LoginUser(1L, "山田太郎", true, false);
    }

    private LoginUser member() {
        return new LoginUser(3L, "鈴木一郎", false, false);
    }

    private EmployeeSummary employee(long id, String name) {
        return new EmployeeSummary(id, name, "e" + id + "@example.com", "開発部", "メンバー");
    }

    private int count(String body, String text) {
        return body.split(Pattern.quote(text), -1).length - 1;
    }

    // ---------- 一覧の表示 ----------

    @Test
    void list_showsEmployeeCards_withNameDepartmentPositionAndDetailLink() {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(new EmployeeListResponse(
                        List.of(employee(1, "山田太郎"), employee(2, "佐藤花子")), 2, null, null));

        String body = openAs(loginAs(admin()), "/employees");

        assertThat(body).contains("山田太郎", "佐藤花子", "開発部", "メンバー");
        assertThat(count(body, "class=\"employee-card\"")).isEqualTo(2);
        assertThat(body).contains("/employees/1").contains("/employees/2"); //詳細へのリンク
        assertThat(body).contains("全 2 名");
        assertThat(body).contains("name=\"keyword\""); //検索欄
    }

    @Test
    void list_showsInitialOfNameAsAvatar() {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(new EmployeeListResponse(List.of(employee(1, "山田太郎")), 1, null, null));

        String body = openAs(loginAs(admin()), "/employees");

        assertThat(body).contains("class=\"employee-avatar\">山</span>");
    }

    @Test
    void list_whenOnePage_hidesPagination() {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(new EmployeeListResponse(List.of(employee(1, "山田太郎")), 8, null, null));

        String body = openAs(loginAs(admin()), "/employees");

        assertThat(body).doesNotContain("class=\"pagination\"");
    }

    @Test
    void list_whenManyPages_showsPaginationWithCurrentPage() {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(new EmployeeListResponse(List.of(employee(1, "山田太郎")), 20, 1, null));

        String body = openAs(loginAs(admin()), "/employees");

        assertThat(body).contains("class=\"pagination\"");
        assertThat(body).contains("<span class=\"current\">1</span>"); //いまのページ(画面では1始まり)
        assertThat(body).contains("page=1").contains("page=2"); //次のページへのリンク(URLでは0始まり)
        assertThat(body).contains("<span class=\"disabled\">‹</span>"); //1ページ目に、「前へ」は無い
    }

    @Test
    void list_onSecondPage_marksSecondPageAsCurrent() {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(1), eq(8)))
                .thenReturn(new EmployeeListResponse(List.of(employee(9, "最後の人")), 9, null, 0));

        String body = openAs(loginAs(admin()), "/employees?page=1");

        assertThat(body).contains("<span class=\"current\">2</span>");
        assertThat(body).contains("<span class=\"disabled\">›</span>"); //最後のページに、「次へ」は無い
    }

    @Test
    void list_whenNoEmployees_showsZeroCount_withoutCards() {
        when(api.get(eq(URI_WITH_KEYWORD), eq(EmployeeListResponse.class), eq(0), eq(8), eq("存在しない")))
                .thenReturn(new EmployeeListResponse(List.of(), 0, null, null));

        String body = openAs(loginAs(admin()), "/employees?keyword=存在しない");

        assertThat(body).contains("全 0 名");
        assertThat(count(body, "class=\"employee-card\"")).isZero();
    }

    // ---------- 検索 ----------

    @Test
    void list_withKeyword_keepsKeywordInSearchBox_andInPaginationLinks() {
        when(api.get(eq(URI_WITH_KEYWORD), eq(EmployeeListResponse.class), eq(0), eq(8), eq("開発")))
                .thenReturn(new EmployeeListResponse(List.of(employee(1, "山田太郎")), 20, 1, null));

        String body = openAs(loginAs(admin()), "/employees?keyword=開発");

        assertThat(body).contains("value=\"開発\""); //検索欄に、入力した文字が、残る
        assertThat(body).contains("keyword="); //ページ送りのリンクにも、検索の条件が付く
    }

    // ---------- 権限による出し分け ----------

    @Test
    void list_showsNewRegistrationButton_onlyToSystemAdmin() {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(new EmployeeListResponse(List.of(employee(1, "山田太郎")), 1, null, null));

        String forAdmin = openAs(loginAs(admin()), "/employees");
        String forMember = openAs(loginAs(member()), "/employees");

        assertThat(forAdmin).contains("新規登録");
        assertThat(forMember).doesNotContain("新規登録");
    }

    // ---------- 表示する文字の安全性(XSS) ----------

    @Test
    void list_escapesHtmlInNamesAndKeyword() {
        String evil = "<script>alert(1)</script>";
        when(api.get(eq(URI_WITH_KEYWORD), eq(EmployeeListResponse.class), eq(0), eq(8), eq("\"><b>x</b>")))
                .thenReturn(new EmployeeListResponse(List.of(employee(1, evil)), 1, null, null));

        String body = openAs(loginAs(admin()), "/employees?keyword=\"><b>x</b>");

        //名前や、検索の文字に、HTMLが入っていても、実行されず、文字としてそのまま表示される
        assertThat(body).doesNotContain(evil);
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(body).doesNotContain("\"><b>x</b>");
    }
}

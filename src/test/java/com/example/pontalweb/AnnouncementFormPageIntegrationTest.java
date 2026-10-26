package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.example.pontalweb.client.AnnouncementDetail;
import com.example.pontalweb.client.AnnouncementRequest;
import com.example.pontalweb.client.EmployeeListResponse;
import com.example.pontalweb.client.EmployeeSummary;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//お知らせの作成・編集の画面を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身と、送信の流れを確認する結合テスト
//(JSPのEL式、checked / selected / disabled の付き方、入力のまちがいの出し直し、CSRF、権限は、MockMvcでは確認しきれない)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AnnouncementFormPageIntegrationTest {

    private static final String ITEM_URI = "/api/announcements/{id}";
    private static final String EMPLOYEES_URI = "/api/employees?page=0&size=100";

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private CognitoAuthenticationProvider authenticationProvider;

    @MockBean
    private PontalApiClient api;

    @BeforeEach
    void setUp() {
        when(authenticationProvider.supports(any())).thenReturn(true);
        when(api.get(eq("/api/departments"), any(ParameterizedTypeReference.class)))
                .thenReturn(List.of("開発部", "人事部", "営業部"));
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeListResponse.class)))
                .thenReturn(new EmployeeListResponse(List.of(
                        new EmployeeSummary(1L, "山田太郎", "a@example.com", "開発部", "エンジニア"),
                        new EmployeeSummary(3L, "鈴木一郎", "c@example.com", "営業部", "主任")), 2, null, null));
    }

    // ---------- ログイン(疑似のCognitoで成功させる)。権限も、本物と同じように付ける ----------

    private String loginAs(LoginUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.isSystemAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        }
        if (user.isHrAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_HR_ADMIN"));
        }
        when(authenticationProvider.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(user, "token-abc"), null, authorities));

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
        return cookieOf(login);
    }

    private String cookieOf(ResponseEntity<String> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    private ResponseEntity<String> get(String cookie, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String open(LoginUser user, String path) {
        ResponseEntity<String> response = get(loginAs(user), path);
        assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    //フォームを送る。csrfToken が null なら、CSRFトークンを付けない
    private ResponseEntity<String> post(String cookie, String path, String csrfToken, String... nameValues) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (csrfToken != null) {
            form.add("_csrf", csrfToken);
        }
        for (int i = 0; i < nameValues.length; i += 2) {
            form.add(nameValues[i], nameValues[i + 1]);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.postForEntity(path, new HttpEntity<>(form, headers), String.class);
    }

    private String csrfOf(String html) {
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(csrf.find()).as("フォームにCSRFトークンがある").isTrue();
        return csrf.group(1);
    }

    private LoginUser systemAdmin() {
        return new LoginUser(1L, "山田太郎", true, false);
    }

    private LoginUser hrAdmin() {
        return new LoginUser(2L, "佐藤花子", false, true);
    }

    private LoginUser member() {
        return new LoginUser(3L, "鈴木一郎", false, false);
    }

    private AnnouncementDetail detail(long id, String targetType, String department, Long employeeId) {
        return new AnnouncementDetail(id, "年末調整のお願い", "対応依頼", true, "総務部", targetType, "ラベル",
                "2026-10-22T10:30:00", "1行目\n2行目", "2026-10-22T10:30:00", true, false, department, employeeId);
    }

    private void stubDetail(AnnouncementDetail detail) {
        when(api.get(eq(ITEM_URI), eq(AnnouncementDetail.class), eq(detail.id()))).thenReturn(detail);
    }

    // ---------- 作成画面 ----------

    @Test
    void newForm_showsEmptyForm_withTargetAllChecked_andTargetSelectsDisabled() {
        String body = open(systemAdmin(), "/announcements/new");

        assertThat(body).contains("<h1>お知らせの作成</h1>");
        assertThat(body).contains("action=\"/announcements/new\"");
        assertThat(body).containsPattern("name=\"_csrf\" value=\"[^\"]+\"");
        assertThat(body).containsPattern("<input id=\"title\"[^>]*value=\"\"");
        assertThat(body).containsPattern("<textarea[^>]*></textarea>");
        //宛先は、全員から始まる。部署と個人のプルダウンは、使えない
        assertThat(body).containsPattern("value=\"ALL\"\\s+checked");
        assertThat(body).doesNotContainPattern("value=\"DEPARTMENT\"\\s+checked");
        assertThat(body).containsPattern("<select id=\"targetDepartment\"[^>]*disabled");
        assertThat(body).containsPattern("<select id=\"targetEmployeeId\"[^>]*disabled");
        //重要は、チェックなし。種類は、選ばれていない
        assertThat(body).doesNotContainPattern("name=\"important\"[^>]*checked");
        assertThat(body).doesNotContainPattern("<option[^>]*\\sselected");
        assertThat(body).contains("href=\"/announcements\">キャンセル</a>");
    }

    @Test
    void newForm_showsChoices_forCategoryDepartmentAndEmployee() {
        String body = open(systemAdmin(), "/announcements/new");

        assertThat(body).contains("<option value=\"技術\"", "<option value=\"社内情報\"", "<option value=\"対応依頼\"");
        assertThat(body).contains("<option value=\"開発部\"", "<option value=\"人事部\"", "<option value=\"営業部\"");
        assertThat(body).contains("<option value=\"1\"", "山田太郎(開発部)");
        assertThat(body).contains("<option value=\"3\"", "鈴木一郎(営業部)");
    }

    @Test
    void newForm_hasScriptThatTogglesTargetSelects() {
        assertThat(open(systemAdmin(), "/announcements/new")).contains("addEventListener('change'");
    }

    @Test
    void newForm_isOpenToHrAdmin_too() {
        assertThat(open(hrAdmin(), "/announcements/new")).contains("<h1>お知らせの作成</h1>");
    }

    @Test
    void newForm_isForbidden_forGeneralMember() {
        ResponseEntity<String> response = get(loginAs(member()), "/announcements/new");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- 編集画面 ----------

    @Test
    void editForm_showsCurrentContent_withDepartmentTarget() {
        stubDetail(detail(7, "DEPARTMENT", "開発部", null));

        String body = open(systemAdmin(), "/announcements/7/edit");

        assertThat(body).contains("<h1>お知らせの編集</h1>");
        assertThat(body).contains("action=\"/announcements/7/edit\"");
        assertThat(body).contains("href=\"/announcements/7\">キャンセル</a>"); //キャンセルは、詳細へ
        assertThat(body).containsPattern("<input id=\"title\"[^>]*value=\"年末調整のお願い\"");
        assertThat(body).containsPattern("<textarea[^>]*>1行目\n2行目</textarea>");
        assertThat(body).containsPattern("<option value=\"対応依頼\"\\s+selected>");
        assertThat(body).containsPattern("name=\"important\"[^>]*checked");
        //宛先は、部署。部署のプルダウンは使えて、開発部が選ばれている。個人のプルダウンは、使えない
        assertThat(body).containsPattern("value=\"DEPARTMENT\"\\s+checked");
        assertThat(body).doesNotContainPattern("<select id=\"targetDepartment\"[^>]*disabled");
        assertThat(body).containsPattern("<option value=\"開発部\"\\s+selected>");
        assertThat(body).containsPattern("<select id=\"targetEmployeeId\"[^>]*disabled");
    }

    @Test
    void editForm_withEmployeeTarget_selectsThatEmployee() {
        stubDetail(detail(7, "EMPLOYEE", null, 3L));

        String body = open(systemAdmin(), "/announcements/7/edit");

        assertThat(body).containsPattern("value=\"EMPLOYEE\"\\s+checked");
        assertThat(body).doesNotContainPattern("<select id=\"targetEmployeeId\"[^>]*disabled");
        assertThat(body).containsPattern("<option value=\"3\"\\s+selected>鈴木一郎");
        assertThat(body).containsPattern("<select id=\"targetDepartment\"[^>]*disabled");
    }

    @Test
    void editForm_withAllTarget_checksAll() {
        stubDetail(detail(7, "ALL", null, null));

        String body = open(systemAdmin(), "/announcements/7/edit");

        assertThat(body).containsPattern("value=\"ALL\"\\s+checked");
        assertThat(body).containsPattern("<select id=\"targetDepartment\"[^>]*disabled");
        assertThat(body).containsPattern("<select id=\"targetEmployeeId\"[^>]*disabled");
    }

    @Test
    void editForm_escapesTitleAndBody() {
        AnnouncementDetail evil = new AnnouncementDetail(7L, "\"><script>alert(1)</script>", "技術", false, "総務部", "ALL",
                "全員", "2026-10-22T10:30:00", "</textarea><script>alert(2)</script>", "2026-10-22T10:30:00", true,
                false, null, null);
        stubDetail(evil);

        String body = open(systemAdmin(), "/announcements/7/edit");

        assertThat(body).doesNotContain("<script>alert(1)</script>").doesNotContain("<script>alert(2)</script>");
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;").contains("&lt;/textarea&gt;");
    }

    @Test
    void editForm_whenCannotEdit_isForbidden() {
        AnnouncementDetail readOnly = new AnnouncementDetail(7L, "t", "技術", false, "総務部", "ALL", "全員",
                "2026-10-22T10:30:00", "b", "2026-10-22T10:30:00", false, false, null, null);
        stubDetail(readOnly);

        ResponseEntity<String> response = get(loginAs(systemAdmin()), "/announcements/7/edit");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void editForm_isForbidden_forGeneralMember_withoutCallingApi() {
        ResponseEntity<String> response = get(loginAs(member()), "/announcements/7/edit");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(api, never()).get(eq(ITEM_URI), eq(AnnouncementDetail.class), eq(7L));
    }

    // ---------- 送信: 入力のまちがい ----------

    @Test
    void create_withInvalidInput_showsErrorsUnderFields_andKeepsInput() {
        String cookie = loginAs(systemAdmin());
        String csrf = csrfOf(get(cookie, "/announcements/new").getBody());

        ResponseEntity<String> response = post(cookie, "/announcements/new", csrf,
                "title", "", "body", "書きかけの本文", "category", "", "targetType", "DEPARTMENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); //redirectせず、フォームを出し直す
        String body = response.getBody();
        assertThat(body).contains("タイトルを入力してください", "種類を選んでください", "宛先の部署を選んでください");
        assertThat(body).doesNotContain("本文を入力してください"); //本文は、入力してある
        assertThat(body).contains("書きかけの本文"); //入力した内容が、残る
        assertThat(body).containsPattern("value=\"DEPARTMENT\"\\s+checked"); //選んだ宛先も、残る
        assertThat(body).contains("form-control is-invalid"); //エラーの欄に、赤い枠
        verify(api, never()).post(any(String.class), any(), any(Class.class));
    }

    @Test
    void create_whenApiRejects_showsApiMessageAboveForm() {
        when(api.post(eq("/api/announcements"), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenThrow(new com.example.pontalweb.exception.BadRequestException("部署が未設定のため、お知らせを投稿できません"));
        String cookie = loginAs(systemAdmin());
        String csrf = csrfOf(get(cookie, "/announcements/new").getBody());

        ResponseEntity<String> response = post(cookie, "/announcements/new", csrf,
                "title", "t", "body", "b", "category", "技術", "targetType", "ALL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("alert alert-error").contains("部署が未設定のため、お知らせを投稿できません");
    }

    // ---------- 送信: 成功 ----------

    @Test
    void create_withValidInput_redirectsToDetail_andShowsMessageOnce() {
        stubDetail(detail(9, "ALL", null, null));
        when(api.post(eq("/api/announcements"), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenReturn(detail(9, "ALL", null, null));
        String cookie = loginAs(systemAdmin());
        String csrf = csrfOf(get(cookie, "/announcements/new").getBody());

        ResponseEntity<String> response = post(cookie, "/announcements/new", csrf,
                "title", "年末調整のお願い", "body", "本文", "category", "対応依頼", "important", "true", "targetType", "ALL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/announcements/9");
        //redirect先の詳細に、「作成しました」が出る。もう一度開くと、消えている(1回だけ)
        assertThat(get(cookie, "/announcements/9").getBody()).contains("お知らせを作成しました");
        assertThat(get(cookie, "/announcements/9").getBody()).doesNotContain("お知らせを作成しました");
    }

    @Test
    void update_withValidInput_redirectsToDetail_andShowsMessage() {
        stubDetail(detail(7, "DEPARTMENT", "開発部", null));
        when(api.put(eq(ITEM_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class), eq(7L)))
                .thenReturn(detail(7, "DEPARTMENT", "開発部", null));
        String cookie = loginAs(systemAdmin());
        String csrf = csrfOf(get(cookie, "/announcements/7/edit").getBody());

        ResponseEntity<String> response = post(cookie, "/announcements/7/edit", csrf,
                "title", "変更後", "body", "本文", "category", "技術", "targetType", "EMPLOYEE", "targetEmployeeId", "3");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/announcements/7");
        assertThat(get(cookie, "/announcements/7").getBody()).contains("お知らせを更新しました");
    }

    // ---------- CSRF・権限 ----------

    @Test
    void create_withoutCsrfToken_isRejected_andDoesNotCallApi() {
        String cookie = loginAs(systemAdmin());

        ResponseEntity<String> response = post(cookie, "/announcements/new", null,
                "title", "t", "body", "b", "category", "技術", "targetType", "ALL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(api, never()).post(any(String.class), any(), any(Class.class));
    }

    @Test
    void create_byGeneralMember_isRejected_evenWithCsrfToken() {
        String cookie = loginAs(member());
        //一般の社員は、フォームを開けないので、トークンは、別の画面から取る
        String csrf = csrfOf(get(cookie, "/login").getBody());

        ResponseEntity<String> response = post(cookie, "/announcements/new", csrf,
                "title", "t", "body", "b", "category", "技術", "targetType", "ALL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(api, never()).post(any(String.class), any(), any(Class.class));
    }
}

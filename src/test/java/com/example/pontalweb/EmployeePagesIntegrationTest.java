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

import com.example.pontalweb.client.EmployeeDetail;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//社員の詳細・登録・編集の画面を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
//(JSPのEL式が、DTOの値を取れているか、は、MockMvcでは確認できない。ここで確認する)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EmployeePagesIntegrationTest {

    private static final String URI = "/api/employees/{id}";

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

    private String loginAs(LoginUser user) {
        when(authenticationProvider.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(user, "token-abc"), null, List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));

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

    private String openAs(String cookie, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as(path + "\n" + response.getBody()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private LoginUser admin() {
        return new LoginUser(1L, "管理者", true, false);
    }

    private EmployeeDetail detail() {
        return new EmployeeDetail(3L, "山田太郎", "test@example.com", "開発部", "エンジニア",
                "2018-04-01", "男性", 38, "東京都", "キャンプ", "よろしくお願いします",
                "[{\"question\":\"好きな食べ物は?\",\"answer\":\"ラーメン\"}]", "employees/3.png", true, false);
    }

    private int count(String body, String text) {
        return body.split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }

    // ---------- 詳細 ----------

    @Test
    void detail_showsProfile() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail());

        String body = openAs(loginAs(admin()), "/employees/3");

        assertThat(body).contains("山田太郎", "開発部", "エンジニア", "test@example.com", "男性", "38歳", "東京都");
        assertThat(body).contains("好きな食べ物は?", "ラーメン"); //Q&A(JSON)が、質問と答えに分かれて出る
    }

    @Test
    void detail_showsDeleteButton_forOtherEmployee_withConfirmAndCsrfToken() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail());

        String body = openAs(loginAs(admin()), "/employees/3"); //ログイン中は id=1

        assertThat(body).contains("action=\"/employees/3/delete\"");
        assertThat(body).contains("onsubmit=\"return confirm(");
        assertThat(body).contains("name=\"_csrf\"");
    }

    @Test
    void detail_hidesDeleteButton_forOwnAccount() {
        EmployeeDetail self = new EmployeeDetail(1L, "管理者", "admin@example.com", null, null,
                "2018-04-01", null, null, null, null, null, null, null, true, false);
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(1L))).thenReturn(self);

        String body = openAs(loginAs(admin()), "/employees/1");

        assertThat(body).doesNotContain("/employees/1/delete");
        assertThat(body).contains("/employees/1/edit"); //編集は、自分でもできる
    }

    // ---------- 登録 ----------

    @Test
    void register_showsEmptyForm() {
        String body = openAs(loginAs(admin()), "/employees/new");

        assertThat(body).contains("name=\"initialPassword\"", "name=\"joinDate\"", "isSystemAdmin");
    }

    // ---------- 必須の印(赤い＊)と、入力中のチェック ----------

    private static final String REQUIRED_MARK = "<span class=\"required\">*</span>";

    //ラベル(<label for="...">...</label>)の中の文字列を、取り出す
    private String labelOf(String body, String inputId) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<label for=\"" + inputId + "\">(.*?)</label>", java.util.regex.Pattern.DOTALL).matcher(body);
        assertThat(m.find()).as(inputId + " のラベルがある").isTrue();
        return m.group(1);
    }

    @Test
    void register_marksRequiredFieldsWithRedAsterisk_andOnlyThose() {
        String body = openAs(loginAs(admin()), "/employees/new");

        //必須(API006): 氏名・メールアドレス・初期パスワード・入社日・部署
        for (String id : List.of("name", "email", "initialPassword", "joinDate", "department")) {
            assertThat(labelOf(body, id)).as(id).contains(REQUIRED_MARK);
        }
        //必須ではない: 役職
        assertThat(labelOf(body, "position")).doesNotContain(REQUIRED_MARK);
        //「＊は必須項目です」の説明も、ある
        assertThat(body).contains("class=\"text-muted required-note\"");
    }

    @Test
    void register_everyInputHasLabelForLiveValidation_andLoadsScriptBeforePasswordScript() {
        String body = openAs(loginAs(admin()), "/employees/new");

        //入力中のチェックの目印(data-label)が、6つの入力欄に付いている
        for (String label : List.of("氏名", "メールアドレス", "初期パスワード", "入社日", "部署", "役職")) {
            assertThat(body).contains("data-label=\"" + label + "\"");
        }
        assertThat(count(body, "data-label=")).isEqualTo(6);
        //チェックのスクリプトを読み込む。パスワードの条件(PontalValidation)を使う、下のスクリプトより、先に読み込む
        int load = body.indexOf("/js/form-validation.js");
        int use = body.indexOf("PontalValidation.passwordRules");
        assertThat(load).isPositive();
        assertThat(use).isGreaterThan(load);
    }

    @Test
    void register_keepsPasswordRuleList_forTheCheckmark() {
        String body = openAs(loginAs(admin()), "/employees/new");

        //パスワードの条件の一覧は、そのまま、ある(✓の判断は、同じ条件を使う)
        assertThat(body).contains("id=\"passwordRules\"");
        for (String rule : List.of("length", "lower", "upper", "number", "symbol")) {
            assertThat(body).contains("data-rule=\"" + rule + "\"");
        }
    }

    @Test
    void register_requiredAttributes_stayForBrowserCheck() {
        String body = openAs(loginAs(admin()), "/employees/new");

        //ブラウザ自身のチェック(required)も、そのまま残っている(スクリプトが動かなくても、空では、送れない)
        assertThat(body).containsPattern("id=\"name\"[^>]*\\srequired");
        assertThat(body).containsPattern("id=\"email\"[^>]*\\srequired");
        assertThat(body).containsPattern("id=\"initialPassword\"[^>]*\\srequired");
        assertThat(body).containsPattern("id=\"joinDate\"[^>]*\\srequired");
        assertThat(body).containsPattern("id=\"department\"[^>]*\\srequired");
        assertThat(body).doesNotContainPattern("id=\"position\"[^>]*\\srequired");
    }

    @Test
    void edit_marksOnlyNameAndJoinDateAsRequired() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail());

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        //必須(API007): 氏名・入社日
        assertThat(labelOf(body, "name")).contains(REQUIRED_MARK);
        assertThat(labelOf(body, "joinDate")).contains(REQUIRED_MARK);
        //ほかは、必須ではない
        for (String id : List.of("department", "position", "gender", "age", "birthplace", "hobby", "bio")) {
            assertThat(labelOf(body, id)).as(id).doesNotContain(REQUIRED_MARK);
        }
        assertThat(count(body, REQUIRED_MARK)).isEqualTo(3); //ラベル2つ + 「＊は必須項目です」の説明1つ
    }

    @Test
    void edit_hasLabelsForLiveValidation_andLoadsScript() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail());

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        for (String label : List.of("氏名", "入社日", "部署", "役職", "性別", "年齢", "出身地", "趣味や休日の過ごし方")) {
            assertThat(body).contains("data-label=\"" + label + "\"");
        }
        assertThat(count(body, "data-label=")).isEqualTo(8);
        assertThat(body).contains("src=\"/js/form-validation.js\"");
        //編集画面には、パスワード欄が無いので、パスワードの条件は、出ない
        assertThat(body).doesNotContain("passwordRules");
    }

    @Test
    void validationScript_isServedToLoggedInUser_withTheSharedPasswordRules() {
        ResponseEntity<String> response = rest.exchange("/js/form-validation.js", HttpMethod.GET,
                new HttpEntity<>(cookieHeaders(loginAs(admin()))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("PontalValidation", "passwordRules", "data-label");
    }

    private HttpHeaders cookieHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    // ---------- 編集 ----------

    @Test
    void edit_showsCurrentValuesInForm() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail());

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        assertThat(body).contains("value=\"山田太郎\"", "value=\"2018-04-01\"", "value=\"開発部\"", "value=\"男性\"");
        assertThat(body).contains("value=\"38\"", "value=\"東京都\"");
        assertThat(body).contains(">キャンプ</textarea>", ">よろしくお願いします</textarea>");
        assertThat(body).contains("test@example.com"); //メールアドレスは、表示だけ
        //画面で編集しない項目は、隠しの欄で、いまの値を持つ(全項目を置き換える API に、送り直すため)
        assertThat(body).contains("name=\"image\" value=\"employees/3.png\"");
    }

    @Test
    void edit_checksRoleCheckboxes_accordingToCurrentRoles() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail()); //システム管理者だけ true

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        assertThat(body).containsPattern("name=\"isSystemAdmin\" value=\"true\" checked");
        assertThat(body).doesNotContainPattern("name=\"isHrAdmin\" value=\"true\" checked");
    }

    @Test
    void edit_showsThreeQaRows_withCurrentValuesFilledIn() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail()); //Q&Aが1組

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        assertThat(count(body, "name=\"qaQuestion\"")).isEqualTo(3);
        assertThat(count(body, "name=\"qaAnswer\"")).isEqualTo(3);
        assertThat(body).contains("value=\"好きな食べ物は?\"", ">ラーメン</textarea>");
        assertThat(body).doesNotContain("name=\"selfQa\""); //JSONの文字そのものは、画面に出さない
    }

    @Test
    void edit_whenSelfQaIsPlainText_putsItIntoFirstAnswer_soItIsNotLost() {
        EmployeeDetail legacy = new EmployeeDetail(3L, "山田太郎", "a@example.com", null, null, "2018-04-01",
                null, null, null, null, null, "休日はキャンプです", null, false, false);
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(legacy);

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        assertThat(body).contains(">休日はキャンプです</textarea>");
        assertThat(body).contains("これまでの文字が、1つ目の答えに入っています");
    }

    @Test
    void edit_whenNoQa_showsEmptyRows_withoutLegacyNotice() {
        EmployeeDetail none = new EmployeeDetail(3L, "山田太郎", "a@example.com", null, null, "2018-04-01",
                null, null, null, null, null, null, null, false, false);
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(none);

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        assertThat(count(body, "name=\"qaQuestion\"")).isEqualTo(3);
        assertThat(body).doesNotContain("これまでの文字が、1つ目の答えに入っています");
    }

    @Test
    void edit_ownProfile_locksSystemAdminCheckbox() {
        //ログイン中の人(id=1)が、自分(id=1)の編集画面を開いた。システム管理者のチェックが、固定される
        EmployeeDetail self = new EmployeeDetail(1L, "管理者", "admin@example.com", null, null,
                "2018-04-01", null, null, null, null, null, null, null, true, false);
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(1L))).thenReturn(self);

        String body = openAs(loginAs(admin()), "/employees/1/edit");

        assertThat(body).containsPattern("name=\"isSystemAdmin\" value=\"true\" checked\\s+disabled");
        assertThat(body).contains("自分自身のシステム管理者の権限は、外せません");
    }

    @Test
    void edit_otherProfile_doesNotLockSystemAdminCheckbox() {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(detail());

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        assertThat(body).doesNotContainPattern("name=\"isSystemAdmin\"[^>]*disabled");
        assertThat(body).doesNotContain("自分自身のシステム管理者の権限は、外せません");
    }

    @Test
    void edit_escapesHtmlInValues() {
        EmployeeDetail evil = new EmployeeDetail(3L, "\"><script>alert(1)</script>", "a@example.com", null, null,
                "2018-04-01", null, null, null, "</textarea><script>alert(2)</script>", null, null, null, false, false);
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(evil);

        String body = openAs(loginAs(admin()), "/employees/3/edit");

        assertThat(body).doesNotContain("<script>alert(1)</script>").doesNotContain("<script>alert(2)</script>");
    }
}

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

import com.example.pontalweb.client.AnnouncementItem;
import com.example.pontalweb.client.AnnouncementListResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//ホームの「ハロワお知らせ」を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
//(JSPのEL式がDTOの値を取れているか、タグファイルが動くか、文字が正しくエスケープされるか、は、MockMvcでは確認できない)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HomePageIntegrationTest {

    private static final String URI = "/api/announcements?scope={scope}&size={size}";

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

    // ---------- ログイン(疑似のCognitoで成功させる)。EmployeeListPageIntegrationTestと同じ ----------

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
        return cookieOf(login);
    }

    private String cookieOf(ResponseEntity<String> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    private String openHome() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, loginAs(new LoginUser(1L, "山田太郎", false, false)));
        ResponseEntity<String> response = rest.exchange("/", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private AnnouncementItem item(long id, String title, boolean important) {
        return new AnnouncementItem(id, title, "対応依頼", important, "総務部", "ALL", "全員", "2026-10-22T10:30:00");
    }

    private void stub(String scope, AnnouncementListResponse response) {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq(scope), eq(5))).thenReturn(response);
    }

    private AnnouncementListResponse listOf(AnnouncementItem... items) {
        return new AnnouncementListResponse(List.of(items), items.length, null, null);
    }

    private int count(String body, String text) {
        return body.split(Pattern.quote(text), -1).length - 1;
    }

    // ---------- 表示 ----------

    @Test
    void home_showsBothBoxes_withRowsDateCategorySenderAndDetailLink() {
        stub("mine", listOf(item(1, "年末調整の書類提出のお願い", false)));
        stub("everyone", listOf(item(2, "全社会議のお知らせ", false), item(3, "避難訓練について", false)));

        String body = openHome();

        assertThat(body).contains("ハロワお知らせ", "あなたへのお知らせ", "全員向け");
        assertThat(body).contains("年末調整の書類提出のお願い", "全社会議のお知らせ", "避難訓練について");
        assertThat(body).contains("2026/10/22 10:30"); //日時は、読みやすい形で出る
        assertThat(body).contains("対応依頼", "総務部"); //種類と、差出人(部署)
        assertThat(body).contains("/announcements/1").contains("/announcements/2").contains("/announcements/3");
    }

    @Test
    void home_linksToList_withEachScope() {
        stub("mine", listOf(item(1, "あ", false)));
        stub("everyone", listOf(item(2, "い", false)));

        String body = openHome();

        assertThat(count(body, "すべて表示")).isEqualTo(2);
        assertThat(body).contains("/announcements?scope=mine").contains("/announcements?scope=everyone");
    }

    @Test
    void home_showsImportantBadge_onlyOnImportantRows() {
        stub("mine", listOf(item(1, "重要なお知らせ", true), item(2, "ふつうのお知らせ", false)));
        stub("everyone", listOf());

        String body = openHome();

        assertThat(count(body, "badge badge-danger\">重要</span>")).isEqualTo(1);
    }

    @Test
    void home_escapesTitle_soScriptIsShownAsText() {
        stub("mine", listOf(item(1, "<script>alert(1)</script>", false)));
        stub("everyone", listOf());

        String body = openHome();

        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    // ---------- 0件・失敗 ----------

    @Test
    void home_whenNoAnnouncements_showsEmptyMessage_inBothBoxes() {
        stub("mine", listOf());
        stub("everyone", listOf());

        String body = openHome();

        assertThat(count(body, "お知らせは、ありません")).isEqualTo(2);
        assertThat(body).doesNotContain("<th>タイトル</th>"); //表は出さない
    }

    @Test
    void home_whenOneBoxFails_showsMessageOnlyInThatBox_andOtherBoxStillShows() {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("mine"), eq(5)))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));
        stub("everyone", listOf(item(2, "全社会議のお知らせ", false)));

        String body = openHome();

        assertThat(count(body, "お知らせを取得できませんでした")).isEqualTo(1);
        assertThat(body).contains("全社会議のお知らせ"); //もう片方の欄は、出る
        assertThat(body).contains("山田太郎さん"); //ホームの他の部分(あいさつ)も、出る
    }

    @Test
    void home_showsGreetingInHeader_nextToTitle_andNoGreetingCard() {
        stub("mine", listOf());
        stub("everyone", listOf());

        String body = openHome();

        //ヘッダーの「ホーム」の横に、あいさつが出る(中身には、あいさつのカードは出さない)
        assertThat(body).containsPattern("<h1>ホーム</h1>\\s*<span class=\"topbar-sub\">[^<]*、山田太郎さん</span>");
        assertThat(body).doesNotContain("greeting-card");
    }

    @Test
    void home_showsNoQuickAccess() {
        stub("mine", listOf());
        stub("everyone", listOf());

        String body = openHome();

        //クイックアクセスは、なくした(同じリンクは、左のメニューにある)
        assertThat(body).doesNotContain("クイックアクセス").doesNotContain("quick-card");
    }
}

package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.example.pontalweb.client.AnnouncementDetail;
import com.example.pontalweb.client.AnnouncementListResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//お知らせ詳細の画面と、削除の流れを、実際に起動したアプリ(Tomcat + JSP)で確認する結合テスト
//(JSPのEL式、ボタンの出し分け、CSRFトークンの入ったフォーム、POST → redirect → 一覧のメッセージ、は、MockMvcでは確認しきれない)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AnnouncementDetailPageIntegrationTest {

    private static final String DETAIL_URI = "/api/announcements/{id}";
    private static final String LIST_URI = "/api/announcements?scope={scope}&page={page}&size={size}";

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

    // ---------- ログイン(疑似のCognitoで成功させる)。ほかの結合テストと同じ ----------

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

    private LoginUser member() {
        return new LoginUser(3L, "鈴木一郎", false, false);
    }

    private LoginUser systemAdmin() {
        return new LoginUser(1L, "山田太郎", true, false);
    }

    private AnnouncementDetail detail(boolean important, String body, boolean canEdit, boolean canDelete) {
        return new AnnouncementDetail(7L, "年末調整の書類提出のお願い", "対応依頼", important, "総務部", "DEPARTMENT",
                "開発部", "2026-10-22T10:30:00", body, "2026-10-22T10:30:00", canEdit, canDelete, "開発部", null);
    }

    private void stubDetail(AnnouncementDetail detail) {
        when(api.get(eq(DETAIL_URI), eq(AnnouncementDetail.class), eq(7L))).thenReturn(detail);
    }

    // ---------- 表示 ----------

    @Test
    void detail_showsBadges_title_senderToTarget_date_andBody() {
        stubDetail(detail(true, "本文です", false, false));

        String body = open(member(), "/announcements/7");

        assertThat(body).contains("badge badge-danger\">重要</span>");
        assertThat(body).contains("対応依頼");
        assertThat(body).contains("年末調整の書類提出のお願い");
        assertThat(body).containsPattern("総務部\\s*→\\s*開発部へ"); //差出人は部署だけ。個人名は出ない
        assertThat(body).contains("2026/10/22 10:30");
        assertThat(body).contains("本文です");
        assertThat(body).contains("← 一覧へ戻る");
    }

    @Test
    void detail_hidesImportantBadge_whenNotImportant() {
        stubDetail(detail(false, "本文", false, false));

        assertThat(open(member(), "/announcements/7")).doesNotContain("badge badge-danger");
    }

    @Test
    void detail_keepsLineBreaksOfBody_andEscapesIt() {
        stubDetail(detail(false, "1行目\n2行目 <script>alert(1)</script>", false, false));

        String body = open(member(), "/announcements/7");

        assertThat(body).contains("1行目\n2行目"); //改行は、そのまま出る(表示は、CSSの pre-wrap)
        assertThat(body).contains("announce-body");
        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void detail_highlightsHomeInMenu() {
        stubDetail(detail(false, "本文", false, false));

        assertThat(open(member(), "/announcements/7"))
                .containsPattern("class=\"nav-item active\"[^>]*>\\s*(<[^>]+>\\s*)*(<[^>]+>[^<]*</[^>]+>\\s*)*ホーム");
    }

    // ---------- ボタンの出し分け(APIが返す canEdit / canDelete で決まる) ----------

    @Test
    void detail_withoutPermissions_showsNoEditOrDeleteButton() {
        stubDetail(detail(false, "本文", false, false));

        String body = open(member(), "/announcements/7");

        assertThat(body).doesNotContain("/announcements/7/edit");
        assertThat(body).doesNotContain("/announcements/7/delete");
    }

    @Test
    void detail_whenCanEditOnly_showsEditButton_butNotDelete() {
        //HR管理者: 編集はできるが、削除はできない
        stubDetail(detail(false, "本文", true, false));

        String body = open(member(), "/announcements/7");

        assertThat(body).contains("/announcements/7/edit");
        assertThat(body).doesNotContain("/announcements/7/delete");
    }

    @Test
    void detail_whenCanEditAndDelete_showsBoth_withCsrfTokenInDeleteForm() {
        stubDetail(detail(false, "本文", true, true));

        String body = open(systemAdmin(), "/announcements/7");

        assertThat(body).contains("/announcements/7/edit");
        assertThat(body).contains("/announcements/7/delete");
        assertThat(body).containsPattern("method=\"post\"");
        assertThat(body).containsPattern("name=\"_csrf\" value=\"[^\"]+\""); //CSRFトークンが、フォームに入っている
        assertThat(body).contains("confirm("); //確認のダイアログ
    }

    // ---------- 削除の流れ ----------

    @Test
    void delete_withCsrfToken_redirectsToList_andShowsMessageOnce() {
        stubDetail(detail(false, "本文", true, true));
        when(api.get(eq(LIST_URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenReturn(new AnnouncementListResponse(List.of(), 0, null, null));
        String cookie = loginAs(systemAdmin());

        //詳細の画面から、フォームのCSRFトークンを取り出して、削除を送る(ブラウザが、ボタンを押したときと同じ)
        String detailBody = get(cookie, "/announcements/7").getBody();
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(detailBody);
        assertThat(csrf.find()).isTrue();
        ResponseEntity<String> deleted = post(cookie, "/announcements/7/delete", csrf.group(1));

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(deleted.getHeaders().getLocation().getPath()).isEqualTo("/announcements");
        verify(api).delete(eq("/api/announcements/{id}"), eq(7L));

        //redirect先の一覧に、「削除しました」が出る。もう一度開くと、消えている(1回だけ)
        assertThat(get(cookie, "/announcements").getBody()).contains("削除しました");
        assertThat(get(cookie, "/announcements").getBody()).doesNotContain("削除しました");
    }

    @Test
    void delete_withoutCsrfToken_isRejected_andDoesNotCallApi() {
        String cookie = loginAs(systemAdmin());

        ResponseEntity<String> response = post(cookie, "/announcements/7/delete", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(api, never()).delete(any(String.class), any(Object[].class));
    }

    private ResponseEntity<String> post(String cookie, String path, String csrfToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (csrfToken != null) {
            form.add("_csrf", csrfToken);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.postForEntity(path, new HttpEntity<>(form, headers), String.class);
    }
}

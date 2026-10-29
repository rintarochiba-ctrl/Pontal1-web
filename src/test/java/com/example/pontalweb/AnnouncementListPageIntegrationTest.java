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
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.PontalPrincipal;

//お知らせ一覧の画面を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
//(JSPのEL式が値を取れているか、checked / selected が正しく付くか、権限で出し分けられるか、は、MockMvcでは確認できない)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AnnouncementListPageIntegrationTest {

    private static final String URI = "/api/announcements?scope={scope}&page={page}&size={size}";
    private static final String URI_WITH_CATEGORY = URI + "&category={category}";

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

    // ---------- ログイン(疑似のCognitoで成功させる)。HomePageIntegrationTestと同じ ----------

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

    private String open(LoginUser user, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, loginAs(user));
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private LoginUser member() {
        return new LoginUser(3L, "鈴木一郎", false, false);
    }

    private LoginUser systemAdmin() {
        return new LoginUser(1L, "山田太郎", true, false);
    }

    private LoginUser hrAdmin() {
        return new LoginUser(2L, "佐藤花子", false, true);
    }

    private AnnouncementItem item(long id, String title, boolean important) {
        return new AnnouncementItem(id, title, "対応依頼", important, "総務部", "DEPARTMENT", "開発部",
                "2026-10-22T10:30:00");
    }

    private void stub(String scope, int page, AnnouncementListResponse response) {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq(scope), eq(page), eq(10))).thenReturn(response);
    }

    private AnnouncementListResponse list(int totalCount, Integer next, Integer prev, AnnouncementItem... items) {
        return new AnnouncementListResponse(List.of(items), totalCount, next, prev);
    }

    private int count(String body, String text) {
        return body.split(Pattern.quote(text), -1).length - 1;
    }

    // ---------- 表 ----------

    @Test
    void list_showsRows_withDateCategoryTargetSenderTitleAndDetailLink() {
        stub("all", 0, list(2, null, null, item(1, "年末調整のお願い", false), item(2, "避難訓練について", false)));

        String body = open(member(), "/announcements");

        assertThat(body).contains("年末調整のお願い", "避難訓練について");
        assertThat(body).contains("2026/10/22 10:30"); //日時
        assertThat(body).contains("対応依頼"); //種類
        assertThat(body).contains("開発部"); //宛先(targetLabel)
        assertThat(body).contains("総務部"); //差出人
        assertThat(body).contains("/announcements/1").contains("/announcements/2"); //詳細へのリンク
        assertThat(body).contains("全 2 件");
    }

    @Test
    void list_showsImportantBadge_onlyOnImportantRows() {
        stub("all", 0, list(2, null, null, item(1, "重要", true), item(2, "ふつう", false)));

        String body = open(member(), "/announcements");

        assertThat(count(body, "badge badge-danger\">重要</span>")).isEqualTo(1);
    }

    @Test
    void list_escapesTitle() {
        stub("all", 0, list(1, null, null, item(1, "<script>alert(1)</script>", false)));

        String body = open(member(), "/announcements");

        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void list_whenNoAnnouncements_showsMessage_withoutTable() {
        stub("all", 0, list(0, null, null));

        String body = open(member(), "/announcements");

        assertThat(body).contains("該当するお知らせは、ありません");
        assertThat(body).doesNotContain("<th>タイトル</th>");
        assertThat(body).contains("全 0 件");
    }

    // ---------- 表示の切り替え・種類(checked / selected) ----------

    @Test
    void list_withoutParams_checksAll_andSelectsNoCategory() {
        stub("all", 0, list(0, null, null));

        String body = open(member(), "/announcements");

        assertThat(body).containsPattern("value=\"all\"\\s+checked");
        assertThat(body).doesNotContainPattern("value=\"mine\"\\s+checked");
        assertThat(body).doesNotContainPattern("value=\"everyone\"\\s+checked");
        assertThat(body).doesNotContain("selected");
    }

    @Test
    void list_withScopeMine_checksOnlyMine() {
        stub("mine", 0, list(0, null, null));

        String body = open(member(), "/announcements?scope=mine");

        assertThat(body).containsPattern("value=\"mine\"\\s+checked");
        assertThat(body).doesNotContainPattern("value=\"all\"\\s+checked");
        assertThat(body).doesNotContainPattern("value=\"everyone\"\\s+checked");
    }

    @Test
    void list_withScopeEveryone_checksOnlyEveryone() {
        stub("everyone", 0, list(0, null, null));

        String body = open(member(), "/announcements?scope=everyone");

        assertThat(body).containsPattern("value=\"everyone\"\\s+checked");
        assertThat(body).doesNotContainPattern("value=\"all\"\\s+checked");
    }

    @Test
    void list_withCategory_selectsThatOptionOnly() {
        when(api.get(eq(URI_WITH_CATEGORY), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10), eq("社内情報")))
                .thenReturn(list(0, null, null));

        String body = open(member(), "/announcements?category=社内情報");

        assertThat(body).containsPattern("value=\"社内情報\"\\s+selected");
        assertThat(body).doesNotContainPattern("value=\"技術\"\\s+selected");
        assertThat(body).doesNotContainPattern("value=\"対応依頼\"\\s+selected");
    }

    // ---------- 権限による出し分け ----------

    @Test
    void member_doesNotSeeCreateButton_orManageRadio() {
        stub("all", 0, list(0, null, null));

        String body = open(member(), "/announcements");

        assertThat(body).doesNotContain("/announcements/new");
        assertThat(body).doesNotContain("value=\"manage\"");
    }

    @Test
    void systemAdmin_seesCreateButton_andManageRadio() {
        stub("all", 0, list(0, null, null));

        String body = open(systemAdmin(), "/announcements");

        assertThat(body).contains("/announcements/new");
        assertThat(body).contains("value=\"manage\"").contains("すべて(管理者)");
    }

    @Test
    void hrAdmin_seesCreateButton_andManageRadio() {
        stub("all", 0, list(0, null, null));

        String body = open(hrAdmin(), "/announcements");

        assertThat(body).contains("/announcements/new");
        assertThat(body).contains("value=\"manage\"");
    }

    @Test
    void admin_withScopeManage_checksManage() {
        stub("manage", 0, list(0, null, null));

        String body = open(systemAdmin(), "/announcements?scope=manage");

        assertThat(body).containsPattern("value=\"manage\"\\s+checked");
    }

    // ---------- メニュー・戻るリンク ----------

    @Test
    void list_highlightsHomeInMenu_andHasBackToHomeLink() {
        stub("all", 0, list(0, null, null));

        String body = open(member(), "/announcements");

        assertThat(body).containsPattern("class=\"nav-item active\"[^>]*>\\s*(<[^>]+>\\s*)*(<[^>]+>[^<]*</[^>]+>\\s*)*ホーム");
        assertThat(body).contains("← ホームへ戻る");
    }

    // ---------- ページ送り ----------

    @Test
    void list_whenOnePage_hidesPagination() {
        stub("all", 0, list(10, null, null, item(1, "A", false)));

        assertThat(open(member(), "/announcements")).doesNotContain("class=\"pagination\"");
    }

    @Test
    void list_onFirstOfThreePages_showsNumbers_andDisablesPrev() {
        stub("all", 0, list(25, 1, null, item(1, "A", false)));

        String body = open(member(), "/announcements");

        assertThat(body).contains("class=\"pagination\"");
        assertThat(body).contains("<span class=\"current\">1</span>");
        assertThat(body).contains("<span class=\"disabled\">‹</span>");
        assertThat(body).contains("page=1").contains("page=2"); //2ページ目・3ページ目へのリンク(URLでは0始まり)
        assertThat(body).doesNotContain("<span class=\"disabled\">›</span>");
    }

    @Test
    void list_onLastPage_disablesNext_andMarksItCurrent() {
        stub("all", 2, list(25, null, 1, item(21, "A", false)));

        String body = open(member(), "/announcements?page=2");

        assertThat(body).contains("<span class=\"current\">3</span>");
        assertThat(body).contains("<span class=\"disabled\">›</span>");
    }

    @Test
    void list_withManyPages_showsEllipsis() {
        stub("all", 4, list(100, 5, 3, item(41, "A", false)));

        String body = open(member(), "/announcements?page=4");

        assertThat(count(body, "class=\"ellipsis\">…</span>")).isEqualTo(2); // 1 … 4 5 6 … 10
        assertThat(body).contains("<span class=\"current\">5</span>");
    }

    @Test
    void list_paginationLinks_keepScopeAndCategory() {
        when(api.get(eq(URI_WITH_CATEGORY), eq(AnnouncementListResponse.class), eq("mine"), eq(0), eq(10), eq("技術")))
                .thenReturn(list(25, 1, null, item(1, "A", false)));

        String body = open(member(), "/announcements?scope=mine&category=技術");

        //次のページへのリンクにも、表示の切り替えと種類が付く(ページを送っても、絞り込みが外れない)
        assertThat(body).containsPattern("href=\"[^\"]*page=1[^\"]*scope=mine[^\"]*\"");
        //「技術」は、URLの中では「%e6%8a%80%e8%a1%93」(UTF-8のバイト列)になる。16進数の大文字・小文字は、区別しない
        assertThat(body).containsPattern("(?i)href=\"[^\"]*category=%E6%8A%80%E8%A1%93[^\"]*\"");
    }
}

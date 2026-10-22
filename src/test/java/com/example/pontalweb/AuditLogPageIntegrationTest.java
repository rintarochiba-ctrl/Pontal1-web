package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

import com.example.pontalweb.client.AuditLogBatch;
import com.example.pontalweb.model.LoginUser;

//監査ログの画面を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
//(JSPのEL式が、入れ物の値を取れているか、状態ごとの出し分けが合っているか、は、MockMvcでは確認できない)
//本物のAPIには、週ごとのバッチが、少ししかないので、疑似のAPIで、すべての状態(完了・処理中・失敗・0件・空)を確認する
class AuditLogPageIntegrationTest extends LoggedInPageTestBase {

    private static final String URI = "/api/audit-log-batches?year={year}&month={month}";

    private String systemAdminCookie;

    @BeforeEach
    void setUp() {
        systemAdminCookie = loginAs(new LoginUser(1L, "管理者", true, false), "ROLE_USER", "ROLE_SYSTEM_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private void apiReturns(int year, int month, AuditLogBatch... batches) {
        when(api.get(eq(URI), any(ParameterizedTypeReference.class), eq(year), eq(month))).thenReturn(List.of(batches));
    }

    private AuditLogBatch batch(long id, String start, String end, String status, Integer count) {
        return new AuditLogBatch(id, start, end, status, count);
    }

    // ---------- 週ごとのCSV: 状態ごとの表示 ----------

    @Test
    void exports_showsEachStatusWithItsBadge_count_andCsvButton() {
        apiReturns(2026, 10,
                batch(12, "2026-10-05", "2026-10-11", "COMPLETED", 100),
                batch(13, "2026-10-12", "2026-10-18", "PROCESSING", null),
                batch(14, "2026-10-19", "2026-10-25", "FAILED", null));

        String body = openAs(systemAdminCookie, "/audit-logs/exports?year=2026&month=10");

        //期間と、件数
        assertThat(body).contains("2026/10/05 〜 2026/10/11", "2026/10/12 〜 2026/10/18", "2026/10/19 〜 2026/10/25");
        assertThat(body).contains("100件");
        //状態ごとのバッジ(完了は緑、処理中は黄、失敗は赤)
        assertThat(body).contains("<span class=\"badge badge-success\">完了</span>");
        assertThat(body).contains("<span class=\"badge badge-warning\">処理中</span>");
        assertThat(body).contains("<span class=\"badge badge-danger\">失敗</span>");
        //完了のときだけ、CSVのボタンが出る(3行のうち、1つだけ)
        assertThat(count(body, "class=\"btn btn-sm\"")).isEqualTo(1);
    }

    @Test
    void exports_showsDashForCountWhenProcessing_butZeroWhenCompletedWithNoRecords() {
        apiReturns(2026, 10,
                batch(12, "2026-10-05", "2026-10-11", "COMPLETED", 0),
                batch(13, "2026-10-12", "2026-10-18", "PROCESSING", null));

        String body = openAs(systemAdminCookie, "/audit-logs/exports?year=2026&month=10");

        assertThat(body).contains("0件"); //記録が、ほんとうに0件の週は、「0件」と出る
        //処理中の、件数と操作の列が、「-」(<c:choose> の改行や空白が入るので、空白を許す形で数える)
        assertThat(java.util.regex.Pattern.compile("class=\"col-center\">\\s*-\\s*</td>").matcher(body).results().count())
                .isEqualTo(2);
    }

    @Test
    void exports_whenNoBatches_showsMessage_withoutTable() {
        apiReturns(2026, 9);

        String body = openAs(systemAdminCookie, "/audit-logs/exports?year=2026&month=9");

        assertThat(body).contains("この月の週ごとのCSVは、まだありません");
        assertThat(body).doesNotContain("<table");
    }

    // ---------- 週ごとのCSV: 年月の選択 ----------

    @Test
    void exports_showsYearAndMonthSelects_withCurrentSelectionMarked() {
        int thisYear = LocalDate.now().getYear();
        apiReturns(thisYear - 1, 3);

        String body = openAs(systemAdminCookie, "/audit-logs/exports?year=" + (thisYear - 1) + "&month=3");

        //年の候補は、今年から3年分。選んだ年と月に、selected が付く
        assertThat(body).contains("<option value=\"" + thisYear + "\" >" + thisYear + "</option>");
        assertThat(body).contains("<option value=\"" + (thisYear - 2) + "\" >" + (thisYear - 2) + "</option>");
        assertThat(body).contains("<option value=\"" + (thisYear - 1) + "\" selected>" + (thisYear - 1) + "</option>");
        assertThat(body).contains("<option value=\"3\" selected>3</option>");
        assertThat(count(body, "<option value=\"")).isEqualTo(3 + 12); //年が3つ、月が12個
    }

    @Test
    void exports_withoutParameters_marksThisYearAndThisMonthAsSelected() {
        LocalDate today = LocalDate.now();
        apiReturns(today.getYear(), today.getMonthValue());

        String body = openAs(systemAdminCookie, "/audit-logs/exports");

        assertThat(body).contains("<option value=\"" + today.getYear() + "\" selected>");
        assertThat(body).contains("<option value=\"" + today.getMonthValue() + "\" selected>");
    }

    // ---------- タブ ----------

    @Test
    void exports_marksCsvTabAsActive() {
        apiReturns(2026, 10);

        String body = openAs(systemAdminCookie, "/audit-logs/exports?year=2026&month=10");

        assertThat(body).containsPattern("class=\"tab active\" href=\"/audit-logs/exports\"");
        assertThat(body).containsPattern("class=\"tab\" href=\"/audit-logs\"");
    }

    @Test
    void history_showsPreparingMessage_andMarksHistoryTabAsActive() {
        String body = openAs(systemAdminCookie, "/audit-logs");

        assertThat(body).contains("準備中です");
        assertThat(body).containsPattern("class=\"tab active\" href=\"/audit-logs\"");
        assertThat(body).containsPattern("class=\"tab\" href=\"/audit-logs/exports\"");
    }

    @Test
    void menu_highlightsAuditLogItem_forSystemAdmin() {
        apiReturns(2026, 10);

        String body = openAs(systemAdminCookie, "/audit-logs/exports?year=2026&month=10");

        assertThat(body).containsPattern("nav-item active\"\\s+href=\"/audit-logs\"");
    }

    // ---------- 権限 ----------

    @Test
    void generalUser_cannotOpenAuditLogPages() {
        String memberCookie = loginAs(new LoginUser(7L, "山田太郎", false, false), "ROLE_USER");

        assertThat(get(memberCookie, "/audit-logs").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(memberCookie, "/audit-logs/exports").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void hrAdmin_cannotOpenAuditLogPages() {
        String hrCookie = loginAs(new LoginUser(2L, "人事担当", false, true), "ROLE_USER", "ROLE_HR_ADMIN");

        assertThat(get(hrCookie, "/audit-logs").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(hrCookie, "/audit-logs/exports").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private int count(String body, String text) {
        return body.split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }
}

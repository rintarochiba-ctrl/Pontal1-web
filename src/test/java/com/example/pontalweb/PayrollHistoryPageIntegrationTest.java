package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

import com.example.pontalweb.client.EmployeeOptionPage;
import com.example.pontalweb.client.SalarySlipUploadHistory;
import com.example.pontalweb.model.LoginUser;

//給与明細のアップロード履歴を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
class PayrollHistoryPageIntegrationTest extends LoggedInPageTestBase {

    private static final String HISTORY_URI = "/api/salary-slips/history?page={page}&size={size}";

    private String hrAdminCookie;

    @BeforeEach
    void setUp() {
        when(api.get(eq("/api/employees?page={page}&size={size}"), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(
                        new EmployeeOptionPage.Option(3L, "山田太郎", "開発部"),
                        new EmployeeOptionPage.Option(1L, "人事担当", null)), null));
        hrAdminCookie = loginAs(new LoginUser(1L, "人事担当", false, true), "ROLE_USER", "ROLE_HR_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private void historyOf(int page, SalarySlipUploadHistory... items) {
        when(api.get(eq(HISTORY_URI), any(ParameterizedTypeReference.class), eq(page), eq(20))).thenReturn(List.of(items));
    }

    @Test
    void history_showsRowsWithNamesAndLinkToThatEmployeesList() {
        historyOf(0, new SalarySlipUploadHistory(30L, 3L, "2024-09", 1L, "2026-10-21T12:34:56"));

        String body = openAs(hrAdminCookie, "/payroll/history");

        assertThat(body).contains("2026/10/21 12:34", "山田太郎", "2024年09月支給分", "人事担当");
        assertThat(body).contains("/payroll?employeeId=3"); //名前から、その人の一覧へ
    }

    @Test
    void history_whenEmpty_showsMessage_withoutTable() {
        historyOf(0);

        String body = openAs(hrAdminCookie, "/payroll/history");

        assertThat(body).contains("アップロードの履歴は、まだありません");
        assertThat(body).doesNotContain("<table");
    }

    @Test
    void history_showsPaginationLinks_onlyWhenThereAreOtherPages() {
        SalarySlipUploadHistory[] fullPage = new SalarySlipUploadHistory[20];
        java.util.Arrays.fill(fullPage, new SalarySlipUploadHistory(1L, 3L, "2024-09", 1L, "2026-10-21T12:34:56"));
        historyOf(0, fullPage);
        historyOf(1, new SalarySlipUploadHistory(2L, 3L, "2024-08", 1L, "2026-10-20T09:00:00"));

        String body = openAs(hrAdminCookie, "/payroll/history");

        assertThat(body).contains("class=\"pagination\"", "次へ ›", "page=1");
        assertThat(body).doesNotContain("‹ 前へ"); //1ページ目に、「前へ」は無い
    }

    @Test
    void history_unknownEmployee_showsIdInsteadOfName() {
        historyOf(0, new SalarySlipUploadHistory(30L, 99L, "2024-09", null, "2026-10-21T12:34:56"));

        String body = openAs(hrAdminCookie, "/payroll/history");

        assertThat(body).contains("不明な社員(ID: 99)");
    }

    @Test
    void history_escapesHtmlInNames() {
        when(api.get(eq("/api/employees?page={page}&size={size}"), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(
                        new EmployeeOptionPage.Option(3L, "<script>alert(1)</script>", null)), null));
        historyOf(0, new SalarySlipUploadHistory(30L, 3L, "2024-09", 3L, "2026-10-21T12:34:56"));

        String body = openAs(hrAdminCookie, "/payroll/history");

        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void generalUser_cannotOpenHistory() {
        String memberCookie = loginAs(new LoginUser(7L, "山田太郎", false, false), "ROLE_USER");

        assertThat(get(memberCookie, "/payroll/history").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

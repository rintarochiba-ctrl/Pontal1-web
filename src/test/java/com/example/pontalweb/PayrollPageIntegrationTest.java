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
import com.example.pontalweb.client.SalarySlipSummary;
import com.example.pontalweb.model.LoginUser;

//給与管理の画面を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
class PayrollPageIntegrationTest extends LoggedInPageTestBase {

    private static final String EMPLOYEES_URI = "/api/employees?page={page}&size={size}";
    private static final String SLIPS_URI = "/api/employees/{id}/salary-slips";

    private String hrAdminCookie;

    @BeforeEach
    void setUpEmployees() {
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(
                        new EmployeeOptionPage.Option(3L, "山田太郎", "開発部"),
                        new EmployeeOptionPage.Option(4L, "佐藤花子", null)), null));
        hrAdminCookie = loginAs(new LoginUser(1L, "人事担当", false, true), "ROLE_USER", "ROLE_HR_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private void employee3HasSlips(SalarySlipSummary... slips) {
        when(api.get(eq(SLIPS_URI), any(ParameterizedTypeReference.class), eq(3L))).thenReturn(List.of(slips));
    }

    @Test
    void whenNoEmployeeSelected_showsSelectBoxAndGuidance_withoutTable() {
        String body = openAs(hrAdminCookie, "/payroll");

        assertThat(body).contains("山田太郎(開発部)", "佐藤花子"); //部署が無い人は、名前だけ
        assertThat(body).contains("社員を選択してください");
        assertThat(body).doesNotContain("<table");
    }

    @Test
    void whenEmployeeSelected_showsSlipsAndKeepsSelection() {
        employee3HasSlips(new SalarySlipSummary(30L, "2024-09"), new SalarySlipSummary(20L, "2023-12"));

        String body = openAs(hrAdminCookie, "/payroll?employeeId=3");

        assertThat(body).containsPattern("<option value=\"3\" selected>山田太郎\\(開発部\\)</option>"); //選んだ社員が、選ばれたまま
        assertThat(body).contains("2024年09月支給分", "2023年12月支給分");
        assertThat(body).contains("/salary-slips/30/download?payMonth=2024-09"); //他人の明細も、同じダウンロードを使う
        assertThat(body).contains("name=\"employeeId\" value=\"3\""); //絞り込みの欄に、社員を引き継ぐ
        //年の候補は、その人の明細にある年だけ(selected が付かないときは、">" の前に空白が入る)
        assertThat(body).containsPattern("<option value=\"2024\"\\s*>2024年</option>");
        assertThat(body).containsPattern("<option value=\"2023\"\\s*>2023年</option>");
        assertThat(body).doesNotContain("2022年");
    }

    @Test
    void whenFilteredByYearAndMonth_showsOnlyMatching_andKeepsFilterSelection() {
        employee3HasSlips(new SalarySlipSummary(30L, "2024-09"), new SalarySlipSummary(29L, "2024-08"),
                new SalarySlipSummary(20L, "2023-09"));

        String body = openAs(hrAdminCookie, "/payroll?employeeId=3&year=2024&month=09");

        assertThat(body).contains("2024年09月支給分");
        assertThat(body).doesNotContain("2024年08月支給分").doesNotContain("2023年09月支給分");
        assertThat(body).contains("<option value=\"2024\" selected>2024年</option>");
        assertThat(body).contains("<option value=\"09\" selected>9月</option>");
    }

    @Test
    void whenNothingMatchesFilter_showsNoMatchMessage() {
        employee3HasSlips(new SalarySlipSummary(30L, "2024-09"));

        String body = openAs(hrAdminCookie, "/payroll?employeeId=3&year=2024&month=01");

        assertThat(body).contains("該当する給与明細は、ありません");
        assertThat(body).doesNotContain("<table");
    }

    @Test
    void whenEmployeeHasNoSlips_showsMessage_withoutFilterForm() {
        employee3HasSlips();

        String body = openAs(hrAdminCookie, "/payroll?employeeId=3");

        assertThat(body).contains("この社員の給与明細は、まだありません");
        assertThat(body).doesNotContain("id=\"year\""); //絞り込みの欄は、出さない
    }

    @Test
    void highlightsPayrollMenu() {
        String body = openAs(hrAdminCookie, "/payroll");

        assertThat(body).containsPattern("nav-item active\"\\s+href=\"/payroll\"");
    }

    @Test
    void escapesHtmlInEmployeeNames() {
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(
                        new EmployeeOptionPage.Option(3L, "<script>alert(1)</script>", "開発部")), null));

        String body = openAs(hrAdminCookie, "/payroll");

        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void generalUser_cannotOpenPayroll() {
        String memberCookie = loginAs(new LoginUser(7L, "山田太郎", false, false), "ROLE_USER");

        assertThat(get(memberCookie, "/payroll").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

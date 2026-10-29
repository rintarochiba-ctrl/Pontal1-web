package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.example.pontalweb.client.SalarySlipSummary;
import com.example.pontalweb.model.LoginUser;

//給与明細の一覧を、実際に起動したアプリ(Tomcat + JSP)で表示して、HTMLの中身を確認する結合テスト
class SalarySlipPageIntegrationTest extends LoggedInPageTestBase {

    private static final String LIST_URI = "/api/employees/{id}/salary-slips";

    //一般ユーザー(社員ID=7)として、ログインして、給与明細の画面を開く
    private String openSalarySlips() {
        String cookie = loginAs(new LoginUser(7L, "山田太郎", false, false), "ROLE_USER");
        return openAs(cookie, "/salary-slips");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_showsPayMonthsWithPdfLinks_inGivenOrder() {
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L)))
                .thenReturn(List.of(new SalarySlipSummary(3L, "2024-09"), new SalarySlipSummary(2L, "2024-08")));

        String body = openSalarySlips();

        assertThat(body).contains("2024年09月支給分", "2024年08月支給分");
        assertThat(body.indexOf("2024年09月支給分")).isLessThan(body.indexOf("2024年08月支給分")); //新しい順のまま
        assertThat(body).contains("/salary-slips/3/download?payMonth=2024-09");
        assertThat(body).contains("/salary-slips/2/download?payMonth=2024-08");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_whenNoSlips_showsMessage_withoutTable() {
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L))).thenReturn(List.of());

        String body = openSalarySlips();

        assertThat(body).contains("給与明細は、まだありません");
        assertThat(body).doesNotContain("<table");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_highlightsSalaryMenu() {
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L))).thenReturn(List.of());

        String body = openSalarySlips();

        assertThat(body).containsPattern("nav-item active\"\\s+href=\"/salary-slips\"");
    }
}

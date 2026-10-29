package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.EmployeeOptionPage;
import com.example.pontalweb.client.EmployeeOptionPage.Option;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipSummary;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;

//PayrollController(給与管理)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「社員を全員分取ること」「選ばれた社員の明細を取ること」「年月の絞り込み」「エラーの画面」を確認する
@WebMvcTest(PayrollController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(権限は、SecurityConfigTestと、結合テストで確認)
class PayrollControllerTest {

    private static final String EMPLOYEES_URI = "/api/employees?page={page}&size={size}";
    private static final String SLIPS_URI = "/api/employees/{id}/salary-slips";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private SalarySlipSummary slip(long id, String payMonth) {
        return new SalarySlipSummary(id, payMonth);
    }

    private void employeesOnePage() {
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(new Option(3L, "山田太郎", "開発部")), null));
    }

    @SuppressWarnings("unchecked")
    private void slipsOf(long employeeId, SalarySlipSummary... slips) {
        when(api.get(eq(SLIPS_URI), any(ParameterizedTypeReference.class), eq(employeeId))).thenReturn(List.of(slips));
    }

    // ---------- 社員の選択 ----------

    @Test
    @SuppressWarnings("unchecked")
    void withoutEmployee_loadsEmployees_butDoesNotCallSalarySlipApi() throws Exception {
        employeesOnePage();

        mockMvc.perform(get("/payroll"))
                .andExpect(status().isOk())
                .andExpect(view().name("payroll"))
                .andExpect(model().attribute("options", List.of(new Option(3L, "山田太郎", "開発部"))))
                .andExpect(model().attributeDoesNotExist("slips"));

        verify(api, never()).get(eq(SLIPS_URI), any(ParameterizedTypeReference.class), any());
    }

    @Test
    void loadsEveryPageOfEmployees_followingNext() throws Exception {
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(new Option(1L, "A", null)), 1));
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(1), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(new Option(2L, "B", null)), null));

        mockMvc.perform(get("/payroll"))
                .andExpect(model().attribute("options", List.of(new Option(1L, "A", null), new Option(2L, "B", null))));
    }

    @Test
    void stopsFollowingPages_afterUpperLimit() throws Exception {
        //nextが、いつまでも空にならなくても、無限に繰り返さない
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), any(Integer.class), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(new Option(1L, "A", null)), 1));

        mockMvc.perform(get("/payroll")).andExpect(status().isOk());

        verify(api, times(20)).get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), any(Integer.class), eq(100));
    }

    // ---------- 一覧と絞り込み ----------

    @Test
    void withEmployee_callsApiForThatEmployee_andShowsAllSlips() throws Exception {
        employeesOnePage();
        slipsOf(3, slip(30, "2024-09"), slip(20, "2023-12"));

        mockMvc.perform(get("/payroll").param("employeeId", "3"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("employeeId", 3L))
                .andExpect(model().attribute("hasSlips", true))
                .andExpect(model().attribute("slips", List.of(slip(30, "2024-09"), slip(20, "2023-12"))))
                .andExpect(model().attribute("years", List.of("2024", "2023")));
    }

    @Test
    void filtersByYearAndMonth() throws Exception {
        employeesOnePage();
        slipsOf(3, slip(30, "2024-09"), slip(29, "2024-08"), slip(20, "2023-09"));

        mockMvc.perform(get("/payroll").param("employeeId", "3").param("year", "2024").param("month", "09"))
                .andExpect(model().attribute("slips", List.of(slip(30, "2024-09"))))
                //候補の年は、絞り込む前の全明細から作る(絞り込んでも、他の年を選び直せる)
                .andExpect(model().attribute("years", List.of("2024", "2023")))
                .andExpect(model().attribute("year", "2024"))
                .andExpect(model().attribute("month", "09"));
    }

    @Test
    void whenNoSlipsAtAll_hasSlipsIsFalse() throws Exception {
        employeesOnePage();
        slipsOf(3);

        mockMvc.perform(get("/payroll").param("employeeId", "3"))
                .andExpect(model().attribute("hasSlips", false))
                .andExpect(model().attribute("slips", List.of()));
    }

    // ---------- 絞り込みの部品(Controllerの中の、static メソッド) ----------

    @Test
    void filter_ignoresEmptyOrMalformedConditions() {
        List<SalarySlipSummary> slips = List.of(slip(2, "2024-09"), slip(1, "2023-01"));

        assertThat(PayrollController.filter(slips, "", "")).isEqualTo(slips);
        assertThat(PayrollController.filter(slips, "abc", "13")).isEqualTo(slips); //形が違う値は、無視
        assertThat(PayrollController.filter(slips, "2023", "")).containsExactly(slip(1, "2023-01"));
        assertThat(PayrollController.filter(slips, "", "09")).containsExactly(slip(2, "2024-09"));
        assertThat(PayrollController.filter(slips, "2024", "01")).isEmpty();
    }

    @Test
    void filter_doesNotMatchPartOfAnotherYearOrMonth() {
        //"2024" で、"12024-.." のような、途中の一致を、拾わない。月も、"1" と "11" を、取り違えない
        List<SalarySlipSummary> slips = List.of(slip(1, "2024-11"), slip(2, "2024-01"));

        assertThat(PayrollController.filter(slips, "", "01")).containsExactly(slip(2, "2024-01"));
    }

    @Test
    void yearsOf_isDistinctAndNewestFirst_andSkipsBrokenValues() {
        List<SalarySlipSummary> slips = List.of(slip(1, "2023-05"), slip(2, "2024-09"), slip(3, "2024-01"),
                slip(4, null), slip(5, "broken"));

        assertThat(PayrollController.yearsOf(slips)).containsExactly("2024", "2023");
    }

    // ---------- APIのエラー ----------

    @Test
    void whenEmployeeDoesNotExist_showsNotFoundPage() throws Exception {
        employeesOnePage();
        when(api.get(eq(SLIPS_URI), any(ParameterizedTypeReference.class), eq(999L)))
                .thenThrow(new NotFoundException("社員が見つかりません"));

        mockMvc.perform(get("/payroll").param("employeeId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(get("/payroll"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void whenEmployeeIdIsNotANumber_showsBadRequestPage() throws Exception {
        employeesOnePage();

        mockMvc.perform(get("/payroll").param("employeeId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }
}

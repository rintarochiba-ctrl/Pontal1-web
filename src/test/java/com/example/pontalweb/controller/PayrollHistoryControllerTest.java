package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
import com.example.pontalweb.client.SalarySlipUploadHistory;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.model.UploadHistoryRow;

//PayrollHistoryController(給与明細のアップロード履歴)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「社員のidを名前に直すこと」「次のページがあるかの判定」「日時の表示」「エラーの画面」を確認する
@WebMvcTest(PayrollHistoryController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(権限は、SecurityConfigTestと、結合テストで確認)
class PayrollHistoryControllerTest {

    private static final String HISTORY_URI = "/api/salary-slips/history?page={page}&size={size}";
    private static final String EMPLOYEES_URI = "/api/employees?page={page}&size={size}";
    private static final int PAGE_SIZE = 20;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private SalarySlipUploadHistory history(long slipId, long employeeId, String payMonth, Long uploadedBy, String uploadedAt) {
        return new SalarySlipUploadHistory(slipId, employeeId, payMonth, uploadedBy, uploadedAt);
    }

    @SuppressWarnings("unchecked")
    private void historyPage(int page, List<SalarySlipUploadHistory> items) {
        when(api.get(eq(HISTORY_URI), any(ParameterizedTypeReference.class), eq(page), eq(PAGE_SIZE))).thenReturn(items);
    }

    private void employeesExist() {
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(
                        new Option(3L, "山田太郎", "開発部"), new Option(1L, "人事担当", null)), null));
    }

    private List<SalarySlipUploadHistory> fullPage() {
        return IntStream.range(0, PAGE_SIZE)
                .mapToObj(i -> history(100 - i, 3, "2024-09", 1L, "2026-10-21T12:34:56"))
                .toList();
    }

    // ---------- 表示 ----------

    @Test
    @SuppressWarnings("unchecked")
    void history_showsRowsWithEmployeeNames_andFormattedValues() throws Exception {
        historyPage(0, List.of(history(30, 3, "2024-09", 1L, "2026-10-21T12:34:56.789")));
        employeesExist();

        mockMvc.perform(get("/payroll/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("payroll-history"))
                .andExpect(model().attribute("rows", List.of(
                        new UploadHistoryRow(3L, "山田太郎", "2024年09月支給分", "人事担当", "2026/10/21 12:34"))))
                .andExpect(model().attribute("page", 0))
                .andExpect(model().attribute("hasPrev", false))
                .andExpect(model().attribute("hasNext", false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void history_whenEmpty_doesNotLoadEmployees() throws Exception {
        historyPage(0, List.of());

        mockMvc.perform(get("/payroll/history"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rows", List.of()));

        verify(api, never()).get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), any(), any());
    }

    // ---------- ページ送り ----------

    @Test
    void history_whenFullPageAndNextPageHasItems_hasNext() throws Exception {
        historyPage(0, fullPage());
        historyPage(1, List.of(history(1, 3, "2024-01", 1L, "2026-10-01T09:00:00")));
        employeesExist();

        mockMvc.perform(get("/payroll/history"))
                .andExpect(model().attribute("hasNext", true));
    }

    @Test
    void history_whenFullPageButNextPageIsEmpty_hasNoNext() throws Exception {
        //ちょうど割り切れる件数のとき、空のページへの「次へ」を、出さない
        historyPage(0, fullPage());
        historyPage(1, List.of());
        employeesExist();

        mockMvc.perform(get("/payroll/history"))
                .andExpect(model().attribute("hasNext", false));
    }

    @Test
    void history_whenPageIsNotFull_doesNotCheckNextPage() throws Exception {
        historyPage(0, List.of(history(1, 3, "2024-01", 1L, "2026-10-01T09:00:00")));
        employeesExist();

        mockMvc.perform(get("/payroll/history")).andExpect(model().attribute("hasNext", false));

        verify(api, never()).get(eq(HISTORY_URI), any(ParameterizedTypeReference.class), eq(1), eq(PAGE_SIZE));
    }

    @Test
    void history_onSecondPage_hasPrev() throws Exception {
        historyPage(1, List.of(history(1, 3, "2024-01", 1L, "2026-10-01T09:00:00")));
        employeesExist();

        mockMvc.perform(get("/payroll/history").param("page", "1"))
                .andExpect(model().attribute("page", 1))
                .andExpect(model().attribute("hasPrev", true));
    }

    @Test
    void history_whenPageIsNegative_treatedAsFirstPage() throws Exception {
        historyPage(0, List.of());

        mockMvc.perform(get("/payroll/history").param("page", "-5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", 0));
    }

    // ---------- 部品(Controllerの中の、static メソッド) ----------

    @Test
    void nameOf_showsIdWhenEmployeeIsUnknown_andDashWhenIdIsMissing() {
        Map<Long, String> names = Map.of(3L, "山田太郎");

        assertThat(PayrollHistoryController.nameOf(names, 3L)).isEqualTo("山田太郎");
        assertThat(PayrollHistoryController.nameOf(names, 99L)).isEqualTo("不明な社員(ID: 99)"); //削除された社員、など
        assertThat(PayrollHistoryController.nameOf(names, null)).isEqualTo("-");
    }

    @Test
    void formatDateTime_formatsIso_andKeepsUnexpectedValues() {
        assertThat(PayrollHistoryController.formatDateTime("2026-10-21T12:34:56")).isEqualTo("2026/10/21 12:34");
        assertThat(PayrollHistoryController.formatDateTime("2026-10-21T12:34:56.789012")).isEqualTo("2026/10/21 12:34");
        assertThat(PayrollHistoryController.formatDateTime("昨日")).isEqualTo("昨日");
        assertThat(PayrollHistoryController.formatDateTime(null)).isEqualTo("-");
        assertThat(PayrollHistoryController.formatDateTime("")).isEqualTo("-");
    }

    // ---------- APIのエラー ----------

    @Test
    @SuppressWarnings("unchecked")
    void history_whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        when(api.get(eq(HISTORY_URI), any(ParameterizedTypeReference.class), eq(0), eq(PAGE_SIZE)))
                .thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(get("/payroll/history"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void history_whenPageIsNotANumber_showsBadRequestPage() throws Exception {
        mockMvc.perform(get("/payroll/history").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }
}

package com.example.pontalweb.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.AuditLogBatch;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.ForbiddenException;

//AuditLogController(監査ログ)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「APIに渡す年月」「画面(Model)に渡す値」「APIのエラーが、正しい画面になること」を確認する
@WebMvcTest(AuditLogController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(権限は、結合テストで確認)
class AuditLogControllerTest {

    private static final String URI = "/api/audit-log-batches?year={year}&month={month}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private AuditLogBatch batch(long id, String status, Integer count) {
        return new AuditLogBatch(id, "2026-10-05", "2026-10-11", status, count);
    }

    @SuppressWarnings("unchecked")
    private void apiReturns(int year, int month, List<AuditLogBatch> batches) {
        when(api.get(eq(URI), any(ParameterizedTypeReference.class), eq(year), eq(month))).thenReturn(batches);
    }

    // ---------- 操作履歴(準備中の画面) ----------

    @Test
    void index_showsHistoryView() throws Exception {
        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-history"));
    }

    // ---------- 週ごとのCSV ----------

    @Test
    void exports_withYearAndMonth_callsApiWithThem_andPutsResultIntoModel() throws Exception {
        List<AuditLogBatch> batches = List.of(batch(12, "COMPLETED", 100));
        apiReturns(2025, 3, batches);

        mockMvc.perform(get("/audit-logs/exports").param("year", "2025").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-exports"))
                .andExpect(model().attribute("batches", batches))
                .andExpect(model().attribute("year", 2025))
                .andExpect(model().attribute("month", 3));
    }

    @Test
    void exports_withoutYearAndMonth_usesThisMonth() throws Exception {
        LocalDate today = LocalDate.now();
        apiReturns(today.getYear(), today.getMonthValue(), List.of());

        mockMvc.perform(get("/audit-logs/exports"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("year", today.getYear()))
                .andExpect(model().attribute("month", today.getMonthValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exports_whenOnlyYearIsGiven_usesThisMonthForMonth() throws Exception {
        int thisMonth = LocalDate.now().getMonthValue();
        apiReturns(2024, thisMonth, List.of());

        mockMvc.perform(get("/audit-logs/exports").param("year", "2024"))
                .andExpect(model().attribute("year", 2024))
                .andExpect(model().attribute("month", thisMonth));

        verify(api).get(eq(URI), any(ParameterizedTypeReference.class), eq(2024), eq(thisMonth));
    }

    @Test
    void exports_offersThisYearAndTwoPreviousYears_newestFirst() throws Exception {
        int thisYear = LocalDate.now().getYear();
        apiReturns(thisYear, LocalDate.now().getMonthValue(), List.of());

        mockMvc.perform(get("/audit-logs/exports"))
                .andExpect(model().attribute("years", List.of(thisYear, thisYear - 1, thisYear - 2)));
    }

    // ---------- エラー ----------

    @Test
    @SuppressWarnings("unchecked")
    void exports_whenApiRejectsMonth_showsBadRequestPage_andNotLoginPage() throws Exception {
        //月が範囲外(13)のとき、APIが400を返す。画面は、400のエラー画面にする
        when(api.get(eq(URI), any(ParameterizedTypeReference.class), eq(2026), eq(13)))
                .thenThrow(new com.example.pontalweb.exception.BadRequestException("monthは1〜12で指定してください"));

        mockMvc.perform(get("/audit-logs/exports").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exports_whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        when(api.get(eq(URI), any(ParameterizedTypeReference.class), any(), any()))
                .thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(get("/audit-logs/exports"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exports_whenApiIsDown_showsServerErrorPage() throws Exception {
        when(api.get(eq(URI), any(ParameterizedTypeReference.class), any(), any()))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));

        mockMvc.perform(get("/audit-logs/exports"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    // ---------- CSVのダウンロード ----------

    private static final String DOWNLOAD_URI = "/api/audit-log-batches/{batchId}/download";

    @Test
    void download_returnsCsvAsAttachment_withFileNameFromPeriod() throws Exception {
        //API の CSV は、UTF-8 の BOM つき(Excelで、文字化けしない)。そのまま、返す
        byte[] csv = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', ',', 'b' };
        when(api.getBytes(eq(DOWNLOAD_URI), eq(12L))).thenReturn(csv);

        mockMvc.perform(get("/audit-logs/exports/12/download").param("start", "2026-10-05").param("end", "2026-10-11"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("UTF-8")))
                .andExpect(content().bytes(csv))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"audit-log_2026-10-05_2026-10-11.csv\""))
                //機密の書類なので、保存(キャッシュ)させない
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void download_usesBatchIdAsFileName_whenPeriodIsMissingOrInvalid() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(12L))).thenReturn("a".getBytes());

        mockMvc.perform(get("/audit-logs/exports/12/download"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-log_12.csv\""));
        //ファイル名に、変な文字(パスや、改行)を、入れさせない
        mockMvc.perform(get("/audit-logs/exports/12/download").param("start", "../../etc/passwd").param("end", "2026-10-11"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-log_12.csv\""));
        //片方だけ正しくても、代用にする
        mockMvc.perform(get("/audit-logs/exports/12/download").param("start", "2026-10-05"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-log_12.csv\""));
    }

    @Test
    void download_whenBatchIsNotCompleted_showsConflictPage() throws Exception {
        //完了していない週(処理中・失敗)は、APIが409を返す
        when(api.getBytes(eq(DOWNLOAD_URI), eq(13L)))
                .thenThrow(new com.example.pontalweb.exception.ConflictException("まだダウンロードできません"));

        mockMvc.perform(get("/audit-logs/exports/13/download"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/409"));
    }

    @Test
    void download_whenBatchDoesNotExist_showsNotFoundPage() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(999L)))
                .thenThrow(new com.example.pontalweb.exception.NotFoundException("見つかりません"));

        mockMvc.perform(get("/audit-logs/exports/999/download"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void download_whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(12L))).thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(get("/audit-logs/exports/12/download"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void download_whenApiReturnsNothing_showsServerErrorPage() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(12L))).thenReturn(null);

        mockMvc.perform(get("/audit-logs/exports/12/download"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    @Test
    void download_whenBatchIdIsNotANumber_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(get("/audit-logs/exports/abc/download"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        org.mockito.Mockito.verifyNoInteractions(api);
    }

    @Test
    void exports_whenYearIsNotANumber_showsBadRequestPage() throws Exception {
        mockMvc.perform(get("/audit-logs/exports").param("year", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }
}

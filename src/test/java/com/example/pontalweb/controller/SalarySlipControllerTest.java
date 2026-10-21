package com.example.pontalweb.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipSummary;
import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.model.LoginUser;

//SalarySlipController(給与明細の一覧とダウンロード)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「ログイン中の本人の分を取ること」「PDFを、保存するファイルとして返すこと」「エラーの画面」を確認する
@WebMvcTest(SalarySlipController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(ログインが必要かは、SecurityConfigTestで確認)
class SalarySlipControllerTest {

    private static final String LIST_URI = "/api/employees/{id}/salary-slips";
    private static final String DOWNLOAD_URI = "/api/salary-slips/{id}/download";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private LoginUser loginUser(long id) {
        return new LoginUser(id, "山田太郎", false, false);
    }

    // ---------- 一覧 ----------

    @Test
    @SuppressWarnings("unchecked")
    void list_callsApiWithLoggedInEmployeeId_andPutsSlipsIntoModel() throws Exception {
        List<SalarySlipSummary> slips = List.of(new SalarySlipSummary(3L, "2024-09"), new SalarySlipSummary(2L, "2024-08"));
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L))).thenReturn(slips);

        mockMvc.perform(get("/salary-slips").sessionAttr(SessionKeys.LOGIN_USER, loginUser(7)))
                .andExpect(status().isOk())
                .andExpect(view().name("salary-slips"))
                .andExpect(model().attribute("slips", slips));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_whenNoSlips_stillShowsView() throws Exception {
        when(api.get(eq(LIST_URI), any(ParameterizedTypeReference.class), eq(7L))).thenReturn(List.of());

        mockMvc.perform(get("/salary-slips").sessionAttr(SessionKeys.LOGIN_USER, loginUser(7)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("slips", List.of()));
    }

    @Test
    void list_withoutLoginUserInSession_redirectsToLogin_withoutCallingApi() throws Exception {
        mockMvc.perform(get("/salary-slips"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verifyNoInteractions(api);
    }

    // ---------- ダウンロード ----------

    @Test
    void download_returnsPdfAsAttachment_withFileNameFromPayMonth() throws Exception {
        byte[] pdf = "%PDF-dummy".getBytes();
        when(api.getBytes(eq(DOWNLOAD_URI), eq(3L))).thenReturn(pdf);

        mockMvc.perform(get("/salary-slips/3/download").param("payMonth", "2024-09"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"salary-slip_2024-09.pdf\""))
                //機密の書類なので、保存(キャッシュ)させない
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void download_usesSlipIdAsFileName_whenPayMonthIsMissingOrInvalid() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(3L))).thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/salary-slips/3/download"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"salary-slip_3.pdf\""));
        //ファイル名に、変な文字(パスや、改行)を入れさせない
        mockMvc.perform(get("/salary-slips/3/download").param("payMonth", "../../etc/passwd"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"salary-slip_3.pdf\""));
    }

    @Test
    void download_whenSlipBelongsToSomeoneElse_showsForbiddenPage() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(9L))).thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(get("/salary-slips/9/download"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void download_whenSlipDoesNotExist_showsNotFoundPage() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(999L))).thenThrow(new NotFoundException("給与明細が見つかりません"));

        mockMvc.perform(get("/salary-slips/999/download"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void download_whenApiReturnsNothing_showsServerErrorPage() throws Exception {
        when(api.getBytes(eq(DOWNLOAD_URI), eq(3L))).thenReturn(null);

        mockMvc.perform(get("/salary-slips/3/download"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    @Test
    void download_whenIdIsNotANumber_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(get("/salary-slips/abc/download"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoInteractions(api);
    }
}

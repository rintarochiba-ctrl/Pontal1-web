package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.MultiValueMap;

import com.example.pontalweb.client.EmployeeOptionPage;
import com.example.pontalweb.client.EmployeeOptionPage.Option;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipUploadResult;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;

//PayrollUploadController(給与明細のアップロード)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「APIに送る内容」「ファイルの確認」「失敗のとき入力画面に戻ること」を確認する
@WebMvcTest(PayrollUploadController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは、外す(権限は、SecurityConfigTestと、結合テストで確認)
class PayrollUploadControllerTest {

    private static final String UPLOAD_URI = "/api/salary-slips";
    private static final String EMPLOYEES_URI = "/api/employees?page={page}&size={size}";
    private static final byte[] PDF = "%PDF-1.4 dummy".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private void employeesExist() {
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(new Option(3L, "山田太郎", "開発部")), null));
    }

    private MockHttpServletRequestBuilder submit(MockMultipartFile file) {
        return multipart("/payroll/upload")
                .file(file)
                .param("employeeId", "3")
                .param("payMonth", "2024-09");
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "給与_2024年9月.PDF", "application/pdf", PDF);
    }

    @SuppressWarnings("unchecked")
    private MultiValueMap<String, Object> sentParts() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(api).postMultipart(eq(UPLOAD_URI), (MultiValueMap<String, Object>) captor.capture(),
                eq(SalarySlipUploadResult.class));
        return (MultiValueMap<String, Object>) captor.getValue();
    }

    // ---------- 入力画面 ----------

    @Test
    void form_showsEmployees_andMaxFileSize() throws Exception {
        employeesExist();

        mockMvc.perform(get("/payroll/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("payroll-upload"))
                .andExpect(model().attribute("options", List.of(new Option(3L, "山田太郎", "開発部"))))
                .andExpect(model().attribute("maxFileSize", "1MB"));
    }

    // ---------- 成功 ----------

    @Test
    void upload_success_sendsFormToApi_andRedirectsToThatEmployeesList() throws Exception {
        when(api.postMultipart(eq(UPLOAD_URI), any(), eq(SalarySlipUploadResult.class)))
                .thenReturn(new SalarySlipUploadResult(10L, 3L, "2024-09"));

        mockMvc.perform(submit(pdf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payroll?employeeId=3"))
                .andExpect(flash().attribute("successMessage", "給与明細をアップロードしました(2024-09)"));

        MultiValueMap<String, Object> parts = sentParts();
        assertThat(parts.getFirst("employeeId")).isEqualTo("3");
        assertThat(parts.getFirst("payMonth")).isEqualTo("2024-09");
        assertThat(parts.getFirst("overwrite")).isEqualTo("false");
        //ファイルは、中身そのまま。ファイル名は、元の名前ではなく、固定の名前
        ByteArrayResource file = (ByteArrayResource) parts.getFirst("file");
        assertThat(file.getByteArray()).isEqualTo(PDF);
        assertThat(file.getFilename()).isEqualTo("salary-slip.pdf");
    }

    @Test
    void upload_withOverwriteChecked_sendsOverwriteTrue() throws Exception {
        when(api.postMultipart(eq(UPLOAD_URI), any(), eq(SalarySlipUploadResult.class)))
                .thenReturn(new SalarySlipUploadResult(10L, 3L, "2024-09"));

        mockMvc.perform(submit(pdf()).param("overwrite", "true"))
                .andExpect(redirectedUrl("/payroll?employeeId=3"));

        assertThat(sentParts().getFirst("overwrite")).isEqualTo("true");
    }

    // ---------- ファイルの確認(APIを呼ぶ前に止める) ----------

    @Test
    void upload_whenFileIsEmpty_showsFormAgain_withoutCallingApi() throws Exception {
        employeesExist();

        mockMvc.perform(submit(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isOk())
                .andExpect(view().name("payroll-upload"))
                .andExpect(model().attribute("errorMessage", "PDFファイルを選択してください"));

        verify(api, org.mockito.Mockito.never()).postMultipart(any(), any(), any());
    }

    @Test
    void upload_whenExtensionIsNotPdf_showsFormAgain_withoutCallingApi() throws Exception {
        employeesExist();

        mockMvc.perform(submit(new MockMultipartFile("file", "給与.xlsx", "application/pdf", PDF)))
                .andExpect(view().name("payroll-upload"))
                .andExpect(model().attribute("errorMessage", "PDFファイル(.pdf)を選択してください"));

        verify(api, org.mockito.Mockito.never()).postMultipart(any(), any(), any());
    }

    @Test
    void upload_whenContentIsNotPdf_showsFormAgain_withoutCallingApi() throws Exception {
        employeesExist();

        //名前だけ .pdf にした、PDFでないファイル
        mockMvc.perform(submit(new MockMultipartFile("file", "fake.pdf", "application/pdf", "<html>".getBytes())))
                .andExpect(view().name("payroll-upload"))
                .andExpect(model().attribute("errorMessage", "PDFファイルではないようです。ファイルを確認してください"));

        verify(api, org.mockito.Mockito.never()).postMultipart(any(), any(), any());
    }

    // ---------- APIが断ったとき(入力画面に戻す) ----------

    @Test
    void upload_whenAlreadyExists_showsFormAgain_withOverwriteGuide_andKeepsSelection() throws Exception {
        employeesExist();
        when(api.postMultipart(eq(UPLOAD_URI), any(), eq(SalarySlipUploadResult.class)))
                .thenThrow(new ConflictException("既に2024-09分の給与明細が登録されています"));

        mockMvc.perform(submit(pdf()))
                .andExpect(status().isOk())
                .andExpect(view().name("payroll-upload"))
                .andExpect(model().attribute("errorMessage",
                        "既に2024-09分の給与明細が登録されています。上書きする場合は、「上書きする」にチェックを入れて、もう一度、ファイルを選んで送信してください"))
                //選んだ社員・年月は、残る(ファイルだけは、選び直し)
                .andExpect(model().attribute("form", Map.of("employeeId", 3L, "payMonth", "2024-09", "overwrite", false)));
    }

    @Test
    void upload_whenApiRejectsInput_showsApiMessage() throws Exception {
        employeesExist();
        when(api.postMultipart(eq(UPLOAD_URI), any(), eq(SalarySlipUploadResult.class)))
                .thenThrow(new BadRequestException("payMonthはYYYY-MM形式で指定してください"));

        mockMvc.perform(submit(pdf()))
                .andExpect(view().name("payroll-upload"))
                .andExpect(model().attribute("errorMessage", "payMonthはYYYY-MM形式で指定してください"));
    }

    @Test
    void upload_whenEmployeeDoesNotExist_showsApiMessage() throws Exception {
        employeesExist();
        when(api.postMultipart(eq(UPLOAD_URI), any(), eq(SalarySlipUploadResult.class)))
                .thenThrow(new NotFoundException("社員が見つかりません"));

        mockMvc.perform(submit(pdf()))
                .andExpect(view().name("payroll-upload"))
                .andExpect(model().attribute("errorMessage", "社員が見つかりません"));
    }

    @Test
    void upload_whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        when(api.postMultipart(eq(UPLOAD_URI), any(), eq(SalarySlipUploadResult.class)))
                .thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(submit(pdf()))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void upload_whenRequiredParameterIsMissing_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(multipart("/payroll/upload").file(pdf()).param("employeeId", "3")) //年月が無い
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoMoreInteractions(api);
    }
}

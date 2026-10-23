package com.example.pontalweb.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.EmployeeListResponse;
import com.example.pontalweb.client.EmployeeSummary;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.UnauthorizedException;

//EmployeeController(社員一覧)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「APIに渡す値」「画面(Model)に渡す値」「APIのエラーが、正しい画面になること」を確認する
@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(ログインが必要かは、SecurityConfigTestで確認)
class EmployeeControllerTest {

    private static final String URI = "/api/employees?page={page}&size={size}";
    private static final String URI_WITH_KEYWORD = "/api/employees?page={page}&size={size}&keyword={keyword}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private EmployeeSummary employee(long id, String name) {
        return new EmployeeSummary(id, name, name + "@example.com", "開発部", "メンバー");
    }

    private EmployeeListResponse response(int totalCount, Integer next, Integer prev, EmployeeSummary... items) {
        return new EmployeeListResponse(List.of(items), totalCount, next, prev);
    }

    // ---------- APIに渡す値 ----------

    @Test
    void list_withoutParams_callsApiWithPage0AndSize8_andNoKeyword() throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(response(1, null, null, employee(1, "山田太郎")));

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees"));

        verify(api).get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8));
    }

    @Test
    void list_passesPageToApi() throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(2), eq(8)))
                .thenReturn(response(20, null, 1, employee(17, "A")));

        mockMvc.perform(get("/employees").param("page", "2"))
                .andExpect(model().attribute("page", 2));

        verify(api).get(eq(URI), eq(EmployeeListResponse.class), eq(2), eq(8));
    }

    @Test
    void list_withKeyword_passesTrimmedKeywordToApi() throws Exception {
        when(api.get(eq(URI_WITH_KEYWORD), eq(EmployeeListResponse.class), eq(0), eq(8), eq("開発")))
                .thenReturn(response(1, null, null, employee(1, "山田太郎")));

        mockMvc.perform(get("/employees").param("keyword", "  開発  "))
                .andExpect(status().isOk())
                //画面の検索欄には、入力した文字が、そのまま戻る
                .andExpect(model().attribute("keyword", "  開発  "));

        verify(api).get(eq(URI_WITH_KEYWORD), eq(EmployeeListResponse.class), eq(0), eq(8), eq("開発"));
    }

    @Test
    void list_withBlankKeyword_doesNotSendKeywordToApi() throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(response(0, null, null));

        mockMvc.perform(get("/employees").param("keyword", "   "));

        verify(api).get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8));
        verify(api, never()).get(eq(URI_WITH_KEYWORD), eq(EmployeeListResponse.class), eq(0), eq(8), eq(""));
    }

    // ---------- 画面(Model)に渡す値 ----------

    @Test
    void list_putsApiResultIntoModel() throws Exception {
        List<EmployeeSummary> items = List.of(employee(1, "山田太郎"), employee(2, "佐藤花子"));
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(new EmployeeListResponse(items, 20, 1, null));

        mockMvc.perform(get("/employees"))
                .andExpect(model().attribute("employees", items))
                .andExpect(model().attribute("totalCount", 20))
                .andExpect(model().attribute("next", 1))
                .andExpect(model().attribute("prev", (Object) null))
                .andExpect(model().attribute("keyword", ""));
    }

    @ParameterizedTest
    @CsvSource({ "0, 0", "1, 1", "7, 1", "8, 1", "9, 2", "16, 2", "17, 3" })
    void list_calculatesTotalPages_roundingUp(int totalCount, int expectedPages) throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(response(totalCount, null, null));

        //1ページ8件。総件数が8の倍数のときに、ページが1つ多くならないこと(8件→1ページ、9件→2ページ)
        mockMvc.perform(get("/employees"))
                .andExpect(model().attribute("totalPages", expectedPages));
    }

    @Test
    void list_whenNoEmployees_stillShowsView() throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenReturn(response(0, null, null));

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees"))
                .andExpect(model().attribute("totalCount", 0));
    }

    // ---------- APIのエラー ----------

    @Test
    void list_whenApiRejectsParameters_showsBadRequestPage() throws Exception {
        //pageがマイナスなどで、APIが400を返した
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(-1), eq(8)))
                .thenThrow(new BadRequestException("pageは0以上で指定してください"));

        mockMvc.perform(get("/employees").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"))
                .andExpect(model().attribute("message", "pageは0以上で指定してください"));
    }

    @Test
    void list_whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(get("/employees"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void list_whenTokenExpired_redirectsToLogin() throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenThrow(new UnauthorizedException("ログインが必要です"));

        mockMvc.perform(get("/employees"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void list_whenApiIsDown_showsServerErrorPage_withoutDetails() throws Exception {
        when(api.get(eq(URI), eq(EmployeeListResponse.class), eq(0), eq(8)))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));

        mockMvc.perform(get("/employees"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("message", (Object) null));
    }

    @Test
    void list_whenPageIsNotANumber_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(get("/employees").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoInteractions(api);
    }
}

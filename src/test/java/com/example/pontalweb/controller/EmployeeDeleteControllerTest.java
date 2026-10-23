package com.example.pontalweb.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.model.LoginUser;

//EmployeeController(社員の削除)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「APIを呼ぶこと」「自分自身は削除しないこと」「削除のあとの移動先」を確認する
@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(権限は、SecurityConfigTestで確認)
class EmployeeDeleteControllerTest {

    private static final String URI = "/api/employees/{id}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private LoginUser loginUser(long id) {
        return new LoginUser(id, "管理者", true, false);
    }

    @Test
    void delete_success_callsApi_andRedirectsToListWithMessage() throws Exception {
        mockMvc.perform(post("/employees/5/delete").sessionAttr(SessionKeys.LOGIN_USER, loginUser(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"))
                .andExpect(flash().attribute("successMessage", "社員を削除しました"));

        verify(api).delete(eq(URI), eq(5L));
    }

    @Test
    void delete_ownAccount_isNotExecuted_andReturnsToDetailWithMessage() throws Exception {
        mockMvc.perform(post("/employees/1/delete").sessionAttr(SessionKeys.LOGIN_USER, loginUser(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees/1"))
                .andExpect(flash().attribute("errorMessage", "自分自身は削除できません"));

        verifyNoInteractions(api);
    }

    @Test
    void delete_whenEmployeeDoesNotExist_showsNotFoundPage() throws Exception {
        doThrow(new NotFoundException("社員が見つかりません")).when(api).delete(eq(URI), eq(999L));

        mockMvc.perform(post("/employees/999/delete").sessionAttr(SessionKeys.LOGIN_USER, loginUser(1)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void delete_whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        doThrow(new ForbiddenException("社員を削除する権限がありません")).when(api).delete(eq(URI), eq(5L));

        mockMvc.perform(post("/employees/5/delete").sessionAttr(SessionKeys.LOGIN_USER, loginUser(1)))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void delete_whenApiFails_showsServerErrorPage() throws Exception {
        //DBの削除は済んだが、Cognitoの無効化に失敗した、などのとき、APIは500を返す
        doThrow(new ApiServerException("PontalのAPIがエラーを返しました status=500")).when(api).delete(eq(URI), eq(5L));

        mockMvc.perform(post("/employees/5/delete").sessionAttr(SessionKeys.LOGIN_USER, loginUser(1)))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    @Test
    void delete_whenIdIsNotANumber_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(post("/employees/abc/delete").sessionAttr(SessionKeys.LOGIN_USER, loginUser(1)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoInteractions(api);
    }
}

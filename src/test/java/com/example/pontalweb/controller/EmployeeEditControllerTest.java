package com.example.pontalweb.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.EmployeeDetail;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.NotFoundException;

//EmployeeController(社員編集の入力画面)のテスト。PontalのAPIは、疑似のもの(Mock)にする
@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(権限は、SecurityConfigTestで確認)
class EmployeeEditControllerTest {

    private static final String URI = "/api/employees/{id}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    @Test
    void editForm_showsCurrentValues() throws Exception {
        EmployeeDetail employee = new EmployeeDetail(3L, "山田太郎", "test@example.com", "開発部", "エンジニア",
                "2018-04-01", "男性", 38, "東京都", "キャンプ", "よろしくお願いします", null, "employees/3.png", true, false);
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(employee);

        mockMvc.perform(get("/employees/3/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee-edit"))
                .andExpect(model().attribute("employee", employee));
    }

    @Test
    void editForm_whenEmployeeDoesNotExist_showsNotFoundPage() throws Exception {
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(999L)))
                .thenThrow(new NotFoundException("社員が見つかりません"));

        mockMvc.perform(get("/employees/999/edit"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void editForm_whenIdIsNotANumber_showsBadRequestPage() throws Exception {
        mockMvc.perform(get("/employees/abc/edit"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }
}

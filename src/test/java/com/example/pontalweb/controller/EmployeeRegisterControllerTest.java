package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.EmployeeCreateRequest;
import com.example.pontalweb.client.EmployeeDetail;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;

//EmployeeController(社員登録)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「APIに送る内容」「登録できたあとの移動先」を確認する
@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(権限は、SecurityConfigTestで確認)
class EmployeeRegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private void apiReturnsCreatedId(long id) {
        EmployeeDetail created = new EmployeeDetail(id, "鈴木三郎", "s.suzuki@example.com", "開発部", null,
                "2026-04-01", null, null, null, null, null, null, null, false, false);
        when(api.post(eq("/api/employees"), any(), eq(EmployeeDetail.class))).thenReturn(created);
    }

    private ArgumentCaptor<Object> sentBody() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(api).post(eq("/api/employees"), captor.capture(), eq(EmployeeDetail.class));
        return captor;
    }

    @Test
    void registerForm_showsInputScreen() throws Exception {
        mockMvc.perform(get("/employees/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee-register"));

        verifyNoInteractions(api);
    }

    @Test
    void register_success_sendsInputToApi_andRedirectsToDetail() throws Exception {
        apiReturnsCreatedId(5);

        mockMvc.perform(post("/employees")
                .param("name", "  鈴木三郎 ")
                .param("email", " s.suzuki@example.com ")
                .param("initialPassword", "Temp#Pass123")
                .param("joinDate", "2026-04-01")
                .param("department", " 開発部 ")
                .param("position", "メンバー")
                .param("isSystemAdmin", "true")
                .param("isHrAdmin", "true"))
                .andExpect(status().is3xxRedirection())
                //登録できたら、そのまま、編集画面(プロフィールの入力)に移動する
                .andExpect(redirectedUrl("/employees/5/edit"))
                .andExpect(flash().attribute("successMessage",
                        "社員を登録しました。続けて、性別・年齢・出身地などのプロフィールを入力してください"));

        EmployeeCreateRequest sent = (EmployeeCreateRequest) sentBody().getValue();
        //名前・メール・部署は前後の空白を取る。パスワードは、そのまま
        assertThat(sent).isEqualTo(new EmployeeCreateRequest("鈴木三郎", "s.suzuki@example.com", "Temp#Pass123",
                "2026-04-01", "開発部", "メンバー", true, true));
    }

    @Test
    void register_withoutCheckboxes_sendsFalse_andBlankPositionAsNull() throws Exception {
        apiReturnsCreatedId(6);

        //チェックボックスは、チェックしないと、パラメータ自体が送られてこない
        mockMvc.perform(post("/employees")
                .param("name", "鈴木三郎")
                .param("email", "s.suzuki@example.com")
                .param("initialPassword", "Temp#Pass123")
                .param("joinDate", "2026-04-01")
                .param("department", "開発部")
                .param("position", "   "))
                .andExpect(redirectedUrl("/employees/6/edit"));

        EmployeeCreateRequest sent = (EmployeeCreateRequest) sentBody().getValue();
        assertThat(sent.systemAdmin()).isFalse();
        assertThat(sent.hrAdmin()).isFalse();
        assertThat(sent.position()).isNull();
    }

    // ---------- 失敗したとき(入力画面に戻す) ----------

    private org.springframework.test.web.servlet.ResultActions submit() throws Exception {
        return mockMvc.perform(post("/employees")
                .param("name", "鈴木三郎")
                .param("email", "s.suzuki@example.com")
                .param("initialPassword", "Temp#Pass123")
                .param("joinDate", "2026-04-01")
                .param("department", "開発部")
                .param("position", "メンバー")
                .param("isSystemAdmin", "true"));
    }

    @Test
    void register_whenEmailAlreadyExists_showsFormAgain_withApiMessage_andKeepsInput() throws Exception {
        when(api.post(eq("/api/employees"), any(), eq(EmployeeDetail.class)))
                .thenThrow(new ConflictException("このメールアドレスは既に登録されています"));

        submit()
                .andExpect(status().isOk())
                .andExpect(view().name("employee-register"))
                .andExpect(model().attribute("errorMessage", "このメールアドレスは既に登録されています"))
                .andExpect(model().attribute("form", java.util.Map.of(
                        "name", "鈴木三郎", "email", "s.suzuki@example.com", "joinDate", "2026-04-01",
                        "department", "開発部", "position", "メンバー", "isSystemAdmin", true, "isHrAdmin", false)));
    }

    @Test
    void register_whenPasswordBreaksPolicy_showsApiMessage() throws Exception {
        when(api.post(eq("/api/employees"), any(), eq(EmployeeDetail.class)))
                .thenThrow(new BadRequestException("初期パスワードがパスワードポリシーを満たしていません"));

        submit()
                .andExpect(view().name("employee-register"))
                .andExpect(model().attribute("errorMessage", "初期パスワードがパスワードポリシーを満たしていません"));
    }

    @Test
    void register_whenApiReturnsEnglishFieldError_showsGeneralMessage() throws Exception {
        when(api.post(eq("/api/employees"), any(), eq(EmployeeDetail.class)))
                .thenThrow(new BadRequestException("email: must be a well-formed email address"));

        submit()
                .andExpect(view().name("employee-register"))
                .andExpect(model().attribute("errorMessage", "入力内容に誤りがあります。各項目を確認してください"));
    }

    @Test
    void register_whenFailed_doesNotPutPasswordIntoModel() throws Exception {
        when(api.post(eq("/api/employees"), any(), eq(EmployeeDetail.class)))
                .thenThrow(new ConflictException("このメールアドレスは既に登録されています"));

        var model = submit().andReturn().getModelAndView().getModel();

        //初期パスワードは、画面に戻さない(Modelのどこにも、入れない)
        assertThat(model.toString()).doesNotContain("Temp#Pass123");
    }

    @Test
    void register_whenFailed_doesNotSetSuccessMessage() throws Exception {
        when(api.post(eq("/api/employees"), any(), eq(EmployeeDetail.class)))
                .thenThrow(new ConflictException("このメールアドレスは既に登録されています"));

        submit().andExpect(flash().attributeCount(0));
    }

    @Test
    void register_whenApiIsDown_stillShowsServerErrorPage() throws Exception {
        when(api.post(eq("/api/employees"), any(), eq(EmployeeDetail.class)))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));

        submit()
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    @Test
    void register_missingRequiredParameter_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(post("/employees")
                .param("name", "鈴木三郎"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoInteractions(api);
    }
}

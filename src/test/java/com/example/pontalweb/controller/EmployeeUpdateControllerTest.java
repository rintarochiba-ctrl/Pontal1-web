package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.pontalweb.client.EmployeeDetail;
import com.example.pontalweb.client.EmployeeUpdateRequest;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.model.LoginUser;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;

//EmployeeController(社員編集の送信)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「APIに送る内容(全項目を送り直すこと)」「保存できたあとの移動先」を確認する
@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(権限は、SecurityConfigTestで確認)
class EmployeeUpdateControllerTest {

    private static final String URI = "/api/employees/{id}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    //画面から送られてくる、ひととおりの入力
    private MockHttpServletRequestBuilder submit(long id) {
        return post("/employees/" + id)
                .param("name", "  山田太郎 ")
                .param("joinDate", "2018-04-01")
                .param("department", "開発部")
                .param("position", "エンジニア")
                .param("gender", "男性")
                .param("age", "38")
                .param("birthplace", "東京都")
                .param("hobby", "キャンプ")
                .param("bio", "よろしくお願いします")
                .param("image", "employees/3.png")
                .param("qaQuestion", "Q")
                .param("qaAnswer", "A")
                .param("isSystemAdmin", "true");
    }

    private EmployeeUpdateRequest sent(long id) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(api).put(eq(URI), captor.capture(), eq(EmployeeDetail.class), eq(id));
        return (EmployeeUpdateRequest) captor.getValue();
    }

    @Test
    void update_success_sendsAllFieldsToApi_andRedirectsToDetail() throws Exception {
        mockMvc.perform(submit(3))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees/3"))
                .andExpect(flash().attribute("successMessage", "プロフィールを保存しました"));

        //APIは、全項目を置き換えるので、画面で編集しない項目(image、selfQa)も、そのまま送る
        assertThat(sent(3)).isEqualTo(new EmployeeUpdateRequest("山田太郎", "男性", 38, "東京都", "2018-04-01",
                "開発部", "エンジニア", "employees/3.png", "よろしくお願いします", "キャンプ",
                "[{\"question\":\"Q\",\"answer\":\"A\"}]", true, false));
    }

    @Test
    void update_blankOptionalFields_areSentAsNull() throws Exception {
        mockMvc.perform(post("/employees/3")
                .param("name", "山田太郎")
                .param("joinDate", "2018-04-01")
                .param("department", "  ")
                .param("position", "")
                .param("gender", "")
                .param("age", "")
                .param("birthplace", "")
                .param("hobby", "")
                .param("bio", "")
                .param("image", "")
                //Q&Aの欄が、3組とも空
                .param("qaQuestion", "", "", "")
                .param("qaAnswer", "", "", ""))
                .andExpect(redirectedUrl("/employees/3"));

        //空の文字を、保存しない(年齢が空のときも、null)。権限のチェックを外せば、false
        assertThat(sent(3)).isEqualTo(new EmployeeUpdateRequest("山田太郎", null, null, null, "2018-04-01",
                null, null, null, null, null, null, false, false));
    }

    @Test
    void update_missingRequiredParameter_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(post("/employees/3").param("name", "山田太郎")) //入社日が無い
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoInteractions(api);
    }

    @Test
    void update_whenAgeIsNotANumber_showsBadRequestPage_withoutCallingApi() throws Exception {
        mockMvc.perform(post("/employees/3")
                .param("name", "山田太郎")
                .param("joinDate", "2018-04-01")
                .param("age", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoInteractions(api);
    }

    // ---------- Q&A ----------

    @Test
    void update_sendsSelfQaAsJson_inOrder_skippingEmptyPairs() throws Exception {
        mockMvc.perform(post("/employees/3")
                .param("name", "山田太郎")
                .param("joinDate", "2018-04-01")
                .param("qaQuestion", " 好きな食べ物は? ", "", "休日は?")
                .param("qaAnswer", "ラーメン", "", "キャンプ"))
                .andExpect(redirectedUrl("/employees/3"));

        assertThat(sent(3).selfQa()).isEqualTo(
                "[{\"question\":\"好きな食べ物は?\",\"answer\":\"ラーメン\"},{\"question\":\"休日は?\",\"answer\":\"キャンプ\"}]");
    }

    @Test
    void update_selfQaIsNotSent_whenNoQaFieldsAtAll() throws Exception {
        mockMvc.perform(post("/employees/3")
                .param("name", "山田太郎")
                .param("joinDate", "2018-04-01"))
                .andExpect(redirectedUrl("/employees/3"));

        assertThat(sent(3).selfQa()).isNull();
    }

    // ---------- 自分自身の権限 ----------

    //システム管理者のチェックを外した(isSystemAdmin を送らない)入力
    private MockHttpServletRequestBuilder submitWithoutSystemAdmin(long id) {
        return post("/employees/" + id)
                .param("name", "山田太郎")
                .param("joinDate", "2018-04-01");
    }

    @Test
    void update_editingSelf_keepsSystemAdmin_evenIfCheckboxIsNotSent() throws Exception {
        //ログイン中の人(id=3)が、自分(id=3)を編集して、システム管理者のチェックを外して送ってきた
        mockMvc.perform(submitWithoutSystemAdmin(3)
                .sessionAttr(SessionKeys.LOGIN_USER, new LoginUser(3L, "山田太郎", true, false)))
                .andExpect(redirectedUrl("/employees/3"));

        assertThat(sent(3).systemAdmin()).isTrue(); //外されず、システム管理者のまま
    }

    @Test
    void update_editingSomeoneElse_canRemoveSystemAdmin() throws Exception {
        //ログイン中の人(id=1)が、別の人(id=3)の権限を外すのは、できる
        mockMvc.perform(submitWithoutSystemAdmin(3)
                .sessionAttr(SessionKeys.LOGIN_USER, new LoginUser(1L, "管理者", true, false)))
                .andExpect(redirectedUrl("/employees/3"));

        assertThat(sent(3).systemAdmin()).isFalse();
    }

    @Test
    void update_editingSelf_doesNotAffectHrAdmin() throws Exception {
        mockMvc.perform(submitWithoutSystemAdmin(3)
                .sessionAttr(SessionKeys.LOGIN_USER, new LoginUser(3L, "山田太郎", true, false)))
                .andExpect(redirectedUrl("/employees/3"));

        assertThat(sent(3).hrAdmin()).isFalse(); //HR管理者は、自分でも外せる(送った値のまま)
    }

    // ---------- 失敗したとき(編集画面に戻す) ----------

    @Test
    void update_whenApiRejectsInput_showsEditFormAgain_keepsInput_andFetchesEmailAgain() throws Exception {
        when(api.put(eq(URI), any(), eq(EmployeeDetail.class), eq(3L)))
                .thenThrow(new BadRequestException("hobby: size must be between 0 and 255"));
        EmployeeDetail current = new EmployeeDetail(3L, "旧名前", "test@example.com", "旧部署", null,
                "2000-01-01", null, null, null, null, null, null, null, false, false);
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L))).thenReturn(current);

        mockMvc.perform(submit(3))
                .andExpect(status().isOk())
                .andExpect(view().name("employee-edit"))
                //英語まじりの400は、一般的な日本語の文にする
                .andExpect(model().attribute("errorMessage", "入力内容に誤りがあります。各項目を確認してください"))
                //入力した内容が残る。メールアドレスは、画面で編集しないので、APIの値
                .andExpect(model().attribute("employee", new EmployeeDetail(3L, "山田太郎", "test@example.com",
                        "開発部", "エンジニア", "2018-04-01", "男性", 38, "東京都", "キャンプ", "よろしくお願いします",
                        "[{\"question\":\"Q\",\"answer\":\"A\"}]", "employees/3.png", true, false)))
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void update_whenApiReturnsJapaneseMessage_showsItAsIs() throws Exception {
        when(api.put(eq(URI), any(), eq(EmployeeDetail.class), eq(3L)))
                .thenThrow(new BadRequestException("入社日が正しくありません"));
        when(api.get(eq(URI), eq(EmployeeDetail.class), eq(3L)))
                .thenReturn(new EmployeeDetail(3L, "山田太郎", "test@example.com", null, null,
                        "2018-04-01", null, null, null, null, null, null, null, false, false));

        mockMvc.perform(submit(3))
                .andExpect(view().name("employee-edit"))
                //日本語のメッセージは、そのまま出す
                .andExpect(model().attribute("errorMessage", "入社日が正しくありません"));
    }

    @Test
    void update_whenEmployeeDoesNotExist_showsNotFoundPage() throws Exception {
        when(api.put(eq(URI), any(), eq(EmployeeDetail.class), eq(999L)))
                .thenThrow(new NotFoundException("社員が見つかりません"));

        mockMvc.perform(submit(999))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void update_whenApiSaysForbidden_showsForbiddenPage() throws Exception {
        when(api.put(eq(URI), any(), eq(EmployeeDetail.class), eq(3L)))
                .thenThrow(new ForbiddenException("社員を編集する権限がありません"));

        mockMvc.perform(submit(3))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }
}

package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.pontalweb.client.AnnouncementDetail;
import com.example.pontalweb.client.AnnouncementRequest;
import com.example.pontalweb.client.EmployeeListResponse;
import com.example.pontalweb.client.EmployeeSummary;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.model.AnnouncementForm;

//AnnouncementFormController(お知らせの作成・編集)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「画面に渡す値」「APIに送る値」「入力のまちがい・APIのエラーのときの画面」を確認する
@WebMvcTest(AnnouncementFormController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(管理者だけが開けることは、SecurityConfigTestで確認)
class AnnouncementFormControllerTest {

    private static final String CREATE_URI = "/api/announcements";
    private static final String ITEM_URI = "/api/announcements/{id}";
    private static final String EMPLOYEES_URI = "/api/employees?page=0&size=100";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private void stubOptions() {
        when(api.get(eq("/api/departments"), any(ParameterizedTypeReference.class)))
                .thenReturn(List.of("開発部", "人事部", "営業部"));
        when(api.get(eq(EMPLOYEES_URI), eq(EmployeeListResponse.class)))
                .thenReturn(new EmployeeListResponse(List.of(
                        new EmployeeSummary(1L, "山田太郎", "a@example.com", "開発部", "エンジニア"),
                        new EmployeeSummary(3L, "鈴木一郎", "c@example.com", "営業部", "主任")), 2, null, null));
    }

    private AnnouncementDetail detail(long id, boolean canEdit) {
        return new AnnouncementDetail(id, "年末調整のお願い", "対応依頼", true, "総務部", "DEPARTMENT", "開発部",
                "2026-10-22T10:30:00", "本文です", "2026-10-22T10:30:00", canEdit, false, "開発部", null);
    }

    //フォームに入力して送る(必須項目を、すべて入れたもの)
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost(String url) {
        return validPost(url, "ALL");
    }

    //宛先の種類を指定して、フォームを送る(同じ名前の値を、2回送ると、「ALL,DEPARTMENT」のように、つながってしまうので、1回だけ)
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost(String url,
            String targetType) {
        return post(url)
                .param("title", "年末調整のお願い")
                .param("body", "本文です")
                .param("category", "対応依頼")
                .param("important", "true")
                .param("targetType", targetType);
    }

    // ---------- 作成画面を開く ----------

    @Test
    void newForm_showsEmptyForm_withOptions() throws Exception {
        stubOptions();

        MvcResult result = mockMvc.perform(get("/announcements/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcement-form"))
                .andExpect(model().attribute("categories", List.of("技術", "社内情報", "対応依頼")))
                .andExpect(model().attribute("departments", List.of("開発部", "人事部", "営業部")))
                .andExpect(model().attributeExists("employees"))
                .andExpect(model().attributeDoesNotExist("errors", "errorMessage"))
                .andReturn();

        //作成なので、announcementId は無い(JSPは、これで、作成か編集かを見分ける)
        assertThat(result.getModelAndView().getModel().get("announcementId")).isNull();
        AnnouncementForm form = (AnnouncementForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getTitle()).isEmpty();
        assertThat(form.getTargetType()).isEqualTo("ALL"); //宛先は、全員から始める
    }

    @Test
    void newForm_whenOptionsAreMissing_stillShowsForm() throws Exception {
        //APIが、空の返事(null)を返しても、フォームは出る(選択肢が、空になるだけ)
        mockMvc.perform(get("/announcements/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcement-form"))
                .andExpect(model().attribute("departments", List.of()))
                .andExpect(model().attribute("employees", List.of()));
    }

    @Test
    void newForm_whenApiServerFails_showsServerErrorView() throws Exception {
        when(api.get(eq("/api/departments"), any(ParameterizedTypeReference.class)))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));

        mockMvc.perform(get("/announcements/new"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    // ---------- 作成を送信する ----------

    @Test
    void create_withValidInput_postsToApi_redirectsToDetail_withFlashMessage() throws Exception {
        stubOptions();
        when(api.post(eq(CREATE_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenReturn(detail(9, true));

        mockMvc.perform(validPost("/announcements/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/announcements/9"))
                .andExpect(flash().attribute("successMessage", "お知らせを作成しました"));

        ArgumentCaptor<AnnouncementRequest> sent = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(api).post(eq(CREATE_URI), sent.capture(), eq(AnnouncementDetail.class));
        assertThat(sent.getValue()).isEqualTo(new AnnouncementRequest(
                "年末調整のお願い", "本文です", "対応依頼", true, "ALL", null, null));
    }

    @Test
    void create_toDepartment_sendsOnlyDepartment() throws Exception {
        when(api.post(eq(CREATE_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenReturn(detail(9, true));

        //部署を選んでいるのに、社員の欄の値も、残って届いたとしても、APIには、部署だけを送る
        mockMvc.perform(validPost("/announcements/new", "DEPARTMENT")
                .param("targetDepartment", "営業部")
                .param("targetEmployeeId", "3"));

        ArgumentCaptor<AnnouncementRequest> sent = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(api).post(eq(CREATE_URI), sent.capture(), eq(AnnouncementDetail.class));
        assertThat(sent.getValue().targetType()).isEqualTo("DEPARTMENT");
        assertThat(sent.getValue().targetDepartment()).isEqualTo("営業部");
        assertThat(sent.getValue().targetEmployeeId()).isNull();
    }

    @Test
    void create_toEmployee_sendsOnlyEmployeeId() throws Exception {
        when(api.post(eq(CREATE_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenReturn(detail(9, true));

        mockMvc.perform(validPost("/announcements/new", "EMPLOYEE")
                .param("targetDepartment", "営業部")
                .param("targetEmployeeId", "3"));

        ArgumentCaptor<AnnouncementRequest> sent = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(api).post(eq(CREATE_URI), sent.capture(), eq(AnnouncementDetail.class));
        assertThat(sent.getValue().targetDepartment()).isNull();
        assertThat(sent.getValue().targetEmployeeId()).isEqualTo(3L);
    }

    @Test
    void create_withoutImportantCheckbox_sendsNotImportant() throws Exception {
        //チェックを入れないと、フォームから important は届かない → 重要ではない
        when(api.post(eq(CREATE_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenReturn(detail(9, true));

        mockMvc.perform(post("/announcements/new")
                .param("title", "t").param("body", "b").param("category", "技術").param("targetType", "ALL"));

        ArgumentCaptor<AnnouncementRequest> sent = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(api).post(eq(CREATE_URI), sent.capture(), eq(AnnouncementDetail.class));
        assertThat(sent.getValue().important()).isFalse();
    }

    @Test
    void create_withInvalidInput_showsFormAgain_withErrors_andKeepsInput_withoutCallingApi() throws Exception {
        stubOptions();

        MvcResult result = mockMvc.perform(post("/announcements/new")
                .param("title", "")
                .param("body", "書きかけの本文")
                .param("category", "")
                .param("targetType", "DEPARTMENT"))
                .andExpect(status().isOk()) //redirectせず、同じフォームを出し直す
                .andExpect(view().name("announcement-form"))
                .andExpect(model().attributeExists("errors"))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) result.getModelAndView().getModel().get("errors");
        assertThat(errors).containsOnlyKeys("title", "category", "target");
        //入力した内容は、フォームに残る
        AnnouncementForm form = (AnnouncementForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getBody()).isEqualTo("書きかけの本文");
        assertThat(form.getTargetType()).isEqualTo("DEPARTMENT");
        //選択肢も、出し直される
        assertThat(result.getModelAndView().getModel().get("departments")).isEqualTo(List.of("開発部", "人事部", "営業部"));
        verify(api, never()).post(any(String.class), any(), any(Class.class));
    }

    @Test
    void create_whenApiReturnsBadRequest_showsApiMessageAboveForm_andKeepsInput() throws Exception {
        stubOptions();
        when(api.post(eq(CREATE_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenThrow(new BadRequestException("部署が未設定のため、お知らせを投稿できません"));

        MvcResult result = mockMvc.perform(validPost("/announcements/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcement-form"))
                .andExpect(model().attribute("errorMessage", "部署が未設定のため、お知らせを投稿できません"))
                .andReturn();

        AnnouncementForm form = (AnnouncementForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getTitle()).isEqualTo("年末調整のお願い");
    }

    @Test
    void create_whenApiReturnsForbidden_showsForbiddenView() throws Exception {
        when(api.post(eq(CREATE_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class)))
                .thenThrow(new ForbiddenException("この操作を行う権限がありません"));

        mockMvc.perform(validPost("/announcements/new"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    // ---------- 編集画面を開く ----------

    @Test
    void editForm_showsFormFilledWithCurrentContent() throws Exception {
        stubOptions();
        when(api.get(eq(ITEM_URI), eq(AnnouncementDetail.class), eq(7L))).thenReturn(detail(7, true));

        MvcResult result = mockMvc.perform(get("/announcements/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcement-form"))
                .andExpect(model().attribute("announcementId", 7L))
                .andReturn();

        AnnouncementForm form = (AnnouncementForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getTitle()).isEqualTo("年末調整のお願い");
        assertThat(form.getBody()).isEqualTo("本文です");
        assertThat(form.getCategory()).isEqualTo("対応依頼");
        assertThat(form.isImportant()).isTrue();
        assertThat(form.getTargetType()).isEqualTo("DEPARTMENT");
        assertThat(form.getTargetDepartment()).isEqualTo("開発部");
    }

    @Test
    void editForm_whenCannotEdit_showsForbiddenView() throws Exception {
        when(api.get(eq(ITEM_URI), eq(AnnouncementDetail.class), eq(7L))).thenReturn(detail(7, false));

        mockMvc.perform(get("/announcements/7/edit"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void editForm_whenNotFound_showsNotFoundView() throws Exception {
        when(api.get(eq(ITEM_URI), eq(AnnouncementDetail.class), eq(99L)))
                .thenThrow(new NotFoundException("お知らせが見つかりません"));

        mockMvc.perform(get("/announcements/99/edit"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void editForm_withNonNumericId_showsBadRequestView() throws Exception {
        mockMvc.perform(get("/announcements/abc/edit"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }

    // ---------- 編集を送信する ----------

    @Test
    void update_withValidInput_putsToApi_redirectsToDetail_withFlashMessage() throws Exception {
        when(api.put(eq(ITEM_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class), eq(7L)))
                .thenReturn(detail(7, true));

        mockMvc.perform(validPost("/announcements/7/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/announcements/7"))
                .andExpect(flash().attribute("successMessage", "お知らせを更新しました"));

        ArgumentCaptor<AnnouncementRequest> sent = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(api).put(eq(ITEM_URI), sent.capture(), eq(AnnouncementDetail.class), eq(7L));
        assertThat(sent.getValue().title()).isEqualTo("年末調整のお願い");
    }

    @Test
    void update_canChangeTarget() throws Exception {
        when(api.put(eq(ITEM_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class), eq(7L)))
                .thenReturn(detail(7, true));

        mockMvc.perform(validPost("/announcements/7/edit", "EMPLOYEE")
                .param("targetEmployeeId", "3"));

        ArgumentCaptor<AnnouncementRequest> sent = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(api).put(eq(ITEM_URI), sent.capture(), eq(AnnouncementDetail.class), eq(7L));
        assertThat(sent.getValue().targetType()).isEqualTo("EMPLOYEE");
        assertThat(sent.getValue().targetEmployeeId()).isEqualTo(3L);
    }

    @Test
    void update_withInvalidInput_showsEditFormAgain_withoutCallingApi() throws Exception {
        stubOptions();

        mockMvc.perform(post("/announcements/7/edit").param("title", "").param("body", "b")
                .param("category", "技術").param("targetType", "ALL"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcement-form"))
                .andExpect(model().attributeExists("errors"))
                .andExpect(model().attribute("announcementId", 7L)); //編集のまま(作成の画面には、ならない)

        verify(api, never()).put(any(String.class), any(), any(Class.class), any());
    }

    @Test
    void update_whenApiReturnsBadRequest_showsApiMessage_asEditForm() throws Exception {
        stubOptions();
        when(api.put(eq(ITEM_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class), eq(7L)))
                .thenThrow(new BadRequestException("指定した社員が存在しません"));

        mockMvc.perform(validPost("/announcements/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcement-form"))
                .andExpect(model().attribute("errorMessage", "指定した社員が存在しません"))
                .andExpect(model().attribute("announcementId", 7L));
    }

    @Test
    void update_whenApiReturnsNotFound_showsNotFoundView() throws Exception {
        when(api.put(eq(ITEM_URI), any(AnnouncementRequest.class), eq(AnnouncementDetail.class), eq(7L)))
                .thenThrow(new NotFoundException("お知らせが見つかりません"));

        mockMvc.perform(validPost("/announcements/7/edit"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }
}

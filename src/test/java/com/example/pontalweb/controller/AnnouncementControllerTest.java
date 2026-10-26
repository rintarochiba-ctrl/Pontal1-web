package com.example.pontalweb.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.pontalweb.client.AnnouncementDetail;
import com.example.pontalweb.client.AnnouncementItem;
import com.example.pontalweb.client.AnnouncementListResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.exception.UnauthorizedException;

//AnnouncementController(お知らせ一覧)のテスト。PontalのAPIは、疑似のもの(Mock)にして、
//「APIに渡す値」「画面(Model)に渡す値」「APIのエラーが、正しい画面になること」を確認する
@WebMvcTest(AnnouncementController.class)
@AutoConfigureMockMvc(addFilters = false) //認証のフィルターは外す(ログインが必要かは、SecurityConfigTestで確認)
class AnnouncementControllerTest {

    private static final String URI = "/api/announcements?scope={scope}&page={page}&size={size}";
    private static final String URI_WITH_CATEGORY = URI + "&category={category}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PontalApiClient api;

    private AnnouncementItem item(long id, String title) {
        return new AnnouncementItem(id, title, "技術", false, "総務部", "ALL", "全員", "2026-10-22T10:30:00");
    }

    private AnnouncementListResponse response(int totalCount, AnnouncementItem... items) {
        return new AnnouncementListResponse(List.of(items), totalCount, null, null);
    }

    // ---------- APIに渡す値 ----------

    @Test
    void list_withoutParams_callsApiWithScopeAll_page0_size10_andNoCategory() throws Exception {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenReturn(response(1, item(1, "お知らせ")));

        mockMvc.perform(get("/announcements"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcements"));

        verify(api).get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10));
    }

    @Test
    void list_passesScopeAndPageToApi() throws Exception {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("mine"), eq(2), eq(10)))
                .thenReturn(response(25, item(21, "A")));

        mockMvc.perform(get("/announcements").param("scope", "mine").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("scope", "mine"))
                .andExpect(model().attribute("page", 2));

        verify(api).get(eq(URI), eq(AnnouncementListResponse.class), eq("mine"), eq(2), eq(10));
    }

    @Test
    void list_withCategory_passesCategoryToApi() throws Exception {
        when(api.get(eq(URI_WITH_CATEGORY), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10), eq("技術")))
                .thenReturn(response(1, item(1, "技術のお知らせ")));

        mockMvc.perform(get("/announcements").param("category", "技術"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("category", "技術"));

        verify(api).get(eq(URI_WITH_CATEGORY), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10), eq("技術"));
    }

    @Test
    void list_withEmptyCategory_doesNotSendCategoryToApi() throws Exception {
        //絞り込みの「すべて」を選ぶと、?category= (空)で送られてくる。この場合、APIには category を渡さない
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenReturn(response(0));

        mockMvc.perform(get("/announcements").param("category", ""))
                .andExpect(status().isOk());

        verify(api).get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10));
        verify(api, never()).get(eq(URI_WITH_CATEGORY), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10), eq(""));
    }

    @Test
    void list_withBlankCategory_doesNotSendCategoryToApi() throws Exception {
        //スペースだけの文字も、「何も選んでいない」として扱う(APIに渡すと、種類が正しくないと、400になってしまう)
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenReturn(response(0));

        mockMvc.perform(get("/announcements").param("category", "   "))
                .andExpect(status().isOk());

        verify(api).get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10));
        verify(api, never()).get(eq(URI_WITH_CATEGORY), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10), eq("   "));
    }

    // ---------- 画面(Model)に渡す値 ----------

    @Test
    void list_putsApiResultIntoModel() throws Exception {
        List<AnnouncementItem> items = List.of(item(1, "A"), item(2, "B"));
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenReturn(new AnnouncementListResponse(items, 2, null, null));

        mockMvc.perform(get("/announcements"))
                .andExpect(model().attribute("announcements", items))
                .andExpect(model().attribute("totalCount", 2))
                .andExpect(model().attribute("page", 0))
                .andExpect(model().attribute("scope", "all"))
                .andExpect(model().attribute("category", ""));
    }

    //総ページ数は、切り上げ(1ページ10件)。0件は、0ページ
    @ParameterizedTest
    @CsvSource({ "0, 0", "1, 1", "9, 1", "10, 1", "11, 2", "20, 2", "21, 3", "100, 10", "101, 11" })
    void list_totalPages_isRoundedUp(int totalCount, int expectedPages) throws Exception {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenReturn(response(totalCount));

        mockMvc.perform(get("/announcements"))
                .andExpect(model().attribute("totalPages", expectedPages));
    }

    @Test
    void list_putsPageNumbersAndPrevNextIntoModel() throws Exception {
        //全25件 = 3ページ。いまは2ページ目(page=1)。画面に出す番号は1始まりなので、いまのページは 2
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(1), eq(10)))
                .thenReturn(new AnnouncementListResponse(List.of(item(11, "A")), 25, 2, 0));

        mockMvc.perform(get("/announcements").param("page", "1"))
                .andExpect(model().attribute("pageNumbers", List.of(1, 2, 3)))
                .andExpect(model().attribute("prev", 0))
                .andExpect(model().attribute("next", 2));
    }

    @Test
    void list_pageNumbers_showEllipsis() throws Exception {
        //全100件 = 10ページ。5ページ目(page=4)を開く → 1 … 4 5 6 … 10(「…」は -1)
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(4), eq(10)))
                .thenReturn(new AnnouncementListResponse(List.of(item(41, "A")), 100, 5, 3));

        mockMvc.perform(get("/announcements").param("page", "4"))
                .andExpect(model().attribute("pageNumbers", List.of(1, -1, 4, 5, 6, -1, 10)));
    }

    // ---------- 詳細 ----------

    private AnnouncementDetail detail(long id) {
        return new AnnouncementDetail(id, "年末調整のお願い", "対応依頼", true, "総務部", "DEPARTMENT", "開発部",
                "2026-10-22T10:30:00", "本文です", "2026-10-22T10:30:00", true, false);
    }

    @Test
    void detail_callsApiWithId_andPutsAnnouncementIntoModel() throws Exception {
        AnnouncementDetail detail = detail(7);
        when(api.get(eq("/api/announcements/{id}"), eq(AnnouncementDetail.class), eq(7L))).thenReturn(detail);

        mockMvc.perform(get("/announcements/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcement-detail"))
                .andExpect(model().attribute("announcement", detail));

        verify(api).get(eq("/api/announcements/{id}"), eq(AnnouncementDetail.class), eq(7L));
    }

    @Test
    void detail_whenNotFound_showsNotFoundView() throws Exception {
        //見られないお知らせも、存在しないお知らせも、APIは同じ404を返す(存在を教えない)
        when(api.get(eq("/api/announcements/{id}"), eq(AnnouncementDetail.class), eq(99L)))
                .thenThrow(new NotFoundException("お知らせが見つかりません"));

        mockMvc.perform(get("/announcements/99"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void detail_withNonNumericId_showsBadRequestView() throws Exception {
        mockMvc.perform(get("/announcements/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));

        verifyNoInteractions(api);
    }

    // ---------- 削除 ----------

    private static final String DELETE_URI = "/api/announcements/{id}";

    @Test
    void delete_callsApiDelete_redirectsToList_withFlashMessage() throws Exception {
        mockMvc.perform(post("/announcements/7/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/announcements"))
                .andExpect(flash().attribute("successMessage", "削除しました"));

        verify(api).delete(eq(DELETE_URI), eq(7L));
    }

    @Test
    void delete_whenNotAllowed_showsForbiddenView_withoutSuccessMessage() throws Exception {
        //システム管理者以外の削除は、APIが403を返す。「削除しました」は、出さない
        doThrow(new ForbiddenException("この操作を行う権限がありません")).when(api).delete(eq(DELETE_URI), eq(7L));

        mockMvc.perform(post("/announcements/7/delete"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"))
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void delete_whenNotFound_showsNotFoundView() throws Exception {
        doThrow(new NotFoundException("お知らせが見つかりません")).when(api).delete(eq(DELETE_URI), eq(7L));

        mockMvc.perform(post("/announcements/7/delete"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void delete_whenApiServerFails_showsServerErrorView() throws Exception {
        doThrow(new ApiServerException("PontalのAPIに接続できませんでした")).when(api).delete(eq(DELETE_URI), eq(7L));

        mockMvc.perform(post("/announcements/7/delete"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    // ---------- APIのエラー ----------

    @Test
    void list_whenApiReturnsForbidden_showsForbiddenView() throws Exception {
        //一般の社員が ?scope=manage を開いた。権限の判断はAPIが行い、画面側は、403の画面にするだけ
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("manage"), eq(0), eq(10)))
                .thenThrow(new ForbiddenException("この操作を行う権限がありません"));

        mockMvc.perform(get("/announcements").param("scope", "manage"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    void list_whenApiServerFails_showsServerErrorView() throws Exception {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));

        mockMvc.perform(get("/announcements"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    @Test
    void list_whenLoginExpired_redirectsToLogin() throws Exception {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("all"), eq(0), eq(10)))
                .thenThrow(new UnauthorizedException("ログインが必要です"));

        mockMvc.perform(get("/announcements"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));
    }
}

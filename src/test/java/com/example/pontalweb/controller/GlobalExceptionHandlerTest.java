package com.example.pontalweb.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

//GlobalExceptionHandlerのテスト。Controllerが例外を投げたとき、正しいHTTPステータスと、エラー画面の名前になることを確認する
//(確認用の画面 SamplePageController のURLを使う)
@WebMvcTest(SamplePageController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void forbiddenException_returns403View_withMessage() throws Exception {
        mockMvc.perform(get("/sample/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"))
                .andExpect(model().attribute("message", "この画面を見る権限がありません(確認用)"));
    }

    @Test
    void notFoundException_returns404View_withMessage() throws Exception {
        mockMvc.perform(get("/sample/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("message", "社員が見つかりません(確認用)"));
    }

    @Test
    void unexpectedException_returns500View_withoutDetails() throws Exception {
        mockMvc.perform(get("/sample/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"))
                //内部のエラーの内容は、画面に出さない(messageは、空)
                .andExpect(model().attribute("message", (Object) null));
    }

    @Test
    void unknownUrl_returns404View_notAn500() throws Exception {
        //存在しないURLは、Spring MVCの例外(NoResourceFoundException)になる。500ではなく、404の画面になること
        mockMvc.perform(get("/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void typeMismatchInPath_returns400View() throws Exception {
        //数字(Long)のところに、文字を入れた
        mockMvc.perform(get("/sample/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }

    @Test
    void validPathVariable_isNotAnError() throws Exception {
        mockMvc.perform(get("/sample/items/5"))
                .andExpect(status().isOk());
    }
}

package com.example.pontalweb.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.exception.UnauthorizedException;

//GlobalExceptionHandlerのテスト。Controllerが例外を投げたとき、正しいHTTPステータスと、エラー画面の名前になることを確認する
//テストの中だけで使う、例外を投げるControllerを用意して、わざと例外を起こす
//(認証のフィルターは、ここでは外す。権限のテストは、SecurityConfigTestで行う)
@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Controller
    static class ThrowingController {
        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new ForbiddenException("この画面を見る権限がありません(確認用)");
        }

        @GetMapping("/test/notfound")
        String notFound() {
            throw new NotFoundException("社員が見つかりません(確認用)");
        }

        @GetMapping("/test/bad-request")
        String badRequest() {
            throw new BadRequestException("メールアドレスの形式が正しくありません");
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new ConflictException("既に登録されているメールアドレスです");
        }

        @GetMapping("/test/api-server-error")
        String apiServerError() {
            throw new ApiServerException("PontalのAPIがエラーを返しました status=500");
        }

        @GetMapping("/test/server-error")
        String serverError() {
            throw new IllegalStateException("予期しないエラー(確認用)");
        }

        @GetMapping("/test/unauthorized")
        String unauthorized() {
            throw new UnauthorizedException("ログインが必要です");
        }

        //アップロードされたファイルが、上限より大きい(上限は、1MB = 1048576 バイト)
        @GetMapping("/test/too-large")
        String tooLarge() {
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(1048576);
        }

        //URLの数字(Long)の部分に、文字を入れると、400になる
        @GetMapping("/test/items/{id}")
        String item(@PathVariable Long id) {
            return "test-view";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void maxUploadSizeExceeded_returns413View_withLimitInMessage() throws Exception {
        mockMvc.perform(get("/test/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(view().name("error/400"))
                .andExpect(model().attribute("message",
                        "ファイルが大きすぎます(上限: 1MB)。小さいファイルを選んで、もう一度お試しください"));
    }

    @Test
    void forbiddenException_returns403View_withMessage() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"))
                .andExpect(model().attribute("message", "この画面を見る権限がありません(確認用)"));
    }

    @Test
    void notFoundException_returns404View_withMessage() throws Exception {
        mockMvc.perform(get("/test/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("message", "社員が見つかりません(確認用)"));
    }

    @Test
    void badRequestException_returns400View_withApiMessage() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"))
                .andExpect(model().attribute("message", "メールアドレスの形式が正しくありません"));
    }

    @Test
    void conflictException_returns409View_withApiMessage() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/409"))
                .andExpect(model().attribute("message", "既に登録されているメールアドレスです"));
    }

    @Test
    void apiServerException_returns500View_withoutMessage() throws Exception {
        mockMvc.perform(get("/test/api-server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"))
                //内部の事情(statusなど)は、画面に出さない
                .andExpect(model().attribute("message", (Object) null));
    }

    @Test
    void unexpectedException_returns500View_withoutDetails() throws Exception {
        mockMvc.perform(get("/test/server-error"))
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
        mockMvc.perform(get("/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }

    @Test
    void validPathVariable_isNotAnError() throws Exception {
        mockMvc.perform(get("/test/items/5"))
                .andExpect(status().isOk());
    }

    // ---------- ログインの有効期限切れ ----------

    @Test
    void unauthorizedException_redirectsToLogin_withExpiredFlag() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void unauthorizedException_invalidatesSession_soOldTokenIsNotReused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accessToken", "old-token");

        mockMvc.perform(get("/test/unauthorized").session(session))
                .andExpect(status().is3xxRedirection());

        //セッションが無効にされている(中のトークンも、使えなくなる)
        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
    }
}

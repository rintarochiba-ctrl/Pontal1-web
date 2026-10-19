package com.example.pontalweb.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;

//GlobalExceptionHandlerのテスト(PontalのAPIのエラーを表す例外)。
//テスト用のControllerに、わざと例外を投げさせて、エラー画面の名前とステータスを確認する
@WebMvcTest(GlobalExceptionHandlerApiErrorTest.ThrowingController.class)
@Import(GlobalExceptionHandlerApiErrorTest.ThrowingController.class)
class GlobalExceptionHandlerApiErrorTest {

    //テストの中だけで使う、例外を投げるController
    @Controller
    static class ThrowingController {
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
    }

    @Autowired
    private MockMvc mockMvc;

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
}

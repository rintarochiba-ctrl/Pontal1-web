package com.example.pontalweb.controller;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

//SamplePageControllerのテスト。Controllerだけを起動して、画面の名前と、Modelの中身(権限)を確認する
//(MockMvcは、JSPの中身までは表示しないので、JSPの見た目は、ブラウザや、起動したアプリで確認する)
@WebMvcTest(SamplePageController.class)
class SamplePageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sample_returnsSampleView() throws Exception {
        mockMvc.perform(get("/sample"))
                .andExpect(status().isOk())
                .andExpect(view().name("sample"));
    }

    @Test
    void sample_withoutRole_isMember() throws Exception {
        mockMvc.perform(get("/sample"))
                .andExpect(model().attribute("loginUser", hasProperty("systemAdmin", is(false))))
                .andExpect(model().attribute("loginUser", hasProperty("hrAdmin", is(false))));
    }

    @Test
    void sample_roleAdmin_isSystemAdmin() throws Exception {
        mockMvc.perform(get("/sample").param("role", "admin"))
                .andExpect(model().attribute("loginUser", hasProperty("systemAdmin", is(true))))
                .andExpect(model().attribute("loginUser", hasProperty("hrAdmin", is(false))));
    }

    @Test
    void sample_roleHr_isHrAdmin() throws Exception {
        mockMvc.perform(get("/sample").param("role", "hr"))
                .andExpect(model().attribute("loginUser", hasProperty("hrAdmin", is(true))))
                .andExpect(model().attribute("loginUser", hasProperty("systemAdmin", is(false))));
    }

    @Test
    void sample_unknownRole_fallsBackToMember() throws Exception {
        mockMvc.perform(get("/sample").param("role", "unknown"))
                .andExpect(model().attribute("loginUser", hasProperty("systemAdmin", is(false))))
                .andExpect(model().attribute("loginUser", hasProperty("hrAdmin", is(false))));
    }
}

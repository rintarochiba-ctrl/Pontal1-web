package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

//HomeControllerのテスト。あいさつが、時刻に合わせて変わることを、決まった時刻(Clock.fixed)で確認する
class HomeControllerTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    //指定した日本時間(時:分)で、止まっているClock
    private Clock tokyoAt(String time) {
        return Clock.fixed(Instant.parse("2026-10-20T" + time + ":00+09:00"), TOKYO);
    }

    // ---------- あいさつの境目 ----------

    @ParameterizedTest
    @CsvSource({
            "00:00, おはようございます",
            "06:30, おはようございます",
            "10:59, おはようございます", //11時の1分前
            "11:00, こんにちは",         //境目
            "17:59, こんにちは",         //18時の1分前
            "18:00, こんばんは",         //境目
            "23:59, こんばんは" })
    void greeting_changesByTimeOfDay(String time, String expected) {
        Model model = new ExtendedModelMap();

        new HomeController(tokyoAt(time)).home(model);

        assertThat(model.getAttribute("greeting")).isEqualTo(expected);
    }

    @Test
    void home_returnsHomeView() {
        assertThat(new HomeController(tokyoAt("09:00")).home(new ExtendedModelMap())).isEqualTo("home");
    }

    @Test
    void greetingAt_usesHourOnly() {
        assertThat(HomeController.greetingAt(LocalTime.of(10, 59, 59))).isEqualTo("おはようございます");
        assertThat(HomeController.greetingAt(LocalTime.of(11, 0, 0))).isEqualTo("こんにちは");
    }

    // ---------- 日本時間で決まること ----------

    @Test
    void greeting_followsJapanTime_notServerTimeZone() {
        //UTCの 00:00 は、日本時間の 09:00。サーバーがUTCでも、日本時間で「おはようございます」になる
        Clock utcMidnight = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), TOKYO);
        Model model = new ExtendedModelMap();

        new HomeController(utcMidnight).home(model);

        assertThat(model.getAttribute("greeting")).isEqualTo("おはようございます");
    }

    @Test
    void greeting_atUtcAfternoon_isEveningInJapan() {
        //UTCの 09:30 は、日本時間の 18:30 → 「こんばんは」(サーバー時刻のままなら、「おはようございます」になってしまう)
        Clock utcMorning = Clock.fixed(Instant.parse("2026-10-20T09:30:00Z"), TOKYO);
        Model model = new ExtendedModelMap();

        new HomeController(utcMorning).home(model);

        assertThat(model.getAttribute("greeting")).isEqualTo("こんばんは");
    }
}

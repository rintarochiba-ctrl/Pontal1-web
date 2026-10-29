package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.example.pontalweb.client.AnnouncementItem;
import com.example.pontalweb.client.AnnouncementListResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.UnauthorizedException;

//HomeControllerのテスト。あいさつが、時刻に合わせて変わることを、決まった時刻(Clock.fixed)で確認する
//ハロワお知らせは、APIを疑似のもの(Mock)に差し替えて、Modelに入る値を確認する
class HomeControllerTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final String URI = "/api/announcements?scope={scope}&size={size}";

    private final PontalApiClient api = mock(PontalApiClient.class);

    //指定した日本時間で止まったClockと、疑似のAPIを持つ、HomeController
    private HomeController controllerAt(String time) {
        return new HomeController(tokyoAt(time), api);
    }

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

        controllerAt(time).home(model);

        assertThat(model.getAttribute("greeting")).isEqualTo(expected);
    }

    @Test
    void home_returnsHomeView() {
        assertThat(controllerAt("09:00").home(new ExtendedModelMap())).isEqualTo("home");
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

        new HomeController(utcMidnight, api).home(model);

        assertThat(model.getAttribute("greeting")).isEqualTo("おはようございます");
    }

    @Test
    void greeting_atUtcAfternoon_isEveningInJapan() {
        //UTCの 09:30 は、日本時間の 18:30 → 「こんばんは」(サーバー時刻のままなら、「おはようございます」になってしまう)
        Clock utcMorning = Clock.fixed(Instant.parse("2026-10-20T09:30:00Z"), TOKYO);
        Model model = new ExtendedModelMap();

        new HomeController(utcMorning, api).home(model);

        assertThat(model.getAttribute("greeting")).isEqualTo("こんばんは");
    }

    // ---------- ハロワお知らせ ----------

    private AnnouncementItem item(long id, String title) {
        return new AnnouncementItem(id, title, "技術", false, "総務部", "ALL", "全員", "2026-10-20T10:30:00");
    }

    private AnnouncementListResponse listOf(AnnouncementItem... items) {
        return new AnnouncementListResponse(List.of(items), items.length, null, null);
    }

    @Test
    void home_loadsMineAndEveryone_fiveEach() {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("mine"), eq(5)))
                .thenReturn(listOf(item(1, "あなた宛て")));
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("everyone"), eq(5)))
                .thenReturn(listOf(item(2, "全員向け1"), item(3, "全員向け2")));
        Model model = new ExtendedModelMap();

        controllerAt("09:00").home(model);

        assertThat((List<?>) model.getAttribute("mineAnnouncements")).hasSize(1);
        assertThat((List<?>) model.getAttribute("everyoneAnnouncements")).hasSize(2);
        assertThat(model.getAttribute("mineFailed")).isEqualTo(false);
        assertThat(model.getAttribute("everyoneFailed")).isEqualTo(false);
    }

    @Test
    void home_whenOneApiCallFails_onlyThatBoxFails_andOtherBoxStillShows() {
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("mine"), eq(5)))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("everyone"), eq(5)))
                .thenReturn(listOf(item(2, "全員向け")));
        Model model = new ExtendedModelMap();

        String view = controllerAt("09:00").home(model);

        assertThat(view).isEqualTo("home"); //ホームは、エラー画面にならず、出る
        assertThat(model.getAttribute("mineFailed")).isEqualTo(true);
        assertThat((List<?>) model.getAttribute("mineAnnouncements")).isEmpty();
        assertThat(model.getAttribute("everyoneFailed")).isEqualTo(false);
        assertThat((List<?>) model.getAttribute("everyoneAnnouncements")).hasSize(1);
    }

    @Test
    void home_whenLoginExpired_doesNotSwallowUnauthorized() {
        //ログインの期限切れ(401)は、お知らせの失敗として隠さず、共通のエラー処理(ログイン画面へ)に任せる
        when(api.get(eq(URI), eq(AnnouncementListResponse.class), eq("mine"), eq(5)))
                .thenThrow(new UnauthorizedException("ログインが必要です"));

        assertThatThrownBy(() -> controllerAt("09:00").home(new ExtendedModelMap()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void home_whenApiReturnsNothing_showsEmptyBoxes_notAnError() {
        //APIが、空のbody(null)を返しても、落ちずに、「お知らせは、ありません」の欄になる
        Model model = new ExtendedModelMap();

        controllerAt("09:00").home(model);

        assertThat((List<?>) model.getAttribute("mineAnnouncements")).isEmpty();
        assertThat(model.getAttribute("mineFailed")).isEqualTo(false);
    }
}

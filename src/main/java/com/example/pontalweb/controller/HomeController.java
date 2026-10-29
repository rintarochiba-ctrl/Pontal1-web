package com.example.pontalweb.controller;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.pontalweb.client.AnnouncementItem;
import com.example.pontalweb.client.AnnouncementListResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;

//ホーム画面。あいさつと、「ハロワお知らせ」(あなたへ / 全員向け)を出す
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    //ホームに出すお知らせの件数(「あなたへ」と「全員向け」で、それぞれ)
    private static final int ANNOUNCEMENT_SIZE = 5;
    private static final String ANNOUNCEMENT_URI = "/api/announcements?scope={scope}&size={size}";

    private final Clock clock;
    private final PontalApiClient api;

    public HomeController(Clock clock, PontalApiClient api) {
        this.clock = clock;
        this.api = api;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("greeting", greetingAt(LocalTime.now(clock)));
        //2つの欄は、別々に取る(片方のAPIが失敗しても、もう片方と、ホームの他の部分は出す)
        addAnnouncements(model, "mine", "mine");         //あなたへ(自分宛ての個人向け + 自分の部署宛て)
        addAnnouncements(model, "everyone", "everyone"); //全員向け
        return "home";
    }

    //お知らせを取って、Modelに入れる。入れる名前は、{name}Announcements(一覧)と {name}Failed(取れなかったか)
    //ログインの期限切れ(401)などは、ここでは捕まえず、共通のエラー処理に任せる(ログイン画面に戻る)
    private void addAnnouncements(Model model, String scope, String name) {
        try {
            AnnouncementListResponse response = api.get(ANNOUNCEMENT_URI, AnnouncementListResponse.class,
                    scope, ANNOUNCEMENT_SIZE);
            List<AnnouncementItem> items = response == null || response.items() == null ? List.of() : response.items();
            model.addAttribute(name + "Announcements", items);
            model.addAttribute(name + "Failed", false);
        } catch (ApiServerException e) {
            //APIに接続できない・APIが500を返した。この欄だけ、メッセージを出す
            log.warn("ホームのお知らせを取得できませんでした scope={}", scope, e);
            model.addAttribute(name + "Announcements", List.of());
            model.addAttribute(name + "Failed", true);
        }
    }

    //時刻に合わせたあいさつ(11時前: おはようございます、18時前: こんにちは、それ以降: こんばんは)
    static String greetingAt(LocalTime time) {
        int hour = time.getHour();
        return hour < 11 ? "おはようございます" : hour < 18 ? "こんにちは" : "こんばんは";
    }
}

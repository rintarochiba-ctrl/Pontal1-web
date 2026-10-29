package com.example.pontalweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.pontalweb.client.AnnouncementListResponse;
import com.example.pontalweb.client.PontalApiClient;

//お知らせ一覧の画面
@Controller
public class AnnouncementController {

    private static final int PAGE_SIZE = 10;

    private final PontalApiClient api;

    public AnnouncementController(PontalApiClient api) {
        this.api = api;
    }

    @GetMapping("/announcements")
    public String list(
            //URLに付いてきた値(?scope=mine&category=技術&page=1 など)を受け取る。何も付いていないときの初期値を決める
            @RequestParam(defaultValue = "all") String scope,    //表示の切り替え。初期値は、見られるもの全部
            @RequestParam(defaultValue = "") String category, //種類。初期値は、絞り込みなし
            @RequestParam(defaultValue = "0") int page,        //ページ。初期値は、先頭
            Model model) {

        String uri = "/api/announcements?scope={scope}&page={page}&size={size}";
        AnnouncementListResponse response;
        if (category.isBlank()) {
            //種類を選んでいない(絞り込みなし)ときの呼び出し
            response = api.get(uri, AnnouncementListResponse.class, scope, page, PAGE_SIZE);
        } else {
            //種類を選んでいるとき。URLの末尾に &category={category} を足して、その値も渡す
            response = api.get(uri + "&category={category}", AnnouncementListResponse.class, scope, page, PAGE_SIZE, category);
        }

        //画面(JSP)に渡す値。左が、JSPで使う名前
        model.addAttribute("announcements", response.items());
        model.addAttribute("totalCount", response.totalCount());
        model.addAttribute("page", page);
        int totalPages = (response.totalCount() + PAGE_SIZE - 1) / PAGE_SIZE; //総ページ数(切り上げ)
        model.addAttribute("totalPages", totalPages);
        //ページ送りに出す数字(「…」は -1)。画面に出す番号は1始まりなので、page + 1 を渡す
        model.addAttribute("pageNumbers", PageNumbers.of(page + 1, totalPages));
        //「‹」「›」を押せるか。ないときは null(社員一覧と同じ)
        model.addAttribute("prev", response.prev());
        model.addAttribute("next", response.next());
        model.addAttribute("scope", scope);
        model.addAttribute("category", category);

        return "announcements"; //画面の名前(JSPのファイル名から、.jsp を取ったもの)
    }
}

package com.example.pontalweb.controller;

import java.time.Clock;
import java.time.LocalTime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

//ホーム画面。あいさつと、クイックアクセスを出す
@Controller
public class HomeController {

    private final Clock clock;

    public HomeController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("greeting", greetingAt(LocalTime.now(clock)));
        return "home";
    }

    //時刻に合わせたあいさつ(11時前: おはようございます、18時前: こんにちは、それ以降: こんばんは)
    static String greetingAt(LocalTime time) {
        int hour = time.getHour();
        return hour < 11 ? "おはようございます" : hour < 18 ? "こんにちは" : "こんばんは";
    }
}

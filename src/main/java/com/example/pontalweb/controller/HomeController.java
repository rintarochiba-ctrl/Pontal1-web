package com.example.pontalweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

//ホーム画面。いまは、ログイン後の移動先として、簡単な画面だけを置く(デザインに合わせた画面は、ホームのPRで作る)
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}

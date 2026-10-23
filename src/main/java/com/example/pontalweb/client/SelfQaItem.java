package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//自己紹介のQ&Aの、1組分(質問と答え)。社員詳細の selfQa(JSONの文字)の、1要素にあたる
//JSP(EL式)は getXxx() で値を取るので、getter を持たせる
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelfQaItem(String question, String answer) {

    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
}

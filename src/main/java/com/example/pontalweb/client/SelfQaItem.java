package com.example.pontalweb.client;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

//自己紹介のQ&Aの、1組分(質問と答え)。社員詳細の selfQa(JSONの文字)の、1要素にあたる
//JSP(EL式)は getXxx() で値を取るので、getter を持たせる
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelfQaItem(String question, String answer) {

    //1人が登録できる、Q&Aの組の最大数
    public static final int MAX_ITEMS = 3;

    private static final ObjectMapper JSON = new ObjectMapper();

    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }

    //編集画面で入力された、質問と答え(同じ順番)を、selfQa に保存する JSON の文字にする
    //  ・質問も答えも空の組は、除く。前後の空白は、取る。最大3組
    //  ・1組もなければ null(空の文字を、保存しない)
    public static String toJson(List<String> questions, List<String> answers) {
        int size = Math.max(questions == null ? 0 : questions.size(), answers == null ? 0 : answers.size());
        List<SelfQaItem> items = new ArrayList<>();
        for (int i = 0; i < size && items.size() < MAX_ITEMS; i++) {
            String question = trimmed(questions, i);
            String answer = trimmed(answers, i);
            if (!question.isEmpty() || !answer.isEmpty()) {
                items.add(new SelfQaItem(question, answer));
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Q&AをJSONにできませんでした", e);
        }
    }

    private static String trimmed(List<String> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return "";
        }
        return values.get(index).trim();
    }
}

package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

//SelfQaItem.toJson のテスト。編集画面で入力した、質問と答えが、selfQa に保存するJSONの文字になることを確認する
//(詳細画面が、同じ形のJSONを読めること=往復できることも、あわせて確認する)
class SelfQaItemTest {

    @Test
    void buildsJsonFromQuestionsAndAnswers() {
        String json = SelfQaItem.toJson(List.of("好きな食べ物は?", "休日は?"), List.of("ラーメン", "キャンプ"));

        assertThat(json).isEqualTo(
                "[{\"question\":\"好きな食べ物は?\",\"answer\":\"ラーメン\"},{\"question\":\"休日は?\",\"answer\":\"キャンプ\"}]");
    }

    @Test
    void trimsWhitespace_andSkipsPairsWhereBothAreEmpty() {
        String json = SelfQaItem.toJson(List.of(" Q1 ", "  ", "Q3"), List.of(" A1 ", "", " "));

        assertThat(json).isEqualTo("[{\"question\":\"Q1\",\"answer\":\"A1\"},{\"question\":\"Q3\",\"answer\":\"\"}]");
    }

    @Test
    void keepsPairWithOnlyAnAnswerOrOnlyAQuestion() {
        assertThat(SelfQaItem.toJson(List.of(""), List.of("答えだけ")))
                .isEqualTo("[{\"question\":\"\",\"answer\":\"答えだけ\"}]");
        assertThat(SelfQaItem.toJson(List.of("質問だけ"), List.of("")))
                .isEqualTo("[{\"question\":\"質問だけ\",\"answer\":\"\"}]");
    }

    @Test
    void keepsAtMostThreePairs() {
        String json = SelfQaItem.toJson(List.of("1", "2", "3", "4"), List.of("a", "b", "c", "d"));

        assertThat(json).contains("\"1\"", "\"2\"", "\"3\"").doesNotContain("\"4\"");
    }

    @Test
    void returnsNull_whenNothingIsEntered() {
        assertThat(SelfQaItem.toJson(List.of("", " ", ""), List.of("", "", ""))).isNull();
        assertThat(SelfQaItem.toJson(List.of(), List.of())).isNull();
        assertThat(SelfQaItem.toJson(null, null)).isNull();
    }

    @Test
    void toleratesDifferentListLengths() {
        assertThat(SelfQaItem.toJson(List.of("Q1", "Q2"), List.of("A1")))
                .isEqualTo("[{\"question\":\"Q1\",\"answer\":\"A1\"},{\"question\":\"Q2\",\"answer\":\"\"}]");
    }

    @Test
    void escapesQuotesAndNewlines_andRoundTripsThroughEmployeeDetail() throws Exception {
        String json = SelfQaItem.toJson(List.of("\"引用\"は?"), List.of("1行目\n2行目"));

        //JSONとして正しい文字になっていて、詳細画面(EmployeeDetail)が、元の質問と答えに戻せる
        assertThat(new ObjectMapper().readTree(json).get(0).get("answer").asText()).isEqualTo("1行目\n2行目");
        EmployeeDetail detail = new EmployeeDetail(1L, "山田太郎", null, null, null, null, null, null, null, null, null,
                json, null, false, false);
        assertThat(detail.getSelfQaItems()).containsExactly(new SelfQaItem("\"引用\"は?", "1行目\n2行目"));
    }
}

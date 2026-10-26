package com.example.pontalweb.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

//PageNumbers(ページ送りに出す数字)のテスト。「…」は ELLIPSIS(-1)で表すが、ここでは、読みやすいよう、文字で書く
class PageNumbersTest {

    //「1 … 4 5 6 … 10」のような文字を、数字の並びに直す(…は ELLIPSIS)
    private List<Integer> numbers(String text) {
        return java.util.Arrays.stream(text.trim().split("\\s+"))
                .map(s -> s.equals("…") ? PageNumbers.ELLIPSIS : Integer.valueOf(s))
                .toList();
    }

    // ---------- 全10ページ ----------

    @ParameterizedTest(name = "全10ページで、いまが{0}ページ → {1}")
    @CsvSource(delimiter = '|', value = {
            "1  | 1 2 … 10",
            "2  | 1 2 3 … 10",
            "3  | 1 2 3 4 … 10",     //「2」と「…」の間が1ページだけなので、「…」にしない
            "4  | 1 2 3 4 5 … 10",
            "5  | 1 … 4 5 6 … 10",
            "6  | 1 … 5 6 7 … 10",
            "7  | 1 … 6 7 8 9 10",
            "8  | 1 … 7 8 9 10",
            "9  | 1 … 8 9 10",
            "10 | 1 … 9 10" })
    void tenPages(int current, String expected) {
        assertThat(PageNumbers.of(current, 10)).isEqualTo(numbers(expected));
    }

    // ---------- ページが少ないとき ----------

    @Test
    void noPages_isEmpty() {
        assertThat(PageNumbers.of(1, 0)).isEmpty();
    }

    @Test
    void onePage_isJustOne() {
        assertThat(PageNumbers.of(1, 1)).isEqualTo(numbers("1"));
    }

    @Test
    void twoPages_showsBoth() {
        assertThat(PageNumbers.of(1, 2)).isEqualTo(numbers("1 2"));
        assertThat(PageNumbers.of(2, 2)).isEqualTo(numbers("1 2"));
    }

    @Test
    void fivePages_showsAll_becauseNothingIsHiddenByMoreThanOnePage() {
        assertThat(PageNumbers.of(3, 5)).isEqualTo(numbers("1 2 3 4 5"));
    }

    @Test
    void sixPages_atFirstPage_hidesFourToFiveWithOneEllipsis() {
        assertThat(PageNumbers.of(1, 6)).isEqualTo(numbers("1 2 … 6"));
    }

    // ---------- 多いとき ----------

    @Test
    void manyPages_inTheMiddle() {
        assertThat(PageNumbers.of(50, 100)).isEqualTo(numbers("1 … 49 50 51 … 100"));
    }

    @Test
    void neverShowsDuplicateOrEllipsisNextToEllipsis() {
        for (int total = 1; total <= 30; total++) {
            for (int current = 1; current <= total; current++) {
                List<Integer> result = PageNumbers.of(current, total);

                //いまのページ、最初、最後は、必ず入っている
                assertThat(result).contains(1, current, total);
                //数字は、小さい順で、重複しない(「…」を除く)
                List<Integer> pages = result.stream().filter(n -> n != PageNumbers.ELLIPSIS).toList();
                assertThat(pages).isSorted().doesNotHaveDuplicates();
                //「…」が、連続しない・先頭や末尾に来ない
                assertThat(result.get(0)).isNotEqualTo(PageNumbers.ELLIPSIS);
                assertThat(result.get(result.size() - 1)).isNotEqualTo(PageNumbers.ELLIPSIS);
                for (int i = 1; i < result.size(); i++) {
                    assertThat(result.get(i) == PageNumbers.ELLIPSIS && result.get(i - 1) == PageNumbers.ELLIPSIS).isFalse();
                }
            }
        }
    }
}

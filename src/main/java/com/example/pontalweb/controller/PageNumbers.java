package com.example.pontalweb.controller;

import java.util.ArrayList;
import java.util.List;

//ページ送りに出す数字を計算する(お知らせ一覧で使う)
//  出すもの: 最初のページ、最後のページ、いまのページと、その前後1ページ
//  間が飛ぶところは、「…」にする。ただし、「…」が隠すのが1ページだけなら、「…」ではなく、その数字を出す
//  例(全10ページ): いまが5 → 1 … 4 5 6 … 10 / いまが3 → 1 2 3 4 … 10 / いまが1 → 1 2 … 10
public final class PageNumbers {

    //「…」を表す値(ページの数字には、ならない値にしてある)
    public static final int ELLIPSIS = -1;

    private PageNumbers() {
    }

    //current: いまのページ(1始まり)、total: 総ページ数 → 出す数字の並び(「…」は ELLIPSIS)
    public static List<Integer> of(int current, int total) {
        List<Integer> result = new ArrayList<>();
        int last = 0; //直前に、結果へ入れた数字(まだ何も入れていないので 0)

        //1ページ目から最後のページまで、1つずつ調べる
        for (int n = 1; n <= total; n++) {
            //【1】n を出すべきか。出すのは「最初」「最後」「いまのページの前後1ページ(いまのページも含む)」
            boolean show = n == 1 || n == total || Math.abs(n - current) <= 1;
            if (!show) {
                continue; //出さないページは、飛ばす
            }

            //n と、直前に入れた数字(last)の間が、飛んでいるか
            if (n - last == 2) {
                //【2】ちょうど1ページだけ飛んでいる → 「…」ではなく、飛んだページの数字を入れる
                result.add(n - 1);
            } else if (n - last > 2) {
                //【3】2ページ以上飛んでいる → 「…」を入れる
                result.add(ELLIPSIS);
            }

            result.add(n);
            last = n;
        }
        return result;
    }
}

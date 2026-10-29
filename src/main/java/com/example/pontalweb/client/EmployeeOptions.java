package com.example.pontalweb.client;

import java.util.ArrayList;
import java.util.List;

//「社員を選ぶプルダウン」に出す、社員の一覧を、APIから、全員分取る(給与管理と、給与明細のアップロードで使う)
public final class EmployeeOptions {

    private static final int PAGE_SIZE = 100; //社員一覧APIの、1回の最大件数
    private static final int MAX_PAGES = 20;  //ページをたどる回数の上限(nextが、いつまでも空にならなくても、無限に繰り返さないため)

    private EmployeeOptions() {
    }

    //1回100件ずつ、次のページが無くなるまで取る
    public static List<EmployeeOptionPage.Option> loadAll(PontalApiClient api) {
        List<EmployeeOptionPage.Option> options = new ArrayList<>();
        int page = 0;
        for (int i = 0; i < MAX_PAGES; i++) {
            EmployeeOptionPage result = api.get("/api/employees?page={page}&size={size}",
                    EmployeeOptionPage.class, page, PAGE_SIZE);
            if (result == null || result.items() == null) {
                break;
            }
            options.addAll(result.items());
            if (result.next() == null) {
                break;
            }
            page = result.next();
        }
        return options;
    }
}

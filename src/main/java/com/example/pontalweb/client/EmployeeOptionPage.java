package com.example.pontalweb.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//PontalのAPI GET /api/employees(社員一覧)のレスポンスのうち、「社員を選ぶプルダウン」に使う分だけ
//items は、社員の id・名前・部署。next は、次のページの番号(最後のページでは null)
//JSP(EL式)は getXxx() で値を取るので、画面で使う Option には、getter を持たせる
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmployeeOptionPage(List<Option> items, Integer next) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(Long id, String name, String department) {

        public Long getId() { return id; }
        public String getName() { return name; }

        //プルダウンに出す文字("山田太郎(開発部)")。部署が無ければ、名前だけ
        public String getLabel() {
            return department == null || department.isBlank() ? name : name + "(" + department + ")";
        }
    }
}

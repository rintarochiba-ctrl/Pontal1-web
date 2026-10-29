package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonProperty;

//PontalのAPI POST /api/employees(社員登録)に送る内容
//APIのJSONの項目名は isSystemAdmin / isHrAdmin なので、@JsonProperty で、その名前に合わせる
//joinDate は "2026-04-01" の形の文字(入力画面の type="date" が、この形で送ってくる)
public record EmployeeCreateRequest(
        String name,
        String email,
        String initialPassword,
        String joinDate,
        String department,
        String position,
        @JsonProperty("isSystemAdmin") boolean systemAdmin,
        @JsonProperty("isHrAdmin") boolean hrAdmin) {

    //record の toString は、全項目を出す。ログなどに、初期パスワードが出ないように、伏せる
    @Override
    public String toString() {
        return "EmployeeCreateRequest[name=" + name + ", email=" + email + ", initialPassword=****, joinDate=" + joinDate
                + ", department=" + department + ", position=" + position
                + ", systemAdmin=" + systemAdmin + ", hrAdmin=" + hrAdmin + "]";
    }
}

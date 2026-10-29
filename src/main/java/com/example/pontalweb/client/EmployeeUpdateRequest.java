package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonProperty;

//PontalのAPI PUT /api/employees/{id}(社員編集)に送る内容
//APIは、全項目を丸ごと置き換える。送らなかった項目は、空(NULL)になる(画像・Q&Aも、いまの値を送り直す)
//APIのJSONの項目名は isSystemAdmin / isHrAdmin なので、@JsonProperty で、その名前に合わせる
public record EmployeeUpdateRequest(
        String name,
        String gender,
        Integer age,
        String birthplace,
        String joinDate,
        String department,
        String position,
        String image,
        String bio,
        String hobby,
        String selfQa,
        @JsonProperty("isSystemAdmin") boolean systemAdmin,
        @JsonProperty("isHrAdmin") boolean hrAdmin) {
}

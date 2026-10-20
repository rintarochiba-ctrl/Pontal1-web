package com.example.pontalweb.client;

import com.example.pontalweb.model.LoginUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

//PontalのAPI GET /api/me のレスポンス(ログイン中の社員の、名前と権限)
//APIのJSONの項目名は isSystemAdmin / isHrAdmin なので、@JsonProperty で、その名前に合わせる
@JsonIgnoreProperties(ignoreUnknown = true)
public record MeResponse(
        Long employeeId,
        String name,
        @JsonProperty("isSystemAdmin") boolean systemAdmin,
        @JsonProperty("isHrAdmin") boolean hrAdmin) {

    //画面で使う、ログイン中の人の情報に変換する
    public LoginUser toLoginUser() {
        return new LoginUser(employeeId, name, systemAdmin, hrAdmin);
    }
}

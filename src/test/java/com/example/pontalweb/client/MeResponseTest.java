package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.pontalweb.model.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;

//MeResponseのテスト。PontalのAPI(GET /api/me)のJSONを、正しく読み取れることを確認する
//(JSONの項目名は isSystemAdmin / isHrAdmin。名前がずれると、権限が、すべて false になってしまう)
class MeResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesPontalApiJson() throws Exception {
        String json = "{\"employeeId\":1,\"name\":\"山田太郎\",\"isSystemAdmin\":true,\"isHrAdmin\":false}";

        MeResponse me = objectMapper.readValue(json, MeResponse.class);

        assertThat(me.employeeId()).isEqualTo(1L);
        assertThat(me.name()).isEqualTo("山田太郎");
        assertThat(me.systemAdmin()).isTrue();
        assertThat(me.hrAdmin()).isFalse();
    }

    @Test
    void parsesHrAdmin() throws Exception {
        String json = "{\"employeeId\":2,\"name\":\"佐藤花子\",\"isSystemAdmin\":false,\"isHrAdmin\":true}";

        MeResponse me = objectMapper.readValue(json, MeResponse.class);

        assertThat(me.systemAdmin()).isFalse();
        assertThat(me.hrAdmin()).isTrue();
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        //APIに項目が増えても、読み取りが壊れない
        String json = "{\"employeeId\":3,\"name\":\"鈴木一郎\",\"isSystemAdmin\":false,\"isHrAdmin\":false,\"department\":\"開発部\"}";

        MeResponse me = objectMapper.readValue(json, MeResponse.class);

        assertThat(me.name()).isEqualTo("鈴木一郎");
    }

    @Test
    void convertsToLoginUser() {
        LoginUser user = new MeResponse(1L, "山田太郎", true, true).toLoginUser();

        assertThat(user.getEmployeeId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("山田太郎");
        assertThat(user.isSystemAdmin()).isTrue();
        assertThat(user.isHrAdmin()).isTrue();
        assertThat(user.getInitial()).isEqualTo("山");
    }
}

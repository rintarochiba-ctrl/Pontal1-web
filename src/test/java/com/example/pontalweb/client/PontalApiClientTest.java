package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;

//PontalApiClientのテスト。本物のAPIにはつながず、疑似のAPIサーバー(MockRestServiceServer)を相手にする
//「トークンが付くこと」「APIのエラーが、正しい例外になること」を確認する
class PontalApiClientTest {

    private static final String BASE_URL = "http://pontal-api.test";

    private MockRestServiceServer server;
    private PontalApiClient client;
    private String token;

    //テスト用の受け取り先(JSONを、この形のクラスに変換する)
    public record Employee(Long id, String name) {
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        token = "test-token";
        //トークンは、テストでは、変数から返す(本番は、セッションから)
        client = new PontalApiClient(builder.build(), () -> token, new ObjectMapper());
    }

    // ---------- 成功(トークンが付く・JSONを受け取る) ----------

    @Test
    void get_sendsBearerToken_andParsesJson() {
        server.expect(requestTo(BASE_URL + "/api/employees/5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"id\":5,\"name\":\"山田太郎\"}", MediaType.APPLICATION_JSON));

        Employee result = client.get("/api/employees/{id}", Employee.class, 5);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.name()).isEqualTo("山田太郎");
        server.verify();
    }

    @Test
    void getWithToken_usesTheGivenToken_notTheSessionToken() {
        //ログインの直後は、トークンが、まだセッションに入っていない。渡されたトークンを、そのまま使う
        server.expect(requestTo(BASE_URL + "/api/me"))
                .andExpect(header("Authorization", "Bearer just-issued-token"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"山田太郎\"}", MediaType.APPLICATION_JSON));

        Employee result = client.getWithToken("just-issued-token", "/api/me", Employee.class);

        assertThat(result.name()).isEqualTo("山田太郎");
        server.verify();
    }

    @Test
    void getWithToken_mapsApiErrorsToExceptions() {
        server.expect(requestTo(BASE_URL + "/api/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"authentication required\"}"));

        assertThatThrownBy(() -> client.getWithToken("t", "/api/me", Employee.class))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void get_withQueryVariables_expandsThem() {
        server.expect(requestTo(BASE_URL + "/api/employees?page=2&size=20"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.get("/api/employees?page={page}&size={size}", new ParameterizedTypeReference<List<Employee>>() {}, 2, 20);

        server.verify();
    }

    @Test
    void get_parsesListWithParameterizedType() {
        server.expect(requestTo(BASE_URL + "/api/list"))
                .andRespond(withSuccess("[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]", MediaType.APPLICATION_JSON));

        List<Employee> result = client.get("/api/list", new ParameterizedTypeReference<List<Employee>>() {});

        assertThat(result).extracting(Employee::name).containsExactly("A", "B");
    }

    @Test
    void getBytes_returnsFileContent() {
        byte[] pdf = "%PDF-dummy".getBytes();
        server.expect(requestTo(BASE_URL + "/api/salary-slips/3/download"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

        assertThat(client.getBytes("/api/salary-slips/{id}/download", 3)).isEqualTo(pdf);
    }

    @Test
    void postMultipart_sendsFormWithFile_andBearerToken() {
        server.expect(requestTo(BASE_URL + "/api/salary-slips"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                //フォームの項目名と、ファイル名・中身が、送られていること
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"employeeId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"payMonth\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("filename=\"salary-slip.pdf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("%PDF-dummy")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":10,\"employeeId\":3,\"payMonth\":\"2024-09\"}"));

        org.springframework.util.LinkedMultiValueMap<String, Object> parts = new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("employeeId", "3");
        parts.add("payMonth", "2024-09");
        parts.add("file", new org.springframework.core.io.ByteArrayResource("%PDF-dummy".getBytes()) {
            @Override
            public String getFilename() {
                return "salary-slip.pdf";
            }
        });

        SalarySlipUploadResult result = client.postMultipart("/api/salary-slips", parts, SalarySlipUploadResult.class);

        assertThat(result).isEqualTo(new SalarySlipUploadResult(10L, 3L, "2024-09"));
        server.verify();
    }

    @Test
    void postMultipart_whenAlreadyExists_throwsConflict() {
        server.expect(requestTo(BASE_URL + "/api/salary-slips"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"既に2024-09分の給与明細が登録されています\"}"));

        assertThatThrownBy(() -> client.postMultipart("/api/salary-slips",
                new org.springframework.util.LinkedMultiValueMap<>(), SalarySlipUploadResult.class))
                .isInstanceOf(ConflictException.class)
                .hasMessage("既に2024-09分の給与明細が登録されています");
    }

    @Test
    void post_sendsJsonBody_andParsesResponse() {
        server.expect(requestTo(BASE_URL + "/api/employees"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"id\":null,\"name\":\"新人\"}"))
                .andRespond(withSuccess("{\"id\":9,\"name\":\"新人\"}", MediaType.APPLICATION_JSON));

        Employee result = client.post("/api/employees", new Employee(null, "新人"), Employee.class);

        assertThat(result.id()).isEqualTo(9L);
    }

    @Test
    void put_sendsJsonBody() {
        server.expect(requestTo(BASE_URL + "/api/employees/9"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"id\":9,\"name\":\"更新\"}", MediaType.APPLICATION_JSON));

        Employee result = client.put("/api/employees/{id}", new Employee(9L, "更新"), Employee.class, 9);

        assertThat(result.name()).isEqualTo("更新");
    }

    @Test
    void delete_succeedsOnNoContent() {
        server.expect(requestTo(BASE_URL + "/api/employees/9"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withNoContent());

        client.delete("/api/employees/{id}", 9);

        server.verify();
    }

    // ---------- APIのエラー → 例外 ----------

    @Test
    void status400_throwsBadRequest_withApiMessage() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"メールアドレスの形式が正しくありません\"}"));

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("メールアドレスの形式が正しくありません");
    }

    @Test
    void status401_throwsUnauthorized() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"authentication required\"}"));

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void status403_throwsForbidden_withApiMessage() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"社員を編集する権限がありません\"}"));

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("社員を編集する権限がありません");
    }

    @Test
    void status404_throwsNotFound_withApiMessage() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"社員が見つかりません\"}"));

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("社員が見つかりません");
    }

    @Test
    void status409_throwsConflict() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"既に登録されているメールアドレスです\"}"));

        assertThatThrownBy(() -> client.post("/api/x", "body", String.class))
                .isInstanceOf(ConflictException.class)
                .hasMessage("既に登録されているメールアドレスです");
    }

    @Test
    void status500_throwsApiServerException_withoutLeakingApiMessage() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withServerError().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":false,\"message\":\"内部のエラーの詳細\"}"));

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(ApiServerException.class)
                //APIの内部の事情(message)は、例外のメッセージに入れない(ステータスだけ)
                .hasMessageContaining("500")
                .hasMessageNotContaining("内部のエラーの詳細");
    }

    @Test
    void errorWithoutMessageInBody_usesDefaultMessage() {
        //Spring標準のエラーのJSON(messageがない)や、空のボディでも、既定のメッセージになる
        server.expect(requestTo(BASE_URL + "/api/a"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"timestamp\":\"2026-10-19\",\"status\":404,\"error\":\"Not Found\"}"));
        server.expect(requestTo(BASE_URL + "/api/b"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.get("/api/a", String.class))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("見つかりません");
        assertThatThrownBy(() -> client.get("/api/b", String.class))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("この操作を行う権限がありません");
    }

    @Test
    void errorWithNonJsonBody_usesDefaultMessage() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withBadRequest().contentType(MediaType.TEXT_HTML).body("<html>bad</html>"));

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("入力内容が正しくありません");
    }

    @Test
    void connectionFailure_throwsApiServerException() {
        server.expect(requestTo(BASE_URL + "/api/x"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(ApiServerException.class)
                .hasMessage("PontalのAPIに接続できませんでした");
    }

    // ---------- トークンがないとき ----------

    @Test
    void withoutToken_throwsUnauthorized_andDoesNotCallApi() {
        token = null;

        assertThatThrownBy(() -> client.get("/api/x", String.class))
                .isInstanceOf(UnauthorizedException.class);
        server.verify(); //期待するAPI呼び出しが無い=1回も呼ばれていない
    }

    @Test
    void withBlankToken_throwsUnauthorized() {
        token = "  ";

        assertThatThrownBy(() -> client.delete("/api/x"))
                .isInstanceOf(UnauthorizedException.class);
    }
}

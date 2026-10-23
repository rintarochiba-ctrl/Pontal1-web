package com.example.pontalweb.client;

import java.io.IOException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

//PontalのAPIを呼ぶ、共通のクライアント。各画面の処理は、これを使って、APIを呼ぶ
//  ・ログイン中の人のトークンを、Authorization: Bearer ... で付ける
//  ・APIがエラーを返したら、ステータスごとの例外(401→Unauthorized、403→Forbidden など)に変える
//  ・APIに接続できないときも、例外に変える
@Component
public class PontalApiClient {

    private final RestClient restClient;
    private final AccessTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public PontalApiClient(RestClient pontalRestClient, AccessTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.restClient = pontalRestClient;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    //GET(JSONを、指定した型で受け取る)。uriVariables は、uri の {名前} に、順番に入る
    //例: get("/api/employees/{id}", EmployeeDetail.class, 5)
    public <T> T get(String uri, Class<T> responseType, Object... uriVariables) {
        return call(() -> restClient.get().uri(uri, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiException)
                .body(responseType));
    }

    //GET(トークンを、呼び出す側で指定する)。ログインの途中で使う
    //ログインが成功した直後は、まだトークンがセッションに入っていないので、受け取ったトークンを、そのまま渡して、APIを呼ぶ
    public <T> T getWithToken(String accessToken, String uri, Class<T> responseType, Object... uriVariables) {
        return call(() -> restClient.get().uri(uri, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiException)
                .body(responseType));
    }

    //GET(一覧など、型の中に型がある場合。例: new ParameterizedTypeReference<List<SalarySlip>>() {})
    public <T> T get(String uri, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
        return call(() -> restClient.get().uri(uri, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiException)
                .body(responseType));
    }

    //GET(ファイルを、バイト列で受け取る。給与明細のPDF、監査ログのCSVのダウンロードで使う)
    public byte[] getBytes(String uri, Object... uriVariables) {
        return call(() -> restClient.get().uri(uri, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiException)
                .body(byte[].class));
    }

    //POST(bodyを、JSONで送る)
    public <T> T post(String uri, Object body, Class<T> responseType, Object... uriVariables) {
        return call(() -> restClient.post().uri(uri, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiException)
                .body(responseType));
    }

    //PUT(bodyを、JSONで送る)
    public <T> T put(String uri, Object body, Class<T> responseType, Object... uriVariables) {
        return call(() -> restClient.put().uri(uri, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiException)
                .body(responseType));
    }

    //DELETE(成功のときは、何も返らない)
    public void delete(String uri, Object... uriVariables) {
        call(() -> restClient.delete().uri(uri, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiException)
                .toBodilessEntity());
    }

    //Authorizationヘッダーの値。ログインしていない(トークンがない)なら、APIを呼ばずに、401として扱う
    private String bearer() {
        String token = tokenProvider.currentToken();
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("ログインしていません");
        }
        return "Bearer " + token;
    }

    //APIの呼び出しを実行する。接続できない・時間切れのときは、ApiServerExceptionにする
    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            throw new ApiServerException("PontalのAPIに接続できませんでした", e);
        }
    }

    //APIが返したエラー(ステータス400番台・500番台)を、ステータスごとの例外にする
    private void throwApiException(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String message = readMessage(response);

        switch (status) {
            case 400 -> throw new BadRequestException(orDefault(message, "入力内容が正しくありません"));
            case 401 -> throw new UnauthorizedException(orDefault(message, "ログインが必要です"));
            case 403 -> throw new ForbiddenException(orDefault(message, "この操作を行う権限がありません"));
            case 404 -> throw new NotFoundException(orDefault(message, "見つかりません"));
            case 409 -> throw new ConflictException(orDefault(message, "すでに登録されているなど、処理できません"));
            //500番台などは、APIの内部の事情なので、メッセージは、画面に出さない(ログ用に、ステータスだけを残す)
            default -> throw new ApiServerException("PontalのAPIがエラーを返しました status=" + status);
        }
    }

    //APIのエラーのJSON({"result":false,"message":"..."})から、messageを取り出す。読めなければ null
    private String readMessage(ClientHttpResponse response) {
        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            JsonNode message = node == null ? null : node.get("message");
            return message != null && message.isTextual() ? message.asText() : null;
        } catch (IOException e) {
            return null; //JSONではない、空、など
        }
    }

    private String orDefault(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message;
    }
}

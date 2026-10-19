package com.example.pontalweb.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

//PontalのAPIを呼ぶための、HTTPクライアント(RestClient)を作る設定
@Configuration
public class PontalApiConfig {

    @Bean
    public RestClient pontalRestClient(RestClient.Builder builder,
            //application.propertiesの pontal.api.base-url(環境変数 PONTAL_API_BASE_URL があれば、そちら)
            @Value("${pontal.api.base-url}") String baseUrl) {
        //Javaの標準のHTTPクライアントを使う。APIが応答しないときに、画面が固まり続けないよう、時間の上限を付ける
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)) //接続できるまで
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30)); //応答が返るまで(PDFなどの大きなファイルも考えて、長めにする)

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}

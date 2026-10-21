package com.example.pontalweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

//Cognitoを呼ぶためのクライアントを作る設定
@Configuration
public class CognitoConfig {

    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient(
            @Value("${cognito.region}") String region) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                //ログイン(InitiateAuth)は、AWSのキーなしで呼べる、公開のAPI。このアプリには、AWSのキーを、持たせない
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .build();
    }
}

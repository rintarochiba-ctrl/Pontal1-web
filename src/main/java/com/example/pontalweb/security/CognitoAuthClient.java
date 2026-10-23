package com.example.pontalweb.security;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordResetRequiredException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

//Cognitoで、メールアドレスとパスワードを確認して、アクセストークンを受け取る
//Cognitoのエラーは、LoginFailedException(理由つき)に変える。パスワードとトークンは、ログに出さない
@Component
public class CognitoAuthClient {

    private static final Logger log = LoggerFactory.getLogger(CognitoAuthClient.class);

    private final CognitoIdentityProviderClient cognito;

    //Cognitoのアプリクライアントのid(.envの COGNITO_CLIENT_ID)
    @Value("${cognito.client-id:}")
    private String clientId;

    public CognitoAuthClient(CognitoIdentityProviderClient cognito) {
        this.cognito = cognito;
    }

    //ログインして、アクセストークンを返す
    public String login(String email, String password) {
        if (clientId == null || clientId.isBlank()) {
            log.error("COGNITO_CLIENT_ID が設定されていません(.env を確認してください)");
            throw new LoginFailedException(LoginFailureReason.UNAVAILABLE);
        }

        InitiateAuthResponse response;
        try {
            response = cognito.initiateAuth(InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH) //メールとパスワードで認証する方式
                    .clientId(clientId)
                    .authParameters(Map.of("USERNAME", email, "PASSWORD", password))
                    .build());
        } catch (NotAuthorizedException | UserNotFoundException e) {
            //パスワードの間違いと、アカウントがないことは、区別せずに、同じ理由にする
            throw new LoginFailedException(LoginFailureReason.CREDENTIALS, e);
        } catch (PasswordResetRequiredException e) {
            throw new LoginFailedException(LoginFailureReason.PASSWORD_RESET_REQUIRED, e);
        } catch (TooManyRequestsException e) {
            throw new LoginFailedException(LoginFailureReason.TOO_MANY_REQUESTS, e);
        } catch (SdkException e) {
            //設定のミス(クライアントidが違う)、通信できない、など。原因は、ログに残す
            log.error("Cognitoのログインの呼び出しに失敗しました: {}", e.getClass().getSimpleName());
            throw new LoginFailedException(LoginFailureReason.UNAVAILABLE, e);
        }

        //初期パスワードでの、初めてのログイン。新しいパスワードの設定が必要(別の画面で対応する)
        if (response.challengeName() == ChallengeNameType.NEW_PASSWORD_REQUIRED) {
            throw new LoginFailedException(LoginFailureReason.NEW_PASSWORD_REQUIRED);
        }

        AuthenticationResultType result = response.authenticationResult();
        if (result == null || result.accessToken() == null) {
            log.error("Cognitoから、アクセストークンが返りませんでした challenge={}", response.challengeName());
            throw new LoginFailedException(LoginFailureReason.UNAVAILABLE);
        }
        return result.accessToken();
    }
}

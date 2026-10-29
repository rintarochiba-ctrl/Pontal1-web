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
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordResetRequiredException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest;
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

        //初期パスワードでの、初めてのログイン。新しいパスワードの設定が必要
        //Cognitoが渡してきたセッションを持たせて、設定画面に移動する(NewPasswordController)
        if (response.challengeName() == ChallengeNameType.NEW_PASSWORD_REQUIRED) {
            throw new NewPasswordRequiredException(email, response.session());
        }

        AuthenticationResultType result = response.authenticationResult();
        if (result == null || result.accessToken() == null) {
            log.error("Cognitoから、アクセストークンが返りませんでした challenge={}", response.challengeName());
            throw new LoginFailedException(LoginFailureReason.UNAVAILABLE);
        }
        return result.accessToken();
    }

    //初回ログインの、新しいパスワードを、Cognitoに送って設定する
    //成功したら、何も返さない(設定のあと、新しいパスワードで、普通にログインし直してもらう)
    public void completeNewPassword(String email, String newPassword, String cognitoSession) {
        try {
            cognito.respondToAuthChallenge(RespondToAuthChallengeRequest.builder()
                    .clientId(clientId)
                    .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                    .session(cognitoSession)
                    .challengeResponses(Map.of("USERNAME", email, "NEW_PASSWORD", newPassword))
                    .build());
        } catch (InvalidPasswordException | InvalidParameterException e) {
            //パスワードのルール(文字数・文字の種類)を満たしていない。入力し直せば、通る可能性がある
            throw new NewPasswordFailedException(
                    "パスワードが、ルールを満たしていません。文字数を増やし、大文字・小文字・数字・記号を組み合わせてください", false, e);
        } catch (NotAuthorizedException | CodeMismatchException | ExpiredCodeException e) {
            //Cognitoのセッションの有効期限(数分)が切れた。最初のログインから、やり直す
            throw new NewPasswordFailedException("時間が経ちすぎました。もう一度、初期パスワードでログインしてください", true, e);
        } catch (TooManyRequestsException e) {
            throw new NewPasswordFailedException("試行が多すぎます。しばらくしてからもう一度お試しください", false, e);
        } catch (SdkException e) {
            log.error("Cognitoの新しいパスワード設定の呼び出しに失敗しました: {}", e.getClass().getSimpleName());
            throw new NewPasswordFailedException("設定できませんでした。しばらくしてからもう一度お試しください", false, e);
        }
    }
}

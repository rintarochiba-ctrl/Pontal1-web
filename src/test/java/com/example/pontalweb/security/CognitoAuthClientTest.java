package com.example.pontalweb.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordResetRequiredException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

//CognitoAuthClientのテスト。本物のCognitoにはつながず、Cognitoのクライアントを、疑似のもの(Mock)に差し替える
@ExtendWith(MockitoExtension.class)
class CognitoAuthClientTest {

    @Mock
    private CognitoIdentityProviderClient cognito;

    private CognitoAuthClient client;

    @BeforeEach
    void setUp() {
        client = new CognitoAuthClient(cognito);
        ReflectionTestUtils.setField(client, "clientId", "test-client-id");
    }

    private void cognitoReturnsToken(String token) {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(InitiateAuthResponse.builder()
                .authenticationResult(AuthenticationResultType.builder().accessToken(token).build())
                .build());
    }

    private void assertFailsWith(LoginFailureReason reason) {
        assertThatThrownBy(() -> client.login("a@example.com", "pass"))
                .isInstanceOf(LoginFailedException.class)
                .extracting(e -> ((LoginFailedException) e).getReason())
                .isEqualTo(reason);
    }

    @Test
    void login_returnsAccessToken() {
        cognitoReturnsToken("access-token-123");

        assertThat(client.login("a@example.com", "pass")).isEqualTo("access-token-123");
    }

    @Test
    void login_sendsClientIdAndCredentials_withPasswordAuthFlow() {
        cognitoReturnsToken("t");

        client.login("a@example.com", "pass");

        ArgumentCaptor<InitiateAuthRequest> captor = ArgumentCaptor.forClass(InitiateAuthRequest.class);
        verify(cognito).initiateAuth(captor.capture());
        InitiateAuthRequest request = captor.getValue();
        assertThat(request.authFlow()).isEqualTo(AuthFlowType.USER_PASSWORD_AUTH);
        assertThat(request.clientId()).isEqualTo("test-client-id");
        assertThat(request.authParameters()).containsEntry("USERNAME", "a@example.com");
        assertThat(request.authParameters()).containsEntry("PASSWORD", "pass");
    }

    @Test
    void wrongPassword_isMappedToCredentials() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("Incorrect username or password.").build());

        assertFailsWith(LoginFailureReason.CREDENTIALS);
    }

    @Test
    void unknownUser_isMappedToCredentials_soAccountExistenceIsNotRevealed() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User does not exist.").build());

        //パスワードの間違いと、同じ理由になる(どちらが違うかを、教えない)
        assertFailsWith(LoginFailureReason.CREDENTIALS);
    }

    @Test
    void passwordResetRequired_isMapped() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(PasswordResetRequiredException.builder().message("reset").build());

        assertFailsWith(LoginFailureReason.PASSWORD_RESET_REQUIRED);
    }

    @Test
    void tooManyRequests_isMapped() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(TooManyRequestsException.builder().message("slow down").build());

        assertFailsWith(LoginFailureReason.TOO_MANY_REQUESTS);
    }

    @Test
    void newPasswordChallenge_isMappedToNewPasswordRequired() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(InitiateAuthResponse.builder()
                .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                .session("challenge-session")
                .build());

        assertFailsWith(LoginFailureReason.NEW_PASSWORD_REQUIRED);
    }

    @Test
    void communicationFailure_isMappedToUnavailable() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(SdkClientException.create("Unable to execute HTTP request"));

        assertFailsWith(LoginFailureReason.UNAVAILABLE);
    }

    @Test
    void wrongClientId_isMappedToUnavailable() {
        //アプリクライアントのidが違う(設定のミス)。利用者のパスワードの間違いとは、区別する
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("User pool client does not exist.").build());

        assertFailsWith(LoginFailureReason.UNAVAILABLE);
    }

    @Test
    void responseWithoutToken_isMappedToUnavailable() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(InitiateAuthResponse.builder().build());

        assertFailsWith(LoginFailureReason.UNAVAILABLE);
    }

    @Test
    void blankClientId_failsWithoutCallingCognito() {
        ReflectionTestUtils.setField(client, "clientId", "");

        assertFailsWith(LoginFailureReason.UNAVAILABLE);
        verifyNoInteractions(cognito);
    }

    @Test
    void loginNeverCallsCognito_whenClientIdIsMissing_evenOnRetry() {
        ReflectionTestUtils.setField(client, "clientId", null);

        assertFailsWith(LoginFailureReason.UNAVAILABLE);
        verify(cognito, never()).initiateAuth(any(InitiateAuthRequest.class));
    }
}

package com.example.pontalweb.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.example.pontalweb.client.MeResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.exception.UnauthorizedException;

//CognitoAuthenticationProviderのテスト。CognitoとPontalのAPIは、疑似のもの(Mock)にして、ログインの流れと、権限の変換を確認する
@ExtendWith(MockitoExtension.class)
class CognitoAuthenticationProviderTest {

    @Mock
    private CognitoAuthClient cognitoAuthClient;

    @Mock
    private PontalApiClient pontalApiClient;

    @InjectMocks
    private CognitoAuthenticationProvider provider;

    private Authentication input(String email, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(email, password);
    }

    private void apiReturns(MeResponse me) {
        when(pontalApiClient.getWithToken(eq("token-abc"), eq("/api/me"), eq(MeResponse.class))).thenReturn(me);
    }

    private List<String> roles(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private void assertFailsWith(Authentication input, LoginFailureReason reason) {
        assertThatThrownBy(() -> provider.authenticate(input))
                .isInstanceOf(LoginFailedException.class)
                .extracting(e -> ((LoginFailedException) e).getReason())
                .isEqualTo(reason);
    }

    // ---------- 成功と、権限の変換 ----------

    @Test
    void generalUser_getsOnlyRoleUser() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        apiReturns(new MeResponse(3L, "鈴木一郎", false, false));

        Authentication result = provider.authenticate(input("a@example.com", "pass"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(roles(result)).containsExactly("ROLE_USER");
    }

    @Test
    void hrAdmin_getsRoleHrAdmin() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        apiReturns(new MeResponse(2L, "佐藤花子", false, true));

        assertThat(roles(provider.authenticate(input("a@example.com", "pass"))))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_HR_ADMIN");
    }

    @Test
    void systemAdmin_getsRoleSystemAdmin() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        apiReturns(new MeResponse(1L, "山田太郎", true, false));

        assertThat(roles(provider.authenticate(input("a@example.com", "pass"))))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_SYSTEM_ADMIN");
    }

    @Test
    void userWithBothPermissions_getsBothRoles() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        apiReturns(new MeResponse(1L, "山田太郎", true, true));

        assertThat(roles(provider.authenticate(input("a@example.com", "pass"))))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_HR_ADMIN", "ROLE_SYSTEM_ADMIN");
    }

    @Test
    void principal_holdsLoginUserAndAccessToken() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        apiReturns(new MeResponse(1L, "山田太郎", true, false));

        PontalPrincipal principal = (PontalPrincipal) provider.authenticate(input("a@example.com", "pass")).getPrincipal();

        assertThat(principal.getAccessToken()).isEqualTo("token-abc");
        assertThat(principal.getLoginUser().getEmployeeId()).isEqualTo(1L);
        assertThat(principal.getLoginUser().getName()).isEqualTo("山田太郎");
        assertThat(principal.getLoginUser().isSystemAdmin()).isTrue();
        assertThat(principal.getLoginUser().isHrAdmin()).isFalse();
    }

    @Test
    void principalToString_doesNotContainToken() {
        PontalPrincipal principal = new PontalPrincipal(new MeResponse(1L, "山田太郎", false, false).toLoginUser(), "secret-token");

        assertThat(principal.toString()).doesNotContain("secret-token");
    }

    @Test
    void email_isTrimmedBeforeLogin() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        apiReturns(new MeResponse(3L, "鈴木一郎", false, false));

        provider.authenticate(input("  a@example.com  ", "pass"));

        verify(cognitoAuthClient).login("a@example.com", "pass");
    }

    // ---------- 失敗 ----------

    @Test
    void blankEmailOrPassword_failsWithCredentials_withoutCallingCognito() {
        assertFailsWith(input("", "pass"), LoginFailureReason.CREDENTIALS);
        assertFailsWith(input("a@example.com", ""), LoginFailureReason.CREDENTIALS);
        assertFailsWith(input("   ", "pass"), LoginFailureReason.CREDENTIALS);
        verifyNoInteractions(cognitoAuthClient, pontalApiClient);
    }

    @Test
    void cognitoFailure_isPropagated_andApiIsNotCalled() {
        when(cognitoAuthClient.login("a@example.com", "bad"))
                .thenThrow(new LoginFailedException(LoginFailureReason.CREDENTIALS));

        assertFailsWith(input("a@example.com", "bad"), LoginFailureReason.CREDENTIALS);
        verifyNoInteractions(pontalApiClient);
    }

    @Test
    void employeeNotRegisteredInPontal_isMappedToNotRegistered() {
        //Cognitoには、いるが、Pontalの社員として、登録されていない(APIは、401を返す)
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        when(pontalApiClient.getWithToken(eq("token-abc"), eq("/api/me"), eq(MeResponse.class)))
                .thenThrow(new UnauthorizedException("未登録"));

        assertFailsWith(input("a@example.com", "pass"), LoginFailureReason.NOT_REGISTERED);
    }

    @Test
    void apiForbiddenOrNotFound_isMappedToNotRegistered() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        when(pontalApiClient.getWithToken(eq("token-abc"), eq("/api/me"), eq(MeResponse.class)))
                .thenThrow(new ForbiddenException("x"))
                .thenThrow(new NotFoundException("x"));

        assertFailsWith(input("a@example.com", "pass"), LoginFailureReason.NOT_REGISTERED);
        assertFailsWith(input("a@example.com", "pass"), LoginFailureReason.NOT_REGISTERED);
    }

    @Test
    void apiDown_isMappedToUnavailable() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        when(pontalApiClient.getWithToken(eq("token-abc"), eq("/api/me"), eq(MeResponse.class)))
                .thenThrow(new ApiServerException("PontalのAPIに接続できませんでした"));

        assertFailsWith(input("a@example.com", "pass"), LoginFailureReason.UNAVAILABLE);
    }

    @Test
    void emptyApiResponse_isMappedToUnavailable() {
        when(cognitoAuthClient.login("a@example.com", "pass")).thenReturn("token-abc");
        apiReturns(new MeResponse(null, null, false, false));

        assertFailsWith(input("a@example.com", "pass"), LoginFailureReason.UNAVAILABLE);
    }

    // ---------- 対応する認証の種類 ----------

    @Test
    void supports_onlyUsernamePasswordAuthentication() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(String.class)).isFalse();
    }
}

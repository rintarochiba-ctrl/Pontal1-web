package com.example.pontalweb.security;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.example.pontalweb.client.MeResponse;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.exception.UnauthorizedException;
import com.example.pontalweb.model.LoginUser;

//ログインの認証を行う。Spring Securityが、ログインフォームの入力を、ここに渡してくる
//  1. Cognitoで、メールアドレスとパスワードを確認して、アクセストークンをもらう
//  2. そのトークンで、PontalのAPI(GET /api/me)を呼んで、名前と権限を取る
//  3. 権限(isSystemAdmin、isHrAdmin)を、Spring Securityの権限(ROLE_...)に変えて、ログイン成功とする
@Component
public class CognitoAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(CognitoAuthenticationProvider.class);

    private final CognitoAuthClient cognitoAuthClient;
    private final PontalApiClient pontalApiClient;

    public CognitoAuthenticationProvider(CognitoAuthClient cognitoAuthClient, PontalApiClient pontalApiClient) {
        this.cognitoAuthClient = cognitoAuthClient;
        this.pontalApiClient = pontalApiClient;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String email = authentication.getName() == null ? "" : authentication.getName().trim();
        Object credentials = authentication.getCredentials();
        String password = credentials == null ? "" : credentials.toString();
        if (email.isEmpty() || password.isEmpty()) {
            throw new LoginFailedException(LoginFailureReason.CREDENTIALS);
        }

        String accessToken = cognitoAuthClient.login(email, password);
        LoginUser loginUser = fetchLoginUser(accessToken);

        //権限。ROLE_ から始まる名前にする(SecurityConfigの hasRole("HR_ADMIN") は、ROLE_HR_ADMIN を探す)
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (loginUser.isHrAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_HR_ADMIN"));
        }
        if (loginUser.isSystemAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                new PontalPrincipal(loginUser, accessToken), null, authorities);
    }

    //PontalのAPIで、ログイン中の社員の情報を取る
    private LoginUser fetchLoginUser(String accessToken) {
        MeResponse me;
        try {
            me = pontalApiClient.getWithToken(accessToken, "/api/me", MeResponse.class);
        } catch (UnauthorizedException | ForbiddenException | NotFoundException e) {
            //Cognitoにはいるが、Pontalの社員として登録されていない(または、無効になっている)
            throw new LoginFailedException(LoginFailureReason.NOT_REGISTERED, e);
        } catch (ApiServerException e) {
            log.error("ログイン中の社員情報(/api/me)を取得できませんでした: {}", e.getMessage());
            throw new LoginFailedException(LoginFailureReason.UNAVAILABLE, e);
        }
        if (me == null || me.employeeId() == null) {
            throw new LoginFailedException(LoginFailureReason.UNAVAILABLE);
        }
        return me.toLoginUser();
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

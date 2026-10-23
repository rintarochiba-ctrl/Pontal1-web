package com.example.pontalweb.security;

import java.security.Principal;

import com.example.pontalweb.model.LoginUser;

//ログインに成功した人の情報。誰か(LoginUser)と、PontalのAPIを呼ぶためのアクセストークンを持つ
public class PontalPrincipal implements Principal {

    private final LoginUser loginUser;
    private final String accessToken;

    public PontalPrincipal(LoginUser loginUser, String accessToken) {
        this.loginUser = loginUser;
        this.accessToken = accessToken;
    }

    public LoginUser getLoginUser() {
        return loginUser;
    }

    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public String getName() {
        return String.valueOf(loginUser.getEmployeeId());
    }

    //トークンは、ログなどに出ないよう、文字列にしたときは、含めない
    @Override
    public String toString() {
        return "PontalPrincipal[employeeId=" + loginUser.getEmployeeId() + "]";
    }
}

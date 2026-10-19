package com.example.pontalweb.client;

//セッションに入れる値の名前(キー)。入れる側(ログイン)と、取り出す側で、同じ名前を使うため、ここにまとめる
public final class SessionKeys {

    //Cognitoから受け取った、アクセストークン
    public static final String ACCESS_TOKEN = "accessToken";

    //ログイン中の人の情報(LoginUser)
    public static final String LOGIN_USER = "loginUser";

    private SessionKeys() {
    }
}

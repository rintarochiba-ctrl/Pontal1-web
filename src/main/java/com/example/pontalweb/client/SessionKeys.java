package com.example.pontalweb.client;

//セッションに入れる値の名前(キー)。入れる側(ログイン)と、取り出す側で、同じ名前を使うため、ここにまとめる
public final class SessionKeys {

    //Cognitoから受け取った、アクセストークン
    public static final String ACCESS_TOKEN = "accessToken";

    //ログイン中の人の情報(LoginUser)
    public static final String LOGIN_USER = "loginUser";

    //初回ログインの、新しいパスワード設定の途中で使う値(どのメールアドレスの人か、Cognitoから受け取ったセッション)
    //設定が終われば、すぐ消す。パスワードそのものは、セッションに入れない
    public static final String NEW_PASSWORD_EMAIL = "newPasswordEmail";
    public static final String NEW_PASSWORD_COGNITO_SESSION = "newPasswordCognitoSession";

    private SessionKeys() {
    }
}

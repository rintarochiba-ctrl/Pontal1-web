package com.example.pontalweb.security;

import java.util.Arrays;
import java.util.Optional;

//ログインに失敗した理由。code は、ログイン画面のURL(/login?error=コード)に使い、message は、画面に出す文
public enum LoginFailureReason {

    //メールアドレスかパスワードが違う。どちらが違うかは、教えない(アカウントの有無を、推測されないため)
    CREDENTIALS("credentials", "メールアドレスまたはパスワードが違います"),
    //初回ログイン。新しいパスワードの設定画面に移動するので、このメッセージは、ふだんは出ない
    NEW_PASSWORD_REQUIRED("newpassword", "初回ログインのパスワード設定が必要です"),
    //新しいパスワードの設定の途中で、時間が経ちすぎた(Cognitoのセッションが切れた)
    NEW_PASSWORD_EXPIRED("newpasswordexpired", "時間が経ちすぎました。もう一度、初期パスワードでログインしてください"),
    PASSWORD_RESET_REQUIRED("passwordreset", "パスワードの再設定が必要です。管理者に問い合わせてください"),
    TOO_MANY_REQUESTS("toomany", "ログインの試行が多すぎます。しばらくしてからもう一度お試しください"),
    //Cognitoにはいるが、Pontalに社員として登録されていない
    NOT_REGISTERED("notregistered", "このアカウントは、Pontalに登録されていません。管理者に問い合わせてください"),
    //Cognito・PontalのAPIに接続できない、設定が足りない、など。画面には、詳しい理由を出さない
    UNAVAILABLE("unavailable", "ログインできませんでした。しばらくしてからもう一度お試しください");

    private final String code;
    private final String message;

    LoginFailureReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    //URLのコードから、理由を探す(知らないコードなら、空)
    public static Optional<LoginFailureReason> fromCode(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
    }
}

package com.example.pontalweb.security;

//初期パスワードでの初めてのログインで、新しいパスワードの設定が必要なことを表す例外
//Cognitoが渡してくる「セッション」(新しいパスワードを送るときに、同じ人であることを示す合言葉)を持つ
//LoginFailureHandlerが、これを受け取って、新しいパスワードの設定画面に移動する
public class NewPasswordRequiredException extends LoginFailedException {

    private final String email;
    private final String cognitoSession;

    public NewPasswordRequiredException(String email, String cognitoSession) {
        super(LoginFailureReason.NEW_PASSWORD_REQUIRED);
        this.email = email;
        this.cognitoSession = cognitoSession;
    }

    public String getEmail() {
        return email;
    }

    public String getCognitoSession() {
        return cognitoSession;
    }
}

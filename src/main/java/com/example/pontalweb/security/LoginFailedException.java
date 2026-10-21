package com.example.pontalweb.security;

import org.springframework.security.core.AuthenticationException;

//ログインに失敗したことを表す例外。理由(LoginFailureReason)を持ち、LoginFailureHandlerが、ログイン画面に戻す
public class LoginFailedException extends AuthenticationException {

    private final LoginFailureReason reason;

    public LoginFailedException(LoginFailureReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public LoginFailedException(LoginFailureReason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public LoginFailureReason getReason() {
        return reason;
    }
}

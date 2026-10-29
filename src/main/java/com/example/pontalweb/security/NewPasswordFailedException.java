package com.example.pontalweb.security;

//新しいパスワードの設定に失敗したことを表す例外。message は、そのまま画面に出せる文
//sessionExpired が true のときは、入力し直しても通らない(最初のログインからやり直す)
public class NewPasswordFailedException extends RuntimeException {

    private final boolean sessionExpired;

    public NewPasswordFailedException(String message, boolean sessionExpired, Throwable cause) {
        super(message, cause);
        this.sessionExpired = sessionExpired;
    }

    public boolean isSessionExpired() {
        return sessionExpired;
    }
}

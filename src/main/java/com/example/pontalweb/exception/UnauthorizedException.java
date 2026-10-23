package com.example.pontalweb.exception;

//ログインしていない、またはトークンの期限が切れている(401)ことを表す例外
//PontalのAPIが401を返したとき、または、セッションにトークンがないときに投げる(ログイン画面への移動は、ログインの実装で行う)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

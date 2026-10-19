package com.example.pontalweb.exception;

//すでに存在するなどで、処理できない(409)ことを表す例外。PontalのAPIが409を返したときに投げる
//(例: 登録しようとしたメールアドレスが、すでに使われている)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

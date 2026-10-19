package com.example.pontalweb.exception;

//権限がない(403)ことを表す例外。PontalのAPIが403を返したときや、画面側で権限がないと分かったときに投げる
//GlobalExceptionHandlerが、403のエラー画面に変換する
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

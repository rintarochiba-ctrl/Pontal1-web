package com.example.pontalweb.exception;

//見つからない(404)ことを表す例外。PontalのAPIが404を返したとき(社員や明細が存在しないなど)に投げる
//GlobalExceptionHandlerが、404のエラー画面に変換する
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

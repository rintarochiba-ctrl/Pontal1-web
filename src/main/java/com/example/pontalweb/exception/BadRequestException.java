package com.example.pontalweb.exception;

//入力内容が正しくない(400)ことを表す例外。PontalのAPIが400を返したときに投げる
//message には、APIが返した理由(例: 「メールアドレスの形式が正しくありません」)が入る。フォームの画面では、これを、画面に出す
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

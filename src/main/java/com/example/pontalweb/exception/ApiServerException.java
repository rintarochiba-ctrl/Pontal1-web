package com.example.pontalweb.exception;

//PontalのAPIの側でエラー(500など)が起きた、またはAPIに接続できなかったことを表す例外
//message は、画面に出さない。原因は、ログに残す(GlobalExceptionHandlerが、500の画面にする)
public class ApiServerException extends RuntimeException {

    public ApiServerException(String message) {
        super(message);
    }

    public ApiServerException(String message, Throwable cause) {
        super(message, cause);
    }
}

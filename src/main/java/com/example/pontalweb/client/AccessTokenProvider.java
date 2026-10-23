package com.example.pontalweb.client;

//いまログインしている人の、アクセストークン(PontalのAPIを呼ぶための通行証)を返す
//実際の取り出し方(セッションから)は、SessionAccessTokenProviderが行う。テストでは、決まった文字列を返すものに差し替えられる
@FunctionalInterface
public interface AccessTokenProvider {

    //トークン。ログインしていなければ null
    String currentToken();
}

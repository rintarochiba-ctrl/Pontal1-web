package com.example.pontalweb.client;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

//いま処理しているリクエストの、セッションから、アクセストークンを取り出す
//(リクエストごとに、そのリクエストを送ってきた人のセッションが使われるので、他の人のトークンと混ざらない)
@Component
public class SessionAccessTokenProvider implements AccessTokenProvider {

    @Override
    public String currentToken() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null; //リクエストの外(バッチなど)では、トークンがない
        }
        Object token = attributes.getAttribute(SessionKeys.ACCESS_TOKEN, RequestAttributes.SCOPE_SESSION);
        return token instanceof String s ? s : null;
    }
}

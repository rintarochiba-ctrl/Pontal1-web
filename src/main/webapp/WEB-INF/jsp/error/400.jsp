<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="リクエストが正しくありません">
  <div class="card error-card">
    <div class="error-code">400</div>
    <h2>リクエストが正しくありません</h2>
    <%-- APIから返ったメッセージがあれば、それを出す。なければ、決まった文を出す --%>
    <p class="text-muted"><c:out value="${message}" default="URLや入力内容を確認して、もう一度お試しください"/></p>
    <a class="btn btn-primary" href="<c:url value='/'/>">ホームへ戻る</a>
  </div>
</t:layout>

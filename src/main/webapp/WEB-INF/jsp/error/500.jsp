<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="エラーが発生しました">
  <div class="card error-card">
    <div class="error-code">500</div>
    <h2>エラーが発生しました</h2>
    <%-- APIから返ったメッセージがあれば、それを出す。なければ、決まった文を出す --%>
    <p class="text-muted"><c:out value="${message}" default="しばらくしてから、もう一度お試しください。解決しない場合は、管理者に問い合わせてください"/></p>
    <a class="btn btn-primary" href="<c:url value='/'/>">ホームへ戻る</a>
  </div>
</t:layout>

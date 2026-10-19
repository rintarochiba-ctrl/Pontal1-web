<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="ページが見つかりません">
  <div class="card error-card">
    <div class="error-code">404</div>
    <h2>ページが見つかりません</h2>
    <%-- APIから返ったメッセージがあれば、それを出す。なければ、決まった文を出す --%>
    <p class="text-muted"><c:out value="${message}" default="お探しのページは、存在しないか、移動した可能性があります"/></p>
    <a class="btn btn-primary" href="<c:url value='/'/>">ホームへ戻る</a>
  </div>
</t:layout>

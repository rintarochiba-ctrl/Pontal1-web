<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="社員一覧" active="employees">

  <%-- 削除のあとに、1回だけ出すお知らせ(Controllerが、redirect と一緒に渡す) --%>
  <c:if test="${not empty successMessage}">
    <div class="alert alert-success"><c:out value="${successMessage}"/></div>
  </c:if>

  <div class="list-toolbar">
    <%-- 検索は、GETで送る(URLが /employees?keyword=開発 になる)。GETは、CSRFの札が要らない --%>
    <form class="search-form" action="<c:url value='/employees'/>" method="get">
      <input type="text" name="keyword" class="form-control" placeholder="名前・メール・部署で検索"
             value="<c:out value='${keyword}'/>">
      <button type="submit" class="btn">検索</button>
    </form>
    <%-- システム管理者だけに出す(登録画面は、別のチケットで作る) --%>
    <c:if test="${loginUser.systemAdmin}">
      <a class="btn btn-primary" href="<c:url value='/employees/new'/>">＋ 新規登録</a>
    </c:if>
  </div>
  <p class="text-muted">全 <c:out value="${totalCount}"/> 名</p>

  <div class="employee-grid">
    <c:forEach var="e" items="${employees}">
      <div class="employee-card">
        <span class="employee-avatar"><c:out value="${e.initial}"/></span>
        <div class="employee-name"><c:out value="${e.name}"/></div>
        <div class="employee-sub"><c:out value="${e.department}"/></div>
        <div class="employee-sub"><c:out value="${e.position}"/></div>
        <a class="employee-link" href="<c:url value='/employees/${e.id}'/>">詳細プロフィール →</a>
      </div>
    </c:forEach>
  </div>

  <%-- 2ページ以上あるときだけ出す。「‹ 1 2 3 ›」の形。prev / next は、ないとき null --%>
  <c:if test="${totalPages > 1}">
    <div class="pagination">
      <c:choose>
        <c:when test="${not empty prev}">
          <a href="<c:url value='/employees'><c:param name='page' value='${prev}'/><c:param name='keyword' value='${keyword}'/></c:url>">‹</a>
        </c:when>
        <c:otherwise><span class="disabled">‹</span></c:otherwise>
      </c:choose>

      <%-- ページの番号を、全部並べる(画面に出す番号は1始まり、URLのpageは0始まり) --%>
      <c:forEach var="i" begin="0" end="${totalPages - 1}">
        <c:choose>
          <c:when test="${i == page}">
            <span class="current"><c:out value="${i + 1}"/></span>
          </c:when>
          <c:otherwise>
            <a href="<c:url value='/employees'><c:param name='page' value='${i}'/><c:param name='keyword' value='${keyword}'/></c:url>"><c:out value="${i + 1}"/></a>
          </c:otherwise>
        </c:choose>
      </c:forEach>

      <c:choose>
        <c:when test="${not empty next}">
          <a href="<c:url value='/employees'><c:param name='page' value='${next}'/><c:param name='keyword' value='${keyword}'/></c:url>">›</a>
        </c:when>
        <c:otherwise><span class="disabled">›</span></c:otherwise>
      </c:choose>
    </div>
  </c:if>

</t:layout>

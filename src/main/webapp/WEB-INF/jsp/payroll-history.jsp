<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="給与明細のアップロード履歴" active="payroll">

  <div class="card">
    <div class="history-head">
      <h2>アップロード履歴</h2>
      <a class="btn" href="<c:url value='/payroll'/>">給与管理へ戻る</a>
    </div>

    <c:choose>
      <c:when test="${not empty rows}">
        <table class="table">
          <thead>
            <tr>
              <th>アップロード日時</th>
              <th>対象の社員</th>
              <th>対象年月</th>
              <th>アップロードした人</th>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="r" items="${rows}">
              <tr>
                <td><c:out value="${r.uploadedAtText}"/></td>
                <%-- 社員の名前を押すと、その人の給与明細の一覧(給与管理)に移動する --%>
                <td><a href="<c:url value='/payroll'><c:param name='employeeId' value='${r.employeeId}'/></c:url>"><c:out value="${r.employeeName}"/></a></td>
                <td><c:out value="${r.payMonthText}"/></td>
                <td><c:out value="${r.uploadedByName}"/></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </c:when>
      <c:otherwise>
        <p class="text-muted">アップロードの履歴は、まだありません</p>
      </c:otherwise>
    </c:choose>

    <%-- ページ送り。次のページがあるとき、前のページがあるときだけ、リンクを出す(現在のページは、1始まりで出す) --%>
    <c:if test="${hasPrev or hasNext}">
      <div class="pagination">
        <c:if test="${hasPrev}">
          <a href="<c:url value='/payroll/history'><c:param name='page' value='${page - 1}'/></c:url>">‹ 前へ</a>
        </c:if>
        <span class="current"><c:out value="${page + 1}"/></span>
        <c:if test="${hasNext}">
          <a href="<c:url value='/payroll/history'><c:param name='page' value='${page + 1}'/></c:url>">次へ ›</a>
        </c:if>
      </div>
    </c:if>
  </div>

</t:layout>

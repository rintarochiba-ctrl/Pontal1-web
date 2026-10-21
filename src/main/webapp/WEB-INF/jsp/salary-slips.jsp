<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="給与明細" active="salary">

  <div class="card">
    <c:choose>
      <c:when test="${not empty slips}">
        <table class="table">
          <thead>
            <tr>
              <th>支給年月</th>
              <th class="col-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="s" items="${slips}">
              <tr>
                <td><strong><c:out value="${s.payMonthText}"/></strong></td>
                <td class="col-actions">
                  <%-- ファイル名に使うので、対象年月も、一緒に渡す(c:param が、文字を安全な形に直す) --%>
                  <a class="btn btn-sm" href="<c:url value='/salary-slips/${s.id}/download'><c:param name='payMonth' value='${s.payMonth}'/></c:url>">PDF</a>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </c:when>
      <c:otherwise>
        <p class="text-muted">給与明細は、まだありません</p>
      </c:otherwise>
    </c:choose>
  </div>

</t:layout>

<%@ tag description="給与明細の一覧の表(支給年月と、PDFのボタン)。給与明細と、給与管理の、2つの画面で使う" pageEncoding="UTF-8" body-content="empty" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="slips" required="true" type="java.util.List" description="SalarySlipSummary のリスト" %>
<%@ attribute name="emptyMessage" required="false" description="1件もないときに出す文" %>
<%--
  使い方:
    <%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
    <t:salary-slip-table slips="${slips}" emptyMessage="給与明細は、まだありません"/>
--%>
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
    <p class="text-muted"><c:out value="${emptyMessage}"/></p>
  </c:otherwise>
</c:choose>

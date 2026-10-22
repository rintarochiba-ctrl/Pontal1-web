<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="監査ログ" active="auditlog">

  <nav class="tabs">
    <a class="tab" href="<c:url value='/audit-logs'/>">操作履歴</a>
    <a class="tab active" href="<c:url value='/audit-logs/exports'/>">週ごとのCSV</a>
  </nav>

<div class="card">
    <form class="exports-form" action="<c:url value='/audit-logs/exports'/>" method="get">
      <label for="year">年</label>
      <select id="year" name="year" class="form-control">
        <c:forEach var="y" items="${years}">
          <option value="${y}" ${y == year ? 'selected' : ''}>${y}</option>
        </c:forEach>
      </select>
      <label for="month">月</label>
      <select id="month" name="month" class="form-control">
        <c:forEach var="m" begin="1" end="12">
          <option value="${m}" ${m == month ? 'selected' : ''}>${m}</option>
        </c:forEach>
      </select>
      <button type="submit" class="btn btn-primary">表示</button>
    </form>

    <c:choose>
      <c:when test="${empty batches}">
        <p class="text-muted">この月の週ごとのCSVは、まだありません</p>
      </c:when>
      <c:otherwise>
        <table class="table">
          <thead>
            <tr>
              <th>期間</th>
              <th class="col-center">件数</th>
              <th class="col-center">状態</th>
              <th class="col-center">操作</th>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="b" items="${batches}">
              <tr>
                <td><c:out value="${b.periodText}"/></td>
                <td class="col-center">
                  <c:choose>
                    <c:when test="${b.recordCount != null}">${b.recordCount}件</c:when>
                    <c:otherwise>-</c:otherwise>
                  </c:choose>
                </td>
                <td class="col-center">
                  <c:choose>
                    <c:when test="${b.status == 'COMPLETED'}"><span class="badge badge-success">完了</span></c:when>
                    <c:when test="${b.status == 'PROCESSING'}"><span class="badge badge-warning">処理中</span></c:when>
                    <c:otherwise><span class="badge badge-danger">失敗</span></c:otherwise>
                  </c:choose>
                </td>
                <td class="col-center">
                  <c:choose>
                    <c:when test="${b.status == 'COMPLETED'}">
                        <a class="btn btn-sm" href="<c:url value='/audit-logs/exports/${b.batchId}/download'>
                            <c:param name="start" value="${b.periodStart}"/>
                            <c:param name="end" value="${b.periodEnd}"/>
                        </c:url>">CSV</a>
                    </c:when>
                    <c:otherwise>-</c:otherwise>
                  </c:choose>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </c:otherwise>
    </c:choose>
  </div>

</t:layout>

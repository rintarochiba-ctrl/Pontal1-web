<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="給与管理" active="payroll">

  <%-- ① 社員の選択。選んで「表示」を押すと、GETで、?employeeId=... に移動する(年月の絞り込みは、リセットされる) --%>
  <div class="card">
    <form class="payroll-form" action="<c:url value='/payroll'/>" method="get">
      <label for="employeeId">社員</label>
      <select id="employeeId" name="employeeId" class="form-control payroll-select" required>
        <option value="">選択してください</option>
        <c:forEach var="e" items="${options}">
          <option value="${e.id}" ${e.id == employeeId ? 'selected' : ''}><c:out value="${e.label}"/></option>
        </c:forEach>
      </select>
      <button type="submit" class="btn btn-primary">表示</button>
    </form>
  </div>

  <c:choose>
    <c:when test="${empty employeeId}">
      <p class="text-muted mt-16">社員を選択してください</p>
    </c:when>
    <c:otherwise>
      <div class="card">
        <%-- ③ 年月の絞り込み。社員は、隠しの欄で引き継ぐ。年は、その人の明細にある年だけ出す --%>
        <c:if test="${hasSlips}">
          <form class="payroll-form payroll-filter" action="<c:url value='/payroll'/>" method="get">
            <input type="hidden" name="employeeId" value="${employeeId}">
            <label for="year">年</label>
            <select id="year" name="year" class="form-control payroll-select-sm">
              <option value="">すべて</option>
              <c:forEach var="y" items="${years}">
                <option value="${y}" ${y == year ? 'selected' : ''}>${y}年</option>
              </c:forEach>
            </select>
            <label for="month">月</label>
            <select id="month" name="month" class="form-control payroll-select-sm">
              <option value="">すべて</option>
              <c:forEach var="m" begin="1" end="12">
                <%-- 2桁の文字("01"〜"12")にして、APIの年月の形("2024-09")と、そろえる --%>
                <c:set var="mm" value="${m < 10 ? '0' : ''}${m}"/>
                <option value="${mm}" ${mm == month ? 'selected' : ''}>${m}月</option>
              </c:forEach>
            </select>
            <button type="submit" class="btn">絞り込む</button>
          </form>
        </c:if>

        <%-- ② 一覧(給与明細の画面と、同じ表) --%>
        <t:salary-slip-table slips="${slips}"
                             emptyMessage="${hasSlips ? '該当する給与明細は、ありません' : 'この社員の給与明細は、まだありません'}"/>
      </div>
    </c:otherwise>
  </c:choose>

</t:layout>

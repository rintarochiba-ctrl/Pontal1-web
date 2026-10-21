<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="給与明細のアップロード" active="payroll">

  <div class="card upload-card">
    <h2>給与明細のアップロード</h2>

    <c:if test="${not empty errorMessage}">
      <div class="alert alert-error"><c:out value="${errorMessage}"/></div>
    </c:if>

    <%-- ファイルを送るフォームは、enctype="multipart/form-data" が必要(これがないと、ファイルの中身が送られない) --%>
    <%-- value は、失敗して戻ってきたとき、選んだ社員・年月を残すため(form は、Controllerが渡す。初回は無い)。ファイルだけは、選び直してもらう --%>
    <form action="<c:url value='/payroll/upload'/>" method="post" enctype="multipart/form-data">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">

      <div class="form-group">
        <label for="employeeId">社員</label>
        <select id="employeeId" name="employeeId" class="form-control" required>
          <option value="">選択してください</option>
          <c:forEach var="e" items="${options}">
            <option value="${e.id}" ${e.id == form.employeeId ? 'selected' : ''}><c:out value="${e.label}"/></option>
          </c:forEach>
        </select>
      </div>

      <div class="form-group">
        <label for="payMonth">対象年月</label>
        <%-- type="month" は、ブラウザが、年月の選び方を出し、"2024-09" の形で送る。
             対応していないブラウザでは、ふつうの入力欄になるので、pattern で、形を確かめる --%>
        <input id="payMonth" name="payMonth" type="month" class="form-control" required
               pattern="\d{4}-(0[1-9]|1[0-2])" placeholder="2024-09"
               value="<c:out value='${form.payMonth}'/>">
      </div>

      <div class="form-group">
        <label for="file">PDFファイル(<c:out value="${maxFileSize}"/> まで)</label>
        <input id="file" name="file" type="file" class="form-control" accept=".pdf,application/pdf" required>
      </div>

      <div class="form-group">
        <label class="check-label">
          <input type="checkbox" name="overwrite" value="true" ${form.overwrite ? 'checked' : ''}>
          すでに登録されている場合は、上書きする
        </label>
      </div>

      <div class="upload-actions">
        <a class="btn" href="<c:url value='/payroll'/>">キャンセル</a>
        <button type="submit" class="btn btn-primary">アップロード</button>
      </div>
    </form>
  </div>

</t:layout>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- お知らせの作成・編集。同じフォームを使う。announcementId が空なら作成、入っていれば編集 --%>
<t:layout title="${empty announcementId ? 'お知らせの作成' : 'お知らせの編集'}" active="home">

  <%-- 送信先と、キャンセルの戻り先。作成は /announcements/new に送って、一覧へ戻る。編集は /announcements/{id}/edit に送って、詳細へ戻る --%>
  <c:choose>
    <c:when test="${empty announcementId}">
      <c:url var="formAction" value="/announcements/new"/>
      <c:url var="cancelUrl" value="/announcements"/>
    </c:when>
    <c:otherwise>
      <c:url var="formAction" value="/announcements/${announcementId}/edit"/>
      <c:url var="cancelUrl" value="/announcements/${announcementId}"/>
    </c:otherwise>
  </c:choose>

  <div class="card form-card">
    <%-- APIが返した入力のまちがい(画面のチェックでは見つけられなかったもの)は、フォームの上に出す --%>
    <c:if test="${not empty errorMessage}">
      <div class="alert alert-error"><c:out value="${errorMessage}"/></div>
    </c:if>

    <form action="${formAction}" method="post">
      <%-- CSRF対策の確認用トークン。POSTのフォームには、必ず付ける --%>
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">

      <div class="form-group">
        <label for="title">タイトル <span class="required">*</span></label>
        <input id="title" type="text" name="title" class="form-control${not empty errors.title ? ' is-invalid' : ''}"
               value="<c:out value='${form.title}'/>" placeholder="タイトルを入力してください">
        <%-- タイトルにまちがいがあるときだけ、メッセージを出す --%>
        <c:if test="${not empty errors.title}"><div class="form-error"><c:out value="${errors.title}"/></div></c:if>
      </div>

      <div class="form-group">
        <label for="category">種類 <span class="required">*</span></label>
        <div class="flex">
          <select id="category" name="category" class="form-control category-select${not empty errors.category ? ' is-invalid' : ''}">
            <option value="">選んでください</option>
            <%-- 選択肢は、Controllerが渡した categories。いま選んでいるものに、selected を付ける --%>
            <c:forEach var="c" items="${categories}">
              <option value="<c:out value='${c}'/>" ${form.category == c ? 'selected' : ''}><c:out value="${c}"/></option>
            </c:forEach>
          </select>
          <label class="check-label"><input type="checkbox" name="important" value="true" ${form.important ? 'checked' : ''}> 重要</label>
        </div>
        <c:if test="${not empty errors.category}"><div class="form-error"><c:out value="${errors.category}"/></div></c:if>
      </div>

      <div class="form-group">
        <label>宛先 <span class="required">*</span></label>
        <div class="target-options">
          <%-- いま選んでいる宛先(form.targetType)のラジオに、checked を付ける。3つとも、同じ形 --%>
          <label><input type="radio" name="targetType" value="ALL" ${form.targetType == 'ALL' ? 'checked' : ''}> 全員</label>

          <label><input type="radio" name="targetType" value="DEPARTMENT" ${form.targetType == 'DEPARTMENT' ? 'checked' : ''}> 部署</label>
          <%-- 部署を選んだときだけ、プルダウンを使える。使えないときは、disabled を付ける(最初の状態は、サーバーで決める。切り替えは、下のスクリプトが行う) --%>
          <select id="targetDepartment" name="targetDepartment" class="form-control target-select"
                  ${form.targetType == 'DEPARTMENT' ? '' : 'disabled'}>
            <option value="">選んでください</option>
            <c:forEach var="d" items="${departments}">
              <option value="<c:out value='${d}'/>" ${form.targetDepartment == d ? 'selected' : ''}><c:out value="${d}"/></option>
            </c:forEach>
          </select>

          <label><input type="radio" name="targetType" value="EMPLOYEE" ${form.targetType == 'EMPLOYEE' ? 'checked' : ''}> 個人</label>
          <select id="targetEmployeeId" name="targetEmployeeId" class="form-control target-select"
                  ${form.targetType == 'EMPLOYEE' ? '' : 'disabled'}>
            <option value="">選んでください</option>
            <c:forEach var="e" items="${employees}">
              <%-- 社員の番号(e.id)が、フォームの宛先の社員(form.targetEmployeeId)と同じなら、selected を付ける --%>
              <option value="${e.id}" ${form.targetEmployeeId == e.id ? 'selected' : ''}><c:out value="${e.name}"/>(<c:out value="${e.department}"/>)</option>
            </c:forEach>
          </select>
        </div>
        <c:if test="${not empty errors.target}"><div class="form-error"><c:out value="${errors.target}"/></div></c:if>
      </div>

      <div class="form-group">
        <label for="body">本文 <span class="required">*</span></label>
        <%-- textarea の中身は、タグの間に書く(value属性は、使えない)。文字は <c:out> を通す --%>
        <textarea id="body" name="body" rows="10" class="form-control${not empty errors.body ? ' is-invalid' : ''}"><c:out value="${form.body}"/></textarea>
        <c:if test="${not empty errors.body}"><div class="form-error"><c:out value="${errors.body}"/></div></c:if>
      </div>

      <div class="form-actions">
        <a class="btn" href="${cancelUrl}">キャンセル</a>
        <button type="submit" class="btn btn-primary">保存</button>
      </div>
    </form>
  </div>

  <script>
    //宛先のラジオを切り替えたとき、選んだものに合うプルダウンだけを、使える状態にする
    //(使えない状態のプルダウンの値は、送信されない)
    (function () {
      var dept = document.getElementById('targetDepartment');
      var emp = document.getElementById('targetEmployeeId');
      function update() {
        var checked = document.querySelector('input[name="targetType"]:checked');
        var type = checked ? checked.value : 'ALL';
        dept.disabled = type !== 'DEPARTMENT';
        emp.disabled = type !== 'EMPLOYEE';
      }
      document.querySelectorAll('input[name="targetType"]').forEach(function (radio) {
        radio.addEventListener('change', update);
      });
      update();
    })();
  </script>

</t:layout>

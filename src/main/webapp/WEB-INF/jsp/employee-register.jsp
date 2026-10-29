<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="新規社員登録" active="employees">

  <div class="card form-card">
    <h2>基本プロフィール入力</h2>
    <p class="text-muted required-note"><span class="required">*</span> は必須項目です</p>

    <c:if test="${not empty errorMessage}">
      <div class="alert alert-error">
        <c:out value="${errorMessage}"/>
        <div>※ 初期パスワードは、もう一度入力してください</div>
      </div>
    </c:if>

    <%-- 送信先は POST /employees。_csrf は、CSRF対策の確認用トークン --%>
    <%-- required や maxlength は、ブラウザの入力チェック(空のまま、長すぎる入力は、送信前に止まる) --%>
    <%-- value は、登録に失敗して戻ってきたとき、入力した内容を残すため(form は、Controllerが渡す。初回は無い) --%>
    <form action="<c:url value='/employees'/>" method="post">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">

      <div class="form-grid">
        <div class="form-group">
          <label for="name">氏名 <span class="required">*</span></label>
          <input id="name" name="name" type="text" class="form-control" maxlength="50" required autofocus data-label="氏名"
                 value="<c:out value='${form.name}'/>">
        </div>

        <div class="form-group">
          <label for="email">メールアドレス <span class="required">*</span></label>
          <input id="email" name="email" type="email" class="form-control" maxlength="255"
                 placeholder="example@company.com" autocomplete="off" required data-label="メールアドレス"
                 value="<c:out value='${form.email}'/>">
        </div>

        <div class="form-group">
          <label for="initialPassword">初期パスワード <span class="required">*</span></label>
          <div class="password-field">
            <input id="initialPassword" name="initialPassword" type="password" class="form-control"
                   autocomplete="new-password" required data-label="初期パスワード">
            <button type="button" class="password-toggle" data-target="initialPassword" aria-label="パスワードを表示する">
              <svg class="icon icon-show" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg class="icon icon-hide" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
          <%-- 入力するたびに、条件を満たしているかを、色で見せる(最終的な判定は、APIの側。ここは、目安) --%>
          <ul class="password-rules" id="passwordRules">
            <li data-rule="length">8文字以上</li>
            <li data-rule="lower">小文字を含む</li>
            <li data-rule="upper">大文字を含む</li>
            <li data-rule="number">数字を含む</li>
            <li data-rule="symbol">記号を含む</li>
          </ul>
        </div>

        <div class="form-group">
          <label for="joinDate">入社日 <span class="required">*</span></label>
          <input id="joinDate" name="joinDate" type="date" class="form-control" required data-label="入社日"
                 value="<c:out value='${form.joinDate}'/>">
        </div>

        <div class="form-group">
          <label for="department">部署 <span class="required">*</span></label>
          <input id="department" name="department" type="text" class="form-control" maxlength="50" required data-label="部署"
                 value="<c:out value='${form.department}'/>">
        </div>

        <div class="form-group">
          <label for="position">役職</label>
          <input id="position" name="position" type="text" class="form-control" maxlength="50" data-label="役職"
                 value="<c:out value='${form.position}'/>">
        </div>
      </div>

      <div class="form-group">
        <label>管理者権限設定</label>
        <div class="check-row">
          <label><input type="checkbox" name="isSystemAdmin" value="true" ${form.isSystemAdmin ? 'checked' : ''}> システム管理者</label>
          <label><input type="checkbox" name="isHrAdmin" value="true" ${form.isHrAdmin ? 'checked' : ''}> 業務管理者(人事)</label>
        </div>
      </div>

      <div class="form-actions">
        <a class="btn" href="<c:url value='/employees'/>">キャンセル</a>
        <button type="submit" class="btn btn-primary">登録する</button>
      </div>
    </form>
  </div>

  <%-- 入力中のチェック(必須・文字数・形式)。data-label が付いた入力欄を、入力するたびに調べる。下のスクリプトより先に読み込む --%>
  <script src="<c:url value='/js/form-validation.js'/>"></script>
  <script>
    // 目のマークごとに、対応する入力欄(data-target)の、表示・非表示を切り替える
    document.querySelectorAll('.password-toggle').forEach(function (button) {
      button.addEventListener('click', function () {
        const input = document.getElementById(button.dataset.target);
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        button.classList.toggle('is-visible', show);
        button.setAttribute('aria-label', show ? 'パスワードを隠す' : 'パスワードを表示する');
      });
    });

    // 初期パスワードの条件チェック。入力するたびに、条件ごとに、満たしていれば緑(ok)、満たしていなければ赤(ng)にする
    // 条件は、Cognitoのユーザープールのパスワードポリシーに合わせる(変えるときは、form-validation.js の passwordRules と、上の<ul>を直す)
    // 条件の中身は、form-validation.js に1つだけ置いて、「✓」の判断と、ここで、同じものを使う
    const passwordRules = PontalValidation.passwordRules;
    const passwordInput = document.getElementById('initialPassword');
    const ruleItems = document.querySelectorAll('#passwordRules li');

    passwordInput.addEventListener('input', function () {
      ruleItems.forEach(function (li) {
        const ok = passwordRules[li.dataset.rule](passwordInput.value);
        li.classList.toggle('ok', ok);
        li.classList.toggle('ng', !ok);
      });
    });
  </script>

</t:layout>

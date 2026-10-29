<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 新しいパスワードの設定画面(初回ログイン)。ログイン前なので、共通レイアウトは使わない --%>
<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>パスワードの設定 | Pontal</title>
  <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
</head>
<body>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <div class="brand-mark">⌘</div>
        <h1>パスワードの設定</h1>
        <p>初めてのログインです。新しいパスワードを設定してください</p>
      </div>

      <c:if test="${not empty errorMessage}">
        <div class="alert alert-error"><c:out value="${errorMessage}"/></div>
      </c:if>

      <%-- 送信先(POST /new-password)の処理は、次の手順で作る。_csrf は、CSRF対策の確認用トークン --%>
      <form action="<c:url value='/new-password'/>" method="post">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">

        <div class="form-group">
          <label for="newPassword">新しいパスワード</label>
          <div class="password-field">
            <input id="newPassword" name="newPassword" type="password" class="form-control"
                   autocomplete="new-password" required autofocus>
            <%-- type="button" にして、押してもフォームが送信されないようにする。data-target は、切り替える入力欄のid --%>
            <button type="button" class="password-toggle" data-target="newPassword" aria-label="パスワードを表示する">
              <svg class="icon icon-show" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg class="icon icon-hide" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>

        <div class="form-group">
          <label for="confirmPassword">新しいパスワード(確認)</label>
          <div class="password-field">
            <input id="confirmPassword" name="confirmPassword" type="password" class="form-control"
                   autocomplete="new-password" required>
            <button type="button" class="password-toggle" data-target="confirmPassword" aria-label="パスワードを表示する">
              <svg class="icon icon-show" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg class="icon icon-hide" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>

        <button type="submit" class="btn btn-primary btn-block btn-lg">パスワードを設定する</button>
      </form>

      <p class="login-note">大文字・小文字・数字・記号を組み合わせた、長めのパスワードにしてください</p>
    </div>
  </div>

  <script>
    // 目のマークごとに、対応する入力欄(data-target)の、表示・非表示を切り替える
    document.querySelectorAll('.password-toggle').forEach(function (button) {
      button.addEventListener('click', function () {
        const input = document.getElementById(button.dataset.target);
        const show = input.type === 'password';      // いま隠れているなら、表示にする
        input.type = show ? 'text' : 'password';
        button.classList.toggle('is-visible', show);
        button.setAttribute('aria-label', show ? 'パスワードを隠す' : 'パスワードを表示する');
      });
    });
  </script>
</body>
</html>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- ログイン画面。共通レイアウト(左のメニュー)は、ログイン前なので使わない --%>
<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ログイン | Pontal</title>
  <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
</head>
<body>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <div class="brand-mark">⌘</div>
        <h1>Pontal</h1>
        <p>社内ポータルシステム</p>
      </div>

      <%-- 失敗したとき・ログアウトしたとき・期限切れのときのメッセージ(Controllerが決めて、渡している) --%>
      <c:if test="${not empty errorMessage}">
        <div class="alert alert-error"><c:out value="${errorMessage}"/></div>
      </c:if>
      <c:if test="${not empty infoMessage}">
        <div class="alert alert-info"><c:out value="${infoMessage}"/></div>
      </c:if>

      <%-- 送信先は POST /login。処理は、Spring Securityが行う。_csrf は、CSRF対策の確認用トークン --%>
      <form action="<c:url value='/login'/>" method="post">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">

        <div class="form-group">
          <label for="email">メールアドレス</label>
          <input id="email" name="email" type="email" class="form-control"
                 placeholder="example@company.com" autocomplete="username" required autofocus>
        </div>

        <div class="form-group">
          <label for="password">パスワード</label>
          <div class="password-field">
            <input id="password" name="password" type="password" class="form-control"
                   autocomplete="current-password" required>
            <%-- type="button" にして、押してもフォームが送信されないようにする --%>
            <button type="button" id="togglePassword" class="password-toggle" aria-label="パスワードを表示する">
              <svg class="icon icon-show" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg class="icon icon-hide" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>

        <button type="submit" class="btn btn-primary btn-block btn-lg">ログイン</button>
      </form>

      <p class="login-note">ログインできない場合は、システム管理者にお問い合わせください</p>
    </div>
  </div>

  <script>
    const input = document.getElementById('password');
    const button = document.getElementById('togglePassword');

    button.addEventListener('click', function () {
      const show = input.type === 'password';      // いま隠れているなら、表示にする
      input.type = show ? 'text' : 'password';
      button.classList.toggle('is-visible', show);
      button.setAttribute('aria-label', show ? 'パスワードを隠す' : 'パスワードを表示する');
    });
  </script>
</body>
</html>

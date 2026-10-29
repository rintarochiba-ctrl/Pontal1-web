<%@ tag description="共通レイアウト(左のメニューと、ヘッダー)" pageEncoding="UTF-8" body-content="scriptless" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="title" required="true" description="ヘッダーに出す画面の名前" %>
<%@ attribute name="active" required="false" description="メニューで強調する項目(home / employees / salary / payroll / auditlog)" %>
<%@ attribute name="subtitle" required="false" description="ヘッダーの、画面の名前の横に出す文字(ホームのあいさつ など)" %>
<%--
  使い方(各画面のJSP):
    <%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
    <t:layout title="社員一覧" active="employees"> ...画面の中身... </t:layout>
  メニューの出し分けは、Controllerが Model に入れた loginUser(権限つき)を見て行う。
  画面に出す文字は、<c:out> を通す(<script> などを、文字として表示して、XSSを防ぐため)。
--%>
<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><c:out value="${title}"/> | Pontal</title>
  <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
</head>
<body>
<div class="app">

  <%-- 左のメニュー --%>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-mark">⌘</span>
      <span class="brand-name">Pontal</span>
    </div>

    <nav class="nav">
      <a class="nav-item${active == 'home' ? ' active' : ''}" href="<c:url value='/'/>">
        <svg class="icon" viewBox="0 0 24 24"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
        ホーム
      </a>
      <a class="nav-item${active == 'employees' ? ' active' : ''}" href="<c:url value='/employees'/>">
        <svg class="icon" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        社員一覧
      </a>
      <a class="nav-item${active == 'salary' ? ' active' : ''}" href="<c:url value='/salary-slips'/>">
        <svg class="icon" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        給与明細
      </a>

      <%-- HR管理者だけに出す --%>
      <c:if test="${loginUser.hrAdmin}">
        <a class="nav-item${active == 'payroll' ? ' active' : ''}" href="<c:url value='/payroll'/>">
          <svg class="icon" viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>
          給与管理
          <span class="nav-badge">HR</span>
        </a>
      </c:if>

      <%-- システム管理者だけに出す --%>
      <c:if test="${loginUser.systemAdmin}">
        <a class="nav-item${active == 'auditlog' ? ' active' : ''}" href="<c:url value='/audit-logs'/>">
          <svg class="icon" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          監査ログ
          <span class="nav-badge">管理者</span>
        </a>
      </c:if>
    </nav>

    <%-- ログイン中の人(ログインしていないときは出さない) --%>
    <c:if test="${not empty loginUser}">
      <div class="user-box">
        <div class="user-info">
          <span class="avatar"><c:out value="${loginUser.initial}"/></span>
          <span class="user-name"><c:out value="${loginUser.name}"/></span>
        </div>
        <%-- ログアウトは、POSTで送る。CSRF対策の確認用トークン(_csrf)を、一緒に送る --%>
        <form class="logout-form" action="<c:url value='/logout'/>" method="post">
          <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
          <button type="submit" class="btn btn-sm btn-block">ログアウト</button>
        </form>
      </div>
    </c:if>
  </aside>

  <%-- 右側(ヘッダーと、画面の中身) --%>
  <div class="main">
    <header class="topbar">
      <h1><c:out value="${title}"/></h1>
      <c:if test="${not empty subtitle}">
        <span class="topbar-sub"><c:out value="${subtitle}"/></span>
      </c:if>
    </header>
    <main class="content">
      <jsp:doBody/>
    </main>
  </div>

</div>
</body>
</html>

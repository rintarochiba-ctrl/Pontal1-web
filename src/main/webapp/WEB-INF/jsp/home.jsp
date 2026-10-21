<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="ホーム" active="home">

  <div class="card greeting-card">
    <h2><c:out value="${greeting}"/>、<c:out value="${loginUser.name}"/>さん</h2>
    <p class="text-muted">本日も安全に業務を進めましょう</p>
  </div>

  <h3 class="section-title">クイックアクセス</h3>
  <div class="quick-grid">
    <a class="quick-card" href="<c:url value='/employees'/>">
      <span class="quick-icon"><svg class="icon" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></span>
      <span>
        <span class="quick-title">社員一覧</span>
        <span class="quick-desc">連絡先と所属部署の確認</span>
      </span>
    </a>

    <a class="quick-card" href="<c:url value='/salary-slips'/>">
      <span class="quick-icon"><svg class="icon" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg></span>
      <span>
        <span class="quick-title">給与明細</span>
        <span class="quick-desc">今月の明細と履歴の閲覧</span>
      </span>
    </a>

    <%-- HR管理者だけに出す --%>
    <c:if test="${loginUser.hrAdmin}">
      <a class="quick-card" href="<c:url value='/payroll'/>">
        <span class="quick-icon"><svg class="icon" viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg></span>
        <span>
          <span class="quick-title">給与管理</span>
          <span class="quick-desc">明細のアップロードと履歴</span>
        </span>
      </a>
    </c:if>

    <%-- システム管理者だけに出す --%>
    <c:if test="${loginUser.systemAdmin}">
      <a class="quick-card" href="<c:url value='/audit-logs'/>">
        <span class="quick-icon"><svg class="icon" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg></span>
        <span>
          <span class="quick-title">監査ログ</span>
          <span class="quick-desc">操作記録の確認とダウンロード</span>
        </span>
      </a>
    </c:if>
  </div>

</t:layout>

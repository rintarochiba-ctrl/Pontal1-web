<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- Controllerが、announcement という名前で渡したお知らせ1件(AnnouncementDetail)を、表示する --%>
<t:layout title="お知らせ詳細" active="home">

  <%-- 作成・編集のあとに、Controllerが渡すメッセージ(redirectしても、1回だけ届く「フラッシュ属性」) --%>
  <c:if test="${not empty successMessage}">
    <div class="alert alert-success"><c:out value="${successMessage}"/></div>
  </c:if>

  <div class="card">
    <div class="detail-badges">
      <%-- 重要なお知らせにだけ、赤い「重要」を出す --%>
      <c:if test="${announcement.important}"><span class="badge badge-danger">重要</span></c:if>
      <span class="badge badge-primary"><c:out value="${announcement.category}"/></span>
    </div>

    <h2 class="announce-detail-title"><c:out value="${announcement.title}"/></h2>
    <%-- 「総務部 → 開発部へ　2026/10/22 10:30」の形。差出人は部署だけを出す --%>
    <p class="text-muted">
      <c:out value="${announcement.senderDepartment}"/> → <c:out value="${announcement.targetLabel}"/>へ
      　<c:out value="${announcement.createdAtText}"/>
    </p>

    <%-- 本文。改行は、CSSの white-space: pre-wrap で、そのまま出る。文字は、<c:out> を通す(<script> を、文字として出す) --%>
    <p class="announce-body"><c:out value="${announcement.body}"/></p>
  </div>

  <div class="flex-between mt-16">
    <a href="<c:url value='/announcements'/>">← 一覧へ戻る</a>

    <div class="flex announce-actions">
      <%-- ボタンを出すかは、APIが返した canEdit / canDelete で決める(権限の判断は、画面側で持たない) --%>
      <c:if test="${announcement.canEdit}">
        <a class="btn" href="<c:url value='/announcements/${announcement.id}/edit'/>">編集</a>
      </c:if>
      <c:if test="${announcement.canDelete}">
        <%-- 削除は、POSTで送る。確認のダイアログで、OKのときだけ送られる --%>
        <form action="<c:url value='/announcements/${announcement.id}/delete'/>" method="post"
              onsubmit="return confirm('このお知らせを削除しますか?');">
          <%-- CSRF対策の確認用トークン。POSTのフォームには、必ず付ける(付けないと、403になる) --%>
          <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
          <button type="submit" class="btn btn-danger">削除</button>
        </form>
      </c:if>
    </div>
  </div>

</t:layout>

<%@ tag description="お知らせの欄(ホームに出す、見出し + 表)" pageEncoding="UTF-8" body-content="empty" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="heading" required="true" description="欄の見出し(あなたへのお知らせ など)" %>
<%@ attribute name="scope" required="true" description="「すべて表示」で開く一覧の、表示の切り替え(mine / everyone)" %>
<%@ attribute name="items" required="true" type="java.util.List" description="表示するお知らせ(AnnouncementItem)" %>
<%@ attribute name="failed" required="true" type="java.lang.Boolean" description="お知らせを取得できなかったか" %>
<%--
  使い方(home.jsp):
    <%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
    <t:announcement-box heading="全員向け" scope="everyone" items="${everyoneAnnouncements}" failed="${everyoneFailed}"/>
  「あなたへ」と「全員向け」で、同じ作りの欄を2回出すので、タグにまとめてある。
--%>
<div class="card">
  <div class="announce-head">
    <h2><c:out value="${heading}"/></h2>
    <a href="<c:url value='/announcements'><c:param name='scope' value='${scope}'/></c:url>">すべて表示</a>
  </div>

  <c:choose>
    <%-- 取得に失敗したときは、この欄にだけメッセージを出す --%>
    <c:when test="${failed}">
      <p class="text-muted">お知らせを取得できませんでした。しばらくしてから、もう一度お試しください</p>
    </c:when>
    <c:when test="${empty items}">
      <p class="text-muted">お知らせは、ありません</p>
    </c:when>
    <c:otherwise>
      <table class="table">
        <thead>
          <tr>
            <th>日時</th>
            <th>種類</th>
            <th>差出人</th>
            <th>タイトル</th>
          </tr>
        </thead>
        <tbody>
          <c:forEach var="a" items="${items}">
            <tr>
              <td class="announce-date"><c:out value="${a.createdAtText}"/></td>
              <td><span class="badge badge-primary"><c:out value="${a.category}"/></span></td>
              <td><c:out value="${a.senderDepartment}"/></td>
              <td>
                <c:if test="${a.important}"><span class="badge badge-danger">重要</span></c:if>
                <a href="<c:url value='/announcements/${a.id}'/>"><c:out value="${a.title}"/></a>
              </td>
            </tr>
          </c:forEach>
        </tbody>
      </table>
    </c:otherwise>
  </c:choose>
</div>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- 左のメニューで強調するのは「ホーム」(お知らせ一覧は、ホームの延長) --%>
<t:layout title="お知らせ一覧" active="home">

  <%-- 削除したあとなどに、Controllerが渡すメッセージ(redirectしても、1回だけ届く「フラッシュ属性」) --%>
  <c:if test="${not empty successMessage}">
    <div class="alert alert-success"><c:out value="${successMessage}"/></div>
  </c:if>

  <div class="list-toolbar">
    <%-- 表示の切り替えと、種類の絞り込み。GETで送る(URLが /announcements?scope=mine&category=技術 になる) --%>
    <form class="announce-filter" action="<c:url value='/announcements'/>" method="get">
      <span class="filter-label">表示</span>
      <%-- いま選んでいる表示(scope)のラジオには、checked を付ける。「あなたへ」「全員向け」も、同じ形で付ける --%>
      <label><input type="radio" name="scope" value="all" ${scope == 'all' ? 'checked' : ''}> すべて</label>
      <label><input type="radio" name="scope" value="mine" ${scope == 'mine' ? 'checked' : ''}> あなたへ</label>
      <label><input type="radio" name="scope" value="everyone" ${scope == 'everyone' ? 'checked' : ''}> 全員向け</label>
      <%-- システム管理者かHR管理者にだけ出す(他人宛ても含む、全件) --%>
      <c:if test="${loginUser.systemAdmin || loginUser.hrAdmin}">
        <label><input type="radio" name="scope" value="manage" ${scope == 'manage' ? 'checked' : ''}> すべて(管理者)</label>
      </c:if>

      <span class="filter-label">種類</span>
      <select name="category" class="form-control">
        <option value="">すべて</option>
        <%-- いま選んでいる種類(category)の option には、selected を付ける。「社内情報」「対応依頼」も、同じ形で付ける --%>
        <option value="技術" ${category == '技術' ? 'selected' : ''}>技術</option>
        <option value="社内情報" ${category == '社内情報' ? 'selected' : ''}>社内情報</option>
        <option value="対応依頼" ${category == '対応依頼' ? 'selected' : ''}>対応依頼</option>
      </select>
      <button type="submit" class="btn">絞り込む</button>
    </form>

    <%-- システム管理者かHR管理者にだけ出す(作成画面は、別のブランチで作る) --%>
    <c:if test="${loginUser.systemAdmin || loginUser.hrAdmin}">
      <a class="btn btn-primary" href="<c:url value='/announcements/new'/>">＋ 作成</a>
    </c:if>
  </div>
  <p class="text-muted">全 <c:out value="${totalCount}"/> 件</p>

  <div class="card">
    <c:choose>
      <%-- 0件のときは、表を出さずに、メッセージだけを出す --%>
      <c:when test="${empty announcements}">
        <p class="text-muted">該当するお知らせは、ありません</p>
      </c:when>
      <c:otherwise>
        <table class="table">
          <thead>
            <tr>
              <th>日時</th>
              <th>種類</th>
              <th>宛先</th>
              <th>差出人</th>
              <th>タイトル</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <%-- お知らせを1件ずつ、1行にする。1件分を a という名前で使う --%>
            <c:forEach var="a" items="${announcements}">
              <tr>
                <td class="announce-date"><c:out value="${a.createdAtText}"/></td>
                <td><span class="badge badge-primary"><c:out value="${a.category}"/></span></td>
                <td><c:out value="${a.targetLabel}"/></td>
                <td><c:out value="${a.senderDepartment}"/></td>
                <td>
                  <%-- 重要なお知らせにだけ、赤い「重要」を出す --%>
                  <c:if test="${a.important}"><span class="badge badge-danger">重要</span></c:if>
                  <c:out value="${a.title}"/>
                </td>
                <%-- 行の右端の「>」から、そのお知らせの詳細へ(詳細の画面は、別のブランチで作る) --%>
                <td><a href="<c:url value='/announcements/${a.id}'/>">&gt;</a></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </c:otherwise>
    </c:choose>
  </div>

  <%-- ページ送り(ここは、私が書きました。2ページ以上あるときだけ出す) --%>
  <c:if test="${totalPages > 1}">
    <div class="pagination">
      <c:choose>
        <c:when test="${not empty prev}">
          <a href="<c:url value='/announcements'><c:param name='page' value='${prev}'/><c:param name='scope' value='${scope}'/><c:param name='category' value='${category}'/></c:url>">‹</a>
        </c:when>
        <c:otherwise><span class="disabled">‹</span></c:otherwise>
      </c:choose>

      <%-- Controllerが計算した数字を、順に出す。-1 は「…」。画面の番号は1始まり、URLのpageは0始まり --%>
      <c:forEach var="p" items="${pageNumbers}">
        <c:choose>
          <c:when test="${p == -1}"><span class="ellipsis">…</span></c:when>
          <c:when test="${p == page + 1}"><span class="current"><c:out value="${p}"/></span></c:when>
          <c:otherwise>
            <a href="<c:url value='/announcements'><c:param name='page' value='${p - 1}'/><c:param name='scope' value='${scope}'/><c:param name='category' value='${category}'/></c:url>"><c:out value="${p}"/></a>
          </c:otherwise>
        </c:choose>
      </c:forEach>

      <c:choose>
        <c:when test="${not empty next}">
          <a href="<c:url value='/announcements'><c:param name='page' value='${next}'/><c:param name='scope' value='${scope}'/><c:param name='category' value='${category}'/></c:url>">›</a>
        </c:when>
        <c:otherwise><span class="disabled">›</span></c:otherwise>
      </c:choose>
    </div>
  </c:if>

  <p class="mt-16"><a href="<c:url value='/'/>">← ホームへ戻る</a></p>

</t:layout>

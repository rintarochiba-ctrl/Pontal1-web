<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="社員詳細" active="employees">

  <%-- 登録・編集のあとに、1回だけ出すお知らせ(Controllerが、redirect と一緒に渡す) --%>
  <c:if test="${not empty successMessage}">
    <div class="alert alert-success"><c:out value="${successMessage}"/></div>
  </c:if>

  <%-- 上のカード: 名前・部署・役職・メールアドレス --%>
  <div class="card detail-header">
    <span class="detail-avatar"><c:out value="${employee.initial}"/></span>
    <div>
      <h2 class="detail-name"><c:out value="${employee.name}"/></h2>
      <div class="detail-badges">
        <c:if test="${not empty employee.department}">
          <span class="badge badge-primary"><c:out value="${employee.department}"/></span>
        </c:if>
        <c:if test="${not empty employee.position}">
          <span class="badge"><c:out value="${employee.position}"/></span>
        </c:if>
      </div>
      <div class="text-muted">メールアドレス: <c:out value="${employee.email}"/></div>
    </div>
    <%-- システム管理者だけに出す(編集画面は、別の手順で作る) --%>
    <c:if test="${loginUser.systemAdmin}">
      <div class="detail-actions">
        <a class="btn" href="<c:url value='/employees/${employee.id}/edit'/>">編集</a>
      </div>
    </c:if>
  </div>

  <div class="detail-grid">
    <div class="card">
      <h2>基本情報</h2>
      <div class="info-row"><span class="text-muted">性別</span><strong><c:out value="${employee.gender}" default="-"/></strong></div>
      <div class="info-row"><span class="text-muted">年齢</span><strong><c:out value="${employee.ageText}"/></strong></div>
      <div class="info-row"><span class="text-muted">出身地</span><strong><c:out value="${employee.birthplace}" default="-"/></strong></div>
      <div class="info-row"><span class="text-muted">入社日</span><strong><c:out value="${employee.joinDateText}"/></strong></div>
    </div>

    <div class="card">
      <h2>自己紹介・Q&amp;A</h2>
      <c:choose>
        <c:when test="${employee.hasProfileText}">
          <c:if test="${not empty employee.hobby}">
            <div class="qa-question">趣味や休日の過ごし方</div>
            <p class="qa-answer"><c:out value="${employee.hobby}"/></p>
          </c:if>
          <c:if test="${not empty employee.bio}">
            <div class="qa-question">自己PR</div>
            <p class="qa-answer"><c:out value="${employee.bio}"/></p>
          </c:if>
          <%-- Q&A: JSON(質問と答えの組)として読めれば、組ごとに並べる。読めなければ、ふつうの文字として、そのまま出す --%>
          <c:choose>
            <c:when test="${not empty employee.selfQaItems}">
              <c:forEach var="qa" items="${employee.selfQaItems}">
                <div class="qa-question"><c:out value="${qa.question}"/></div>
                <p class="qa-answer"><c:out value="${qa.answer}"/></p>
              </c:forEach>
            </c:when>
            <c:when test="${not empty employee.selfQa}">
              <div class="qa-question">Q&amp;A</div>
              <p class="qa-answer"><c:out value="${employee.selfQa}"/></p>
            </c:when>
          </c:choose>
        </c:when>
        <c:otherwise>
          <p class="text-muted">まだ登録されていません</p>
        </c:otherwise>
      </c:choose>
    </div>
  </div>

  <p class="mt-16"><a href="<c:url value='/employees'/>">← 社員一覧に戻る</a></p>

</t:layout>

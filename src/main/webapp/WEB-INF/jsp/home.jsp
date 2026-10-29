<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- あいさつは、ヘッダーの「ホーム」の横に出す(subtitle)。中身は、すぐ、ハロワお知らせから始める --%>
<t:layout title="ホーム" active="home" subtitle="${greeting}、${loginUser.name}さん">

  <%-- ハロワお知らせ: 上に「あなたへ」、下に「全員向け」。同じ作りの欄なので、タグにまとめてある --%>
  <h3 class="section-title">ハロワお知らせ</h3>
  <t:announcement-box heading="あなたへのお知らせ" scope="mine" items="${mineAnnouncements}" failed="${mineFailed}"/>
  <t:announcement-box heading="全員向け" scope="everyone" items="${everyoneAnnouncements}" failed="${everyoneFailed}"/>

</t:layout>

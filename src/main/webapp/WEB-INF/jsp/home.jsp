<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- ホーム画面(仮)。ログイン後の移動先。デザインに合わせた画面は、ホームのPRで作る --%>
<t:layout title="ホーム" active="home">
  <div class="card">
    <h2>ようこそ、<c:out value="${loginUser.name}"/> さん</h2>
    <p class="text-muted">ログインしました。左のメニューから、画面を選んでください</p>
  </div>
</t:layout>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="監査ログ" active="auditlog">

  <nav class="tabs">
    <a class="tab active" href="<c:url value='/audit-logs'/>">操作履歴</a>
    <a class="tab" href="<c:url value='/audit-logs/exports'/>">週ごとのCSV</a>
  </nav>

  <div class="card">
    <p>準備中です</p>
  </div>
</t:layout>

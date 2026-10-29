<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="給与明細" active="salary">

  <div class="card">
    <t:salary-slip-table slips="${slips}" emptyMessage="給与明細は、まだありません"/>
  </div>

</t:layout>

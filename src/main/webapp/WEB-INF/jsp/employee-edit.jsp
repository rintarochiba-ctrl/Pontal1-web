<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="社員編集" active="employees">

  <div class="card form-card">
    <h2>プロフィール編集</h2>
    <p class="text-muted required-note"><span class="required">*</span> は必須項目です</p>

    <%-- 登録のあと、この画面に移動してきたときのお知らせ(1回だけ出る) --%>
    <c:if test="${not empty successMessage}">
      <div class="alert alert-success"><c:out value="${successMessage}"/></div>
    </c:if>
    <c:if test="${not empty errorMessage}">
      <div class="alert alert-error"><c:out value="${errorMessage}"/></div>
    </c:if>

    <p class="text-muted">メールアドレス: <c:out value="${employee.email}"/>(変更できません)</p>

    <%-- 送信先は POST /employees/{id}(処理は、次の手順で作る)。_csrf は、CSRF対策の確認用トークン --%>
    <form action="<c:url value='/employees/${employee.id}'/>" method="post">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">

      <%-- 編集のAPIは、全項目を丸ごと置き換える。画面で編集しない項目(画像)は、いまの値を、そのまま送り直す --%>
      <input type="hidden" name="image" value="<c:out value='${employee.imageUrl}'/>">

      <div class="form-grid">
        <div class="form-group">
          <label for="name">氏名 <span class="required">*</span></label>
          <input id="name" name="name" type="text" class="form-control" maxlength="50" required data-label="氏名"
                 value="<c:out value='${employee.name}'/>">
        </div>

        <div class="form-group">
          <label for="joinDate">入社日 <span class="required">*</span></label>
          <input id="joinDate" name="joinDate" type="date" class="form-control" required data-label="入社日"
                 value="<c:out value='${employee.joinDate}'/>">
        </div>

        <div class="form-group">
          <label for="department">部署</label>
          <input id="department" name="department" type="text" class="form-control" maxlength="50" data-label="部署"
                 value="<c:out value='${employee.department}'/>">
        </div>

        <div class="form-group">
          <label for="position">役職</label>
          <input id="position" name="position" type="text" class="form-control" maxlength="50" data-label="役職"
                 value="<c:out value='${employee.position}'/>">
        </div>

        <div class="form-group">
          <label for="gender">性別</label>
          <%-- 候補は出すが、自由に入力もできる(APIは、10文字までの文字) --%>
          <input id="gender" name="gender" type="text" class="form-control" maxlength="10" list="genderOptions" data-label="性別"
                 value="<c:out value='${employee.gender}'/>">
          <datalist id="genderOptions">
            <option value="男性">
            <option value="女性">
            <option value="その他">
          </datalist>
        </div>

        <div class="form-group">
          <label for="age">年齢</label>
          <input id="age" name="age" type="number" class="form-control" min="0" max="150" data-label="年齢"
                 value="<c:out value='${employee.age}'/>">
        </div>

        <div class="form-group">
          <label for="birthplace">出身地</label>
          <input id="birthplace" name="birthplace" type="text" class="form-control" maxlength="50" data-label="出身地"
                 value="<c:out value='${employee.birthplace}'/>">
        </div>

        <div class="form-group form-wide">
          <label for="hobby">趣味や休日の過ごし方</label>
          <textarea id="hobby" name="hobby" class="form-control" rows="3" maxlength="255" data-label="趣味や休日の過ごし方"><c:out value="${employee.hobby}"/></textarea>
        </div>

        <div class="form-group form-wide">
          <label for="bio">自己PR</label>
          <textarea id="bio" name="bio" class="form-control" rows="4"><c:out value="${employee.bio}"/></textarea>
        </div>
      </div>

      <%-- 自己紹介のQ&A(質問と答え。最大3組)。空の組は、保存されない --%>
      <%-- qaItems は、いまの Q&A(JSONとして読めたもの)。JSONでない、ふつうの文字で入っている古いデータは、
           消えてしまわないよう、最初の答えの欄に入れて見せる(legacyQa) --%>
      <c:set var="qaItems" value="${employee.selfQaItems}"/>
      <c:set var="legacyQa" value="${empty qaItems and not empty employee.selfQa}"/>
      <div class="form-group">
        <label>Q&amp;A(最大3組)</label>
        <c:if test="${legacyQa}">
          <p class="text-muted">これまでの文字が、1つ目の答えに入っています。質問を付けて保存してください</p>
        </c:if>
        <c:forEach var="i" begin="0" end="2">
          <div class="qa-edit">
            <input name="qaQuestion" type="text" class="form-control" maxlength="100" placeholder="質問 ${i + 1}"
                   value="<c:out value='${qaItems[i].question}'/>">
            <textarea name="qaAnswer" class="form-control" rows="2" maxlength="500" placeholder="答え ${i + 1}"><c:out value="${legacyQa && i == 0 ? employee.selfQa : qaItems[i].answer}"/></textarea>
          </div>
        </c:forEach>
      </div>

      <%-- 自分自身の編集では、システム管理者の権限を外せない(チェックを固定する。送信側でも、Controllerが守る) --%>
      <c:set var="editingSelf" value="${loginUser.employeeId == employee.id}"/>
      <div class="form-group">
        <label>管理者権限設定</label>
        <div class="check-row">
          <label><input type="checkbox" name="isSystemAdmin" value="true" ${employee.systemAdmin || editingSelf ? 'checked' : ''} ${editingSelf ? 'disabled' : ''}> システム管理者</label>
          <label><input type="checkbox" name="isHrAdmin" value="true" ${employee.hrAdmin ? 'checked' : ''}> 業務管理者(人事)</label>
        </div>
        <c:if test="${editingSelf}">
          <p class="text-muted">自分自身のシステム管理者の権限は、外せません</p>
        </c:if>
      </div>

      <div class="form-actions">
        <a class="btn" href="<c:url value='/employees/${employee.id}'/>">キャンセル</a>
        <button type="submit" class="btn btn-primary">保存する</button>
      </div>
    </form>
  </div>

  <%-- 入力中のチェック(必須・文字数・数の範囲)。data-label が付いた入力欄を、入力するたびに調べる。最初から入っている値も、開いた直後に調べる --%>
  <script src="<c:url value='/js/form-validation.js'/>"></script>

</t:layout>

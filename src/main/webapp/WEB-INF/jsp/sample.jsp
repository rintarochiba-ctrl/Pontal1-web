<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- 共通レイアウトと、部品(CSS)の見本。ログインができたら、削除する --%>
<t:layout title="サンプル(レイアウトの確認)" active="home">

  <div class="card">
    <h2>メニューの出し分け</h2>
    <p>URLの末尾を変えると、権限ごとのメニューを確認できます</p>
    <div class="flex">
      <a class="btn" href="<c:url value='/sample?role=member'/>">一般ユーザー</a>
      <a class="btn" href="<c:url value='/sample?role=hr'/>">HR管理者</a>
      <a class="btn" href="<c:url value='/sample?role=admin'/>">システム管理者</a>
    </div>
    <p class="text-muted mt-16">いまの表示: <c:out value="${loginUser.name}"/></p>
  </div>

  <div class="card">
    <h2>ボタンとバッジ</h2>
    <div class="flex">
      <a class="btn btn-primary" href="#">主なボタン</a>
      <a class="btn" href="#">ふつうのボタン</a>
      <a class="btn btn-danger" href="#">削除</a>
      <span class="badge badge-success">完了</span>
      <span class="badge badge-primary">進行中</span>
      <span class="badge badge-warning">注意</span>
      <span class="badge badge-danger">失敗</span>
    </div>
  </div>

  <div class="card">
    <h2>メッセージ</h2>
    <div class="alert alert-success">保存しました</div>
    <div class="alert alert-error">入力内容に誤りがあります</div>
    <div class="alert alert-info">お知らせです</div>
  </div>

  <div class="card">
    <h2>表</h2>
    <table class="table">
      <thead><tr><th>支給年月</th><th>操作</th></tr></thead>
      <tbody>
        <tr><td>2026年10月支給分</td><td><a class="btn btn-sm" href="#">PDF</a></td></tr>
        <tr><td>2026年09月支給分</td><td><a class="btn btn-sm" href="#">PDF</a></td></tr>
      </tbody>
    </table>
  </div>

  <div class="card">
    <h2>フォーム</h2>
    <div class="form-group">
      <label>メールアドレス</label>
      <input class="form-control is-invalid" type="text" value="s.suzuki_invalid">
      <div class="form-error">正しいメールアドレスの形式で入力してください</div>
    </div>
    <div class="form-group">
      <label>氏名</label>
      <input class="form-control" type="text" value="鈴木 三郎">
    </div>
  </div>

</t:layout>

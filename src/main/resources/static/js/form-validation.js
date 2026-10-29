/*
 * 入力フォームの、入力中のチェック(社員の登録・編集で使う)
 *
 *   ・チェックしたい入力欄には、data-label="氏名" のように、項目の名前を付ける(これが、チェックの目印)
 *   ・必須か、文字数の上限、数の範囲は、入力欄の required / maxlength / min / max から読み取る
 *   ・入力するたびに、その項目を調べて、OKなら緑の「✓」、まちがいなら赤いメッセージを、入力欄の下に出す
 *   ・開いた直後は、空の必須項目を、赤くしない(一度、入力するか、フォーカスを外してから出す)
 *     最初から入っている値(編集画面など)は、開いた直後に調べて、OKなら「✓」を出す
 *   ・ここは、あくまで、目安のチェック。最終的な判断は、API側が行う(サーバー側のチェックは、変えていない)
 */
(function (root) {
  'use strict';

  // ------------------------------------------------------------------
  // 値だけを見て判断する部分(画面に、依存しない)
  // ------------------------------------------------------------------

  // 初期パスワードの条件。Cognitoのユーザープールのパスワードポリシーに合わせる(変えるときは、ここだけ直す)
  var passwordRules = {
    length: function (v) { return v.length >= 8; },
    lower: function (v) { return /[a-z]/.test(v); },
    upper: function (v) { return /[A-Z]/.test(v); },
    number: function (v) { return /[0-9]/.test(v); },
    symbol: function (v) { return /[^A-Za-z0-9]/.test(v); }
  };

  function passwordOk(value) {
    return Object.keys(passwordRules).every(function (name) {
      return passwordRules[name](value);
    });
  }

  // メールアドレスの形(「 @ 」と「 . 」が入っていて、空白がない)。細かい判断は、API側
  var EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  // 1つの項目を調べて、まちがいがあれば、メッセージを返す。問題なければ、空の文字('')を返す
  //   field: { kind, label, value, required, maxLength, min, max }
  //   kind は、'text' / 'email' / 'password' / 'number' / 'date'
  function check(field) {
    var value = field.value == null ? '' : String(field.value);
    var label = field.label;

    // 必須。パスワードは、空白も文字なので、そのまま。それ以外は、空白だけの入力も、空として扱う
    var empty = field.kind === 'password' ? value === '' : value.trim() === '';
    if (field.required && empty) {
      return label + 'を入力してください';
    }
    if (empty) {
      return ''; // 必須ではなくて、空。問題なし
    }

    if (field.kind === 'password') {
      return passwordOk(value) ? '' : 'パスワードの条件を満たしてください';
    }
    if (field.kind === 'email' && !EMAIL.test(value)) {
      return label + 'の形式が正しくありません';
    }
    if (field.kind === 'number') {
      var n = Number(value);
      if (isNaN(n)) {
        return label + 'は、数字で入力してください';
      }
      var hasMin = field.min != null;
      var hasMax = field.max != null;
      if ((hasMin && n < field.min) || (hasMax && n > field.max)) {
        if (hasMin && hasMax) {
          return label + 'は、' + field.min + '〜' + field.max + 'の間で入力してください';
        }
        return hasMin ? label + 'は、' + field.min + '以上で入力してください'
                      : label + 'は、' + field.max + '以下で入力してください';
      }
    }
    if (field.maxLength != null && value.length > field.maxLength) {
      return label + 'は、' + field.maxLength + '文字以内で入力してください';
    }
    return '';
  }

  // 画面に出す状態を返す。state は、'ok'(✓を出す) / 'error'(メッセージを出す) / 'none'(何も出さない)
  //   必須ではない項目が、空のときは、'ok' にしない(✓を出しても、意味がないため)
  function evaluate(field) {
    var message = check(field);
    if (message) {
      return { state: 'error', message: message };
    }
    var value = field.value == null ? '' : String(field.value);
    var empty = field.kind === 'password' ? value === '' : value.trim() === '';
    return { state: empty ? 'none' : 'ok', message: '' };
  }

  // ------------------------------------------------------------------
  // 画面(入力欄)とつなぐ部分
  // ------------------------------------------------------------------

  function numberOrNull(text) {
    return text === '' || text == null ? null : Number(text);
  }

  // 入力欄の、いまの状態を、check に渡す形にする
  function fieldOf(input) {
    var kind = input.dataset.kind || (['email', 'password', 'number', 'date'].indexOf(input.type) >= 0 ? input.type : 'text');
    return {
      kind: kind,
      label: input.dataset.label,
      value: input.value,
      required: input.required,
      maxLength: input.maxLength >= 0 ? input.maxLength : null,
      min: numberOrNull(input.getAttribute('min')),
      max: numberOrNull(input.getAttribute('max'))
    };
  }

  // 入力欄の下に、メッセージを出す場所を作る(パスワードは、目のマークを含む枠の下)
  function createMessageBox(input) {
    var box = document.createElement('div');
    box.className = 'field-msg';
    box.setAttribute('role', 'status');
    var anchor = input.closest('.password-field') || input;
    anchor.insertAdjacentElement('afterend', box);
    return box;
  }

  function attach(input) {
    var box = createMessageBox(input);
    var touched = false; // 一度、入力したか、フォーカスを外したか

    function render() {
      var result = evaluate(fieldOf(input));
      // 空の必須項目は、まだ触っていないうちは、赤くしない(最初から値がある項目は、触る前でも、出す)
      var quiet = !touched && input.value === '';
      var state = quiet ? 'none' : result.state;
      input.classList.toggle('is-valid', state === 'ok');
      input.classList.toggle('is-invalid', state === 'error');
      box.className = 'field-msg' + (state === 'ok' ? ' ok' : state === 'error' ? ' error' : '');
      box.textContent = state === 'ok' ? '✓' : state === 'error' ? result.message : '';
    }

    input.addEventListener('input', function () { touched = true; render(); });
    input.addEventListener('change', function () { touched = true; render(); });
    input.addEventListener('blur', function () { touched = true; render(); });
    // 送信しようとして、ブラウザに止められたとき(必須が空など)も、メッセージを出す
    input.addEventListener('invalid', function () { touched = true; render(); });
    render(); // 開いた直後。最初から値が入っている項目は、ここで、✓かメッセージが出る
  }

  function init() {
    document.querySelectorAll('input[data-label], textarea[data-label]').forEach(attach);
  }

  // 公開するもの(画面から使う passwordRules と、テスト用の check / evaluate)
  var api = { passwordRules: passwordRules, passwordOk: passwordOk, check: check, evaluate: evaluate };
  root.PontalValidation = api;
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api; // Node.js で、動作を確認するとき用
  }

  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', init);
    } else {
      init();
    }
  }
})(typeof window !== 'undefined' ? window : globalThis);

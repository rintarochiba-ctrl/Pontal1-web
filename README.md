# Pontal1-web

社内ポータルシステム Pontal の画面側です
JSP で画面を作り 画面から Pontal の API (別リポジトリ Pontal1) を呼び出します
ログインは AWS Cognito で行います

```
ブラウザ → pontal-web (JSP / このリポジトリ) → Pontal の API (Pontal1) → PostgreSQL / S3
                 ↓
            Cognito (ログイン)
```

## 技術

| 区分 | 内容 |
|---|---|
| 言語・FW | Java 17 / Spring Boot 3.2 |
| 画面 | JSP + JSTL (war でパッケージ) |
| API 呼び出し | Pontal の API (JWT を Bearer で送る) |
| 認証 | AWS Cognito (トークンをセッションに持つ) |
| テスト | JUnit 5 / Mockito |
| CI | GitHub Actions (PR ごとに `mvn test`) |

## 必要なもの

- JDK 17 以上 と Maven
- Docker Desktop (Pontal の API を動かすため)
- Pontal1 リポジトリ (API 側) を手元に取得しておく

## 初回セットアップ

### 1. Pontal の API を起動する

Pontal1 のフォルダで 起動します (README の手順を参照)

```bash
cd ../Pontal1
docker compose up -d
```

API は `http://localhost:8092` で動きます

### 2. `.env` を作る

`.env.example` をコピーして `.env` を作り 値を書きます (`.env` は Git の管理外です)

```bash
cp .env.example .env
```

- `PONTAL_API_BASE_URL`: Pontal の API の接続先 (既定は `http://localhost:8092`)
- `COGNITO_REGION` と `COGNITO_CLIENT_ID`: ログイン画面 (HWLA-369) から使います
- 値は管理者から受け取ってください

### 3. 起動する

```bash
mvn spring-boot:run
```

ブラウザで http://localhost:8080 を開きます (Pontal の API とは別のポートです)

プロジェクト直下の `.env` は 起動時に自動で読み込まれます
Java を保存すると devtools が自動で再起動します

## Docker で動かす

```bash
docker compose up -d --build
```

- コンテナの中から 手元の Pontal の API (`8092`) を呼びます (`host.docker.internal` を使う)
- 先に Pontal の API を起動しておきます

## テスト

```bash
mvn test                                   # 全部
mvn test -Dtest=PontalWebApplicationTests  # 1 クラスだけ
```

PR を出すと GitHub Actions で `mvn -B test` が自動で動きます (`.github/workflows/ci.yml`)

## ディレクトリ

| パス | 内容 |
|---|---|
| `src/main/java/com/example/pontalweb` | Java のコード (Controller など) |
| `src/main/webapp/WEB-INF/jsp` | 画面 (JSP) |
| `src/main/webapp/WEB-INF/tags` | 共通の部品 (レイアウトなど タグファイル) |
| `src/main/resources/static` | CSS などの静的ファイル |
| `src/main/resources/application.properties` | 設定 |
| `src/test` | テスト |
| `.github` | PR のテンプレートと CI の設定 |

## ブランチと PR

- チケット (HWLA-xxx) ごとに `feature/HWLA-xxx` のブランチを切り 1 チケット 1 PR で出します
- 1 つのチケットに複数の画面がある場合は コミットを画面ごとに分けます
- 先のチケットを先にマージしてから 次に進みます (368 → 369 → 370 → 371 → 372)

## チケットと画面

| チケット | 内容 |
|---|---|
| HWLA-368 | 画面側の土台 (プロジェクト 共通レイアウト エラー画面 CI) |
| HWLA-369 | ログイン画面 (ログイン ログアウト ホーム 初回パスワード変更) |
| HWLA-370 | 社員関連画面 (一覧 詳細 登録 編集・削除) |
| HWLA-371 | 給与関連画面 (給与明細 社員別 アップロード 履歴) |
| HWLA-372 | 監査ログ画面 (月ごとの一覧と CSV ダウンロード) |

画面は 初期の設計から 今の Pontal の API に合わせて作ります
(給与の金額列 CSV の一括アップロード 監査ログの検索 顔写真などは 作りません)

## 注意

- `.env` や 個人用のメモ (`.env.pass`) は Git に上げません
- パスワードやトークンを ログや画面に出さないようにします
- API の仕様は Pontal1 の `openapi/openapi.yaml` を参照してください

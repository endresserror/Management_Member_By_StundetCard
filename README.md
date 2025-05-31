# Student Card System - Android NFC Felica Reader

学生証のFelicaチップを読み取り、学籍番号に基づいてメールを送信したり記録を集めるプログラムです。
入部手続きの簡略化のために作成しました。
また、飲み会の集金記録の作成のためにも作成しました。

## 機能

- **NFC Felica読み取り**: 学生証から学籍番号を抽出
- **自動メール送信**: 読み取った学籍番号に対応するメールアドレスに自動送信
- **リアルタイムログ**: 操作履歴とエラー情報をリアルタ
## 仕様
### NFC読み取り
- **対応技術**: Felica (NfcF)
- **システムコード**: 0xFE00
- **サービスコード**: 0x1A8B
- **文字エンコーディング**: Shift_JIS

### メール送信
- **SMTPサーバー**: Gmail SMTP (smtp.gmail.com:587)
- **認証**: アプリパスワード使用 (アプリ内で設定)
- **メールアドレス形式**: `[学籍番号小文字]@[設定したドメイン]`

## 必要な権限

- `android.permission.NFC`: NFC機能の使用
- `android.permission.INTERNET`: メール送信
- `android.permission.ACCESS_NETWORK_STATE`: ネットワーク状態確認

## ビルドと実行

### 前提条件

- Android Studio Arctic Fox以降
- Android SDK API Level 21以上
- NFCが搭載されたAndroidデバイス

### ビルド手順

1. プロジェクトをAndroid Studioで開く
2. Gradle Syncを実行
3. デバイスまたはエミュレータでアプリを実行

```bash
# コマンドラインからビルド
./gradlew assembleDebug

# APKインストール
./gradlew installDebug
```

## 使用方法

1. アプリを起動
2. メール設定画面で、送信元Gmailアドレスとアプリパスワード、件名、本文テンプレートを設定
3. NFCが有効になっていることを確認
4. 学生証をデバイスの背面にタッチ
5. アプリが自動的に学籍番号を読み取り、設定に基づいてメールを送信

## プロジェクト構造と主要ファイルの説明

```
app/
├── src/main/
│   ├── java/com/example/studentcardsystem/  # Kotlinソースコードのルート
│   │   ├── MainActivity.kt           # アプリケーションのメイン画面とNFCイベント処理、UI更新、メール送信の起点
│   │   ├── FelicaReader.kt          # Felicaカードの読み取り、システムコード/サービスコードの指定、学籍番号の抽出ロジック
│   │   ├── EmailSender.kt           # SMTPを使用したメール送信処理（バックグラウンド実行）
│   │   └── LogAdapter.kt            # RecyclerViewにログメッセージを表示するためのアダプター
│   ├── res/                             # リソースファイル
│   │   ├── layout/
│   │   │   ├── activity_main.xml    # メイン画面のレイアウト定義
│   │   │   └── dialog_email_settings.xml # メール設定ダイアログのレイアウト定義
│   │   ├── values/
│   │   │   ├── colors.xml           # アプリケーションで使用する色の定義
│   │   │   ├── strings.xml          # アプリケーションで使用する文字列リソース
│   │   │   └── themes.xml           # アプリケーションのテーマ定義
│   │   ├── xml/
│   │   │   └── nfc_tech_filter.xml  # NFCタグ検出時にNfcF（Felica）技術のみをフィルタリングするための設定
│   │   └── drawable/                  # 画像リソース (アプリアイコンなど)
│   └── AndroidManifest.xml          # アプリケーションの基本情報、権限、NFCインテントフィルターなどを定義
├── build.gradle (Module :app)       # アプリモジュール固有のビルド設定 (依存ライブラリ、SDKバージョンなど)
build.gradle (Project)               # プロジェクト全体のビルド設定 (Gradleプラグインのバージョンなど)
```

## 主要クラスの詳細

### `MainActivity.kt`
- NFCタグが検出された際のインテントを処理します。
- `FelicaReader` を使用して学生証から学籍番号を読み取ります。
- 読み取った学籍番号とSharedPreferencesに保存された設定に基づき、`EmailSender` を呼び出してメールを送信します。
- UI（学籍番号の表示、ログメッセージの表示）を更新します。
- メール送信設定（送信元メールアドレス、アプリパスワード、件名、本文）をSharedPreferencesに保存・読み込みする機能を提供します。

### `FelicaReader.kt`
- `NfcAdapter.ReaderCallback` を実装し、NFCカードが検出されたときに呼び出されます。
- 指定されたシステムコード (`0xFE00`) とサービスコード (`0x1A8B`) を使用してFelicaカードにアクセスします。
- カードからデータを読み取り、学籍番号をShift_JISエンコーディングでデコードして抽出します。

### `EmailSender.kt`
- `javax.mail` ライブラリを使用してSMTP経由でメールを送信します。
- GmailのSMTPサーバー (`smtp.gmail.com`, ポート `587`) を使用します。
- 送信元メールアドレスとアプリパスワードによる認証を行います。
- メール送信処理はバックグラウンドスレッド (`CoroutineScope(Dispatchers.IO)`) で実行され、UIスレッドをブロックしません。

### `LogAdapter.kt`
- `RecyclerView.Adapter` を継承し、`MainActivity` で表示されるログメッセージのリストを管理・表示します。

## 設定

メール送信に必要な送信元Gmailアドレスとアプリパスワード、およびメールの件名と本文テンプレートは、アプリ起動後に表示される設定アイコンから設定できます。これらの設定はSharedPreferencesに保存されます。

**注意:** Gmailでメールを送信するには、2段階認証プロセスを有効にし、**アプリパスワード**を生成して使用する必要があります。通常のGmailパスワードでは動作しません。

## トラブルシューティング

### NFCが認識されない
- デバイスのNFC設定が有効になっているか確認してください。
- 学生証をデバイスのNFCアンテナ部分（通常は背面）に正しくタッチしているか確認してください。

### メール送信エラー
- インターネット接続が有効であることを確認してください。
- アプリ内で設定したGmailアドレスとアプリパスワードが正しいか確認してください。特にアプリパスワードが正しく生成・入力されているか注意してください。
- Gmailアカウントのセキュリティ設定で「安全性の低いアプリのアクセス」が（もし古い方法で試している場合）許可されているか確認してください（ただし、アプリパスワードの使用が推奨されます）。

## ライセンス

このプログラムは犯罪行為でなければご自由にお使いください。このプログラムを使用してのいかなる損害も私、endresserrorは負いかねません。またこのプログラムの著作権は放棄していません。

## 貢献

プルリクエストや問題報告を歓迎します。開発に参加される場合は、以下のガイドラインに従ってください：

1. このリポジトリをフォークして、新しいブランチを作成してください (`git checkout -b feature/your-feature-name`)。
2. 変更をコミットしてください (`git commit -am '''Add some feature'''`)。
3. リモートリポジトリにプッシュしてください (`git push origin feature/your-feature-name`)。
4. プルリクエストを作成してください。

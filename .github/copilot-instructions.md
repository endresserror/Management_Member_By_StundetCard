<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

# Student Card System - Android NFC Felica Reader

このプロジェクトは、AndroidでFelica技術を使用した学生証を読み取り、学籍番号に基づいてメールを送信するアプリケーションです。

## 技術仕様

- **NFC技術**: Felica (NfcF)
- **システムコード**: 0xFE00  
- **サービスコード**: 0x1A8B
- **メール送信**: SMTP経由でGmail送信
- **言語**: Kotlin
- **UIフレームワーク**: Android View System

## 主要機能

1. **NFC Felica読み取り**: 学生証から学籍番号を抽出
2. **メール送信**: 読み取った学籍番号に基づいて自動メール送信
3. **ログ機能**: リアルタイムでの操作ログ表示
4. **エラーハンドリング**: NFC読み取りエラーとメール送信エラーの適切な処理

## コード生成時の注意事項

- NFC関連のコードでは、適切な権限設定とエラーハンドリングを含める
- メール送信はバックグラウンドスレッドで実行する
- UI更新はメインスレッドで行う
- Felica固有のコマンド構造を理解して実装する

---
type: 手順
title: MusicSwitcher
description: Android端末でフォルダ内の音楽を再生し、URLから取得した音声も同じ場所に保存するアプリ。
---

# MusicSwitcher

Android 8.0以降の64ビット端末（ARM64またはx86-64）向けの音楽プレーヤーです。

1. **フォルダを選ぶ**で端末の音楽フォルダを選びます。サブフォルダの音声も曲一覧に表示されます。
2. 曲をタップすると再生します。下部のミニプレーヤーを開くと、ジャケット付きの再生画面で曲送りや再生位置の変更ができます。別のアプリに切り替えても再生は続きます。
3. **URLから保存**で、ダウンロードが許可されたコンテンツのHTTPSリンクを貼り付けます。音声は選択中のフォルダに `.m4a` で保存され、通信なしで再生できます。

フォルダの権限はアプリの再起動後も保持されます。内部ストレージのルート、`Download` フォルダそのもの、`Android/data` などはAndroidの仕様上選べません。書き込みやファイル名変更を許さないフォルダには保存できません。URLの取得可否は、サイトの仕様やアクセス制限に依存します。

初回はダーク表示です。ライブラリ右上のボタンでライト表示に切り替えられます。

## ビルド

Windowsでは `build.cmd` をダブルクリックするか、ターミナルで次を実行します。

```bat
build.cmd :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

新しい環境ではJDK 17とAndroid SDK 36を用意して `JAVA_HOME`・`ANDROID_HOME` を設定してください。初回はGradleと依存ライブラリの取得にインターネット接続が必要です。取得後は `--offline` でビルドできます。Windowsの `build.cmd` は、`.local/toolchain` にJDKとSDKがあればそちらを優先し、Gradleのキャッシュを `.local/gradle` に保存します。macOS/Linuxでは同じタスクを `sh ./gradlew` で実行できます。

生成先：`app/build/outputs/apk/debug/app-debug.apk`

Android 16の英語設定の使い捨てエミュレーターでは、`scripts/smoke_android.py` でフォルダ選択・ダウンロード・オフライン再生を検証できます。

```bat
python scripts/smoke_android.py --serial emulator-5556 --apk app/build/outputs/apk/debug/app-debug.apk
```

## 同梱ライブラリ

アプリの実行に外部のPython、Termux、FFmpegやサーバーは不要です。[yt-dlp 2026.10.26](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26) のzipimport配布物を固定し、ビルド時にSHA-256を照合します。Python、QuickJS、FFmpegは `youtubedl-android` 0.18.1 のAndroid向けライブラリに含まれます。

このリポジトリはソースとビルドに必要な資材のみを公開します。生成されたAPKは開発・手元検証用で、リポジトリには含まれません。APKを再配布する場合は、[同梱ライブラリのライセンス](NOTICE)と対応ソースの提供条件を確認してください。

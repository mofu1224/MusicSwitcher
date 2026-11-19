---
type: 手順
title: MusicSwitcher
description: Android端末でフォルダ内の音楽を再生し、URLから取得した音声も同じ場所に保存するアプリ。
---

# MusicSwitcher

Android 8.0以降の64ビット端末（ARM64またはx86-64）向けの音楽プレーヤーです。

1. **フォルダを選ぶ**で端末の音楽フォルダを選びます。サブフォルダの音声も曲一覧に表示されます。
2. 曲をタップすると再生します。下部のミニプレーヤーを開くと、ジャケット付きの再生画面で曲送りや再生位置の変更ができます。別のアプリに切り替えても再生は続きます。
3. **Import link** に保存が許可されたコンテンツのHTTPSリンクを貼り付けます。配信元の音声を再エンコードせず、選択中のフォルダに保存します。
4. 曲やライブラリ名を長押しすると、軽い振動とともに確認画面が開きます。曲の削除・ライブラリの登録解除は、この画面で確定します。並べ替えは右端の `≡` を置きたい位置までドラッグします。

フォルダの権限はアプリの再起動後も保持されます。内部ストレージのルート、`Download` フォルダそのもの、`Android/data` などはAndroidの仕様上選べません。書き込みやファイル名変更を許さないフォルダには保存できません。URLの取得可否は、サイトの仕様やアクセス制限に依存します。

初回はダーク表示です。ライブラリ右上のボタンでライト表示に切り替えられます。

## ビルド

Windowsでは `build.cmd` をダブルクリックするか、ターミナルで次を実行します。

```bat
build.cmd :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

新しい環境ではJDK 17とAndroid SDK 36を用意して `JAVA_HOME`・`ANDROID_HOME` を設定してください。初回はGradleと依存ライブラリの取得にインターネット接続が必要です。取得後は `--offline` でビルドできます。Windowsの `build.cmd` は、`.local/toolchain` にJDKとSDKがあればそちらを優先し、Gradleのキャッシュを `.local/gradle` に保存します。macOS/Linuxでは同じタスクを `sh ./gradlew` で実行できます。

生成先：`app/build/outputs/apk/debug/app-debug.apk`

初回リリースの版番号は `0.1.0`（versionCode `1`）です。`build.cmd :app:assembleRelease :app:testDebugUnitTest :app:lintRelease` で検査と未署名APKの生成ができます。配布用には Android SDK の `apksigner sign` で `app/build/outputs/apk/release/app-release-unsigned.apk` に署名し、`apksigner verify --verbose` で確認してください。以後の更新にも同じリリース鍵が必要です。

## 検証

英語設定の使い捨てAndroidエミュレーターを使います。`--serial` と `--avd` は検証端末の値に置き換えてください。テスト中にアプリを停止し、ネットワークや検証用フォルダを操作するため、普段使いの端末では実行しないでください。

```bat
python scripts/smoke_android.py --serial emulator-5558 --avd MusicSwitcherQA --apk app/build/outputs/apk/debug/app-debug.apk --player-ui --edge-cases
python scripts/test_android_edges.py --serial emulator-5558 --avd MusicSwitcherQA --apk app/build/outputs/apk/debug/app-debug.apk
python -m unittest discover -s scripts -p test_qa_tools.py
```

署名済みRelease APKを検証するときは `smoke_android.py` に `--release` を付けます。デバッグ専用のアプリ内部へのアクセスを使わず、APKに同梱した取得エンジンと実際に保存した音声を照合します。

ネイティブライブラリのページサイズ整列は `python scripts/check_native_alignment.py app/build/outputs/apk/debug/app-debug.apk` で検査できます。このコマンドはAPK内の圧縮ライブラリも確認します。

**16 KBページサイズの端末について：** Android 16の16 KBエミュレーターでは端末内の音声を再生できましたが、現在同梱するFFmpegの一部の依存ライブラリが4 KB整列のため、URLからの音声保存に失敗しました。4 KBのAndroid 17エミュレーターでは同じURLの保存と再生を確認しています。

## 同梱ライブラリ

アプリの実行に外部のPython、Termux、FFmpegやサーバーは不要です。[yt-dlp 2026.10.26](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26) のzipimport配布物を固定し、ビルド時にSHA-256を照合します。Python、QuickJS、FFmpegは `youtubedl-android` 0.18.1 のAndroid向けライブラリに含まれます。

## ライセンス

MusicSwitcher独自のソースコードは [MITライセンス](LICENSE) です。同梱・依存する第三者のソフトウェアには、それぞれのライセンスが適用されます。詳しくは [NOTICE](NOTICE) を参照してください。

署名済みAPKとSHA-256確認用ファイルは [v0.1.0のリリースページ](https://github.com/mofu1224/MusicSwitcher/releases/tag/v0.1.0) からダウンロードできます。ソースとビルド手順は同じタグにあります。

---
type: 手順
title: FFmpegに同梱するWebPの更新手順
---

# FFmpegに同梱するWebPの更新手順

`ffmpeg-webp16k.zip` は `youtubedl-android:ffmpeg:0.18.1` の中で4 KB整列だったWebP／SharpYUVの共有ライブラリだけを置き換えます。FFmpeg・FFprobeの実行ファイル、ほかの依存ライブラリ、シンボリックリンクは上流のAARからそのまま取り込みます。通常のビルドではこのZIPを使用するため、PythonやTermuxは不要です。

ZIPを再生成するときは、Termux公式リポジトリの `termux-main/pool/main/libw/libwebp/` から次の2ファイルを取得してください。ハッシュは `scripts/prepare_ffmpeg_webp.py` に固定されています。

- `libwebp_1.6.0.0.0.1+really1.6.0_aarch64.deb`
- `libwebp_1.6.0.0.0.1+really1.6.0_x86_64.deb`

```text
python scripts/prepare_ffmpeg_webp.py <aarch64.deb> <x86_64.deb> <new-output.zip>
```

Termuxの署名付きパッケージ一覧で配布物を照合し、差し替え時は元のライブラリとのABI互換性を確認してください。更新後はGradle側のハッシュを更新し、両ABIのAPK整列検査と、Androidの4 KB・16 KB環境での動画からの音声保存を確認します。使用したソースとライセンスは [NOTICE](../../NOTICE) に記載しています。

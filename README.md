# light_rumor

次世代プロフェッショナルRAW現像・写真編集スタジオ (Android)

[![Build & Release APK](https://github.com/minetan1229/light_rumor/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/minetan1229/light_rumor/actions/workflows/build-and-release.yml)
[![Release](https://img.shields.io/github/v/release/minetan1229/light_rumor?color=FF7900&label=APK%20Release)](https://github.com/minetan1229/light_rumor/releases/latest)

---

## 概要

**light_rumor** は、スマートフォン上で本格的なRAW現像と画像編集を実現する、完全オフラインのAndroid向け写真編集アプリケーションです。

AI任せの自動補正に頼らず、写真家自身が光と色彩を直感的にコントロールできる手動調整を重視。32-bit浮動小数点リニア色空間パイプライン、Vulkanコンピュートシェーダー、プロ向け測定計器（RGB波形モニターなど）を備え、スタジオワークからフィールド撮影まで高精度な編集環境を提供します。

---

## 主な機能

### 📷 RAW & 高画質画像処理
- **幅広いフォーマット対応**: 各社RAWファイル（ARW / CR2 / CR3 / NEF / DNG等）および汎用画像（JPEG / PNG / HEIC / WebP / TIFF）のデコード
- **高階調現像パイプライン**: 32-bit浮動小数点リニア色空間による高品質な現像処理
- **多様なエクスポート**: 最高品質JPEG、16-bit TIFF、Linear DNG、WebP出力、メタデータ（Exif）保持、撮影情報ウォーターマーク合成

### 🎨 本格的な現像パラメーター
- **基本補正**: 露出、コントラスト、ハイライト、シャドウ、白レベル、黒レベル、自然な彩度、明瞭度、かすみ除去（Dehaze）
- **カラー調整**: ホワイトバランス（ケルビン・色合い）、8バンドHSLカラーミキサー、3ウェイカラーホイール、本格モノクロ現像
- **トーンカーブ**: RGBマスターおよび各色チャンネルカーブ、直感的なターゲット調整ツール（TAT）
- **ディテール & 光学補正**: 輝度・カラーノイズ低減、マスキング付きシャープネス、レンズ補正（歪曲収差・周辺光量・色収差）
- **ジオメトリ**: 自由・固定比率トリミング、精密回転、パース補正

### 📊 プロフェッショナル測定計器
- **リアルタイム計器**: リアルタイムRGB波形モニター（Waveform）、RGBパレード、ベクトルスコープ、RGBヒストグラム
- **撮影アシスト**: フォルスカラー、ゼブラパターン、フォーカスピーキング

### 🖌️ 高度なマスク & レタッチ
- **多彩なマスク機能**: 線形グラデーション、円形/楕円、筆圧対応ブラシ、輝度マスク、カラーマスク
- **スポット修復**: 局所領域の不要物除去（コピースタンプ・修復ブラシ）

### ⚙️ 撮影現場支援 & ワークフロー
- **ゼロ遅延カリング & 選別**: 高速プレビュー、レーティング・カラーラベル付け、4画面連動等倍比較
- **効率的なワークフロー**: パラメーター一括同期（Batch Sync）、XMPサイドカー互換、プリセット管理
- **高度な合成（スタッキング）**: フォーカススタッキング（深度合成）、星景追尾スタック、長時間露光合成、多重露光
- **テザー撮影**: USB-C接続によるカメラテザー撮影対応

### 🔒 完全オフライン & プライバシー保護
- 外部通信権限（インターネット権限）を一切持たず、すべての処理が端末ローカルで完結します。

---

## ダウンロード & インストール

最新バージョンのAPKは [Releases](https://github.com/minetan1229/light_rumor/releases/latest) からダウンロードできます。

### ⬇️ [**light_rumor v1.1.6 をダウンロード (APK)**](https://github.com/minetan1229/light_rumor/releases/download/v1.1.6/light_rumor-v1.1.6.apk)

**最新バージョン: v1.1.6 更新内容**
- **HDRリニアパイプラインにおける数学的堅牢化 (`rgbToHsl`)**:
  - C++現像コア（`ExportPipeline.cpp`, `MaskEngine.cpp`）およびGLSLコンピュートシェーダー（`develop_pipeline.comp`, `basic_tone_vibrance.comp`, `color_mixer_bw.comp`, `mask_evaluation.comp`）において、露出補正後のHDRハイライト領域（RGB > 1.0）での0除算・NaN/INF発生および分母負数による彩度跳ね上がりを完全防止。
- **32-bit環境におけるヒープ破壊・脆弱性対策 (`RawDecoder.cpp`)**:
  - 32-bit ARM/x86環境下で不正な高解像度DNG/TIFF画像ヘッダー（65536×65536等）読み込み時に発生する整数ラップアラウンド（OOBアクセス）を完全に抑止。画素数と必要バイト数を `uint64_t` で事前検証し、上限チェックを強化。
- **マルチタッチジェスチャーとマスク描画の競合解消 (`BeforeAfterOverlay.kt`)**:
  - `awaitEachGesture` を用いたポインター数管理に刷新。マスク編集モード中における2本指ピンチズーム・パン操作と1本指マスク描画・ピン移動の競合を解消し、滑らかなタッチ操作を実現。
- **非同期画像ロードのレースコンディション解消 (`CullingScreen.kt`)**:
  - 高速スワイプ選別時、独立スレッドでの画像デコード遅延により古い写真が新しい写真のプレビューを上書き表示してしまう競合を `activeUri == targetUri` 検証により完全防止。
- **Android PowerManager WakeLock リーク防止 (`ExportService.kt`)**:
  - バッチエクスポート処理時におけるWakeLockの多重生成を単一管理に修正し、システムサーバー側でのロック残留による端末のバッテリー浪費を防止。
- **ポリゴンマスクのパラメータ参照整合性 (`mask_evaluation.comp`)**:
  - ポリゴンマスクのフェザー処理で誤って線形グラデーション用変数を参照していた問題を修正し、専用マクロ `#define u_polyFeather` による正確なエッジ処理を保証。
- **Adobe XMP/RDF 規格準拠 (`XmpSidecarManager.kt`)**:
  - Lightroom等が生成した要素形式（`<xmp:Rating>`）と属性形式（`xmp:Rating=""`）の二重挿入競合を解消し、相互運用性を向上。

### インストール手順
1. 上記リンクから最新の `.apk` ファイルを端末にダウンロードします。
2. ダウンロードしたファイルをタップして開きます。
3. 「提供元不明のアプリのインストール」の許可を求められた場合は、画面の案内に従って許可してください。
4. インストールを完了し、アプリを起動します。

> [!TIP]
> **完全オフライン動作**: インターネット通信権限を一切持たないため、データ通信量を消費せず、プライベートな写真も安心して編集できます。

---

## 動作要件

- **OS**: Android 8.0 (API レベル 26) 以上
- **推奨アーキテクチャ**: 64-bit ARM (arm64-v8a)

---

## ビルド方法

### 必要環境
- Android Studio（または Android SDK Command-line Tools）
- Android SDK (API 35)
- Android NDK (27.0.12077973)
- CMake 3.22.1 以上
- JDK 17 以上

### Android APK のビルド
```bash
# デバッグビルド
./gradlew assembleDebug

# リリースビルド
./gradlew assembleRelease
# 生成先: app/build/outputs/apk/release/
```

### デスクトップ用ネイティブテストのビルド & 実行 (任意)
```bash
cmake -B build -G Ninja
cmake --build build
ctest --test-dir build --output-on-failure
```

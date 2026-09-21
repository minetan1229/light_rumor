# light_rumor - 次世代プロフェッショナルRAW現像・写真編集スタジオ

[![Build & Release APK](https://github.com/minetan1229/light_rumor/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/minetan1229/light_rumor/actions/workflows/build-and-release.yml)
[![Release](https://img.shields.io/github/v/release/minetan1229/light_rumor?color=FF7900&label=APK%20Release)](https://github.com/minetan1229/light_rumor/releases/latest)

---

## ダウンロード
### ⬇️ [**light_rumor v1.1.1 をダウンロード（APK）**](https://github.com/minetan1229/light_rumor/raw/master/dist/light_rumor-v1.1.1.apk)

**最新バージョン: v1.1.1 更新内容**
- **端末情報・カメラ機種・レンズ・EXIFの自由変更＆実ファイル直接書き込み保存**: 端末情報（Sony, Canon, Nikon, Fujifilm, Leica, iPhone, Pixel等）やレンズ情報の候補チップ選択＆自由入力を追加。保存時はメモリ上だけでなく、**実際の画像ファイル自体のEXIFヘッダーおよびXMPサイドカーへ直接バイナリ書き込み**され永続保存されます
- **スライダー操作性の完全修復（ThumbZone）**: 水平ドラッグ専用基盤（`Modifier.draggable`）への刷新により、縦スクロールとの誤爆・競合を完全根絶。指を離した数値を確実に保持し、ドラッグ終了時のみ履歴を記録する軽快なGPUプレビューを実現
- **写真上での直観的マスク操作＆「欠けたところ」色分け表示**: マスクモード時の外周タッチ競合を解消し、写真上でのピン移動・半径変更・ブラシ直接描画が100%確実に追従。マスク適用領域（赤）と欠けたところ（青/シアン）の二色同時色分け表示に対応
- **カラープロファイル実写サムネイル＆モノクロ統合**: 現在の写真に各プロファイルや白黒フィルターを適用した姿をリアルタイムに縮小サムネイル表示。カラー現像とモノクロ現像の統合スイッチにより散乱していた設定を一元化
- **端末写真「コレクション」タブ＆まとめて一括選択**: Androidネイティブ高速サムネイルAPI（`loadThumbnail`）を活用し、端末内写真の0ms表示と複数枚一括オープンに対応
- **EXIFの捏造表示完全撤廃＆候補チップUI**: ダミー値の表示を全廃し、F値（`f/1.2`〜`f/22`）、SS、ISOのチップUIを導入

Android 8.0（API 26）以降、**全6フェーズのプロフェッショナル機能すべてが使えます**。

**入れかた**

1. 上のリンクをスマホで開いて APK を保存する
2. 保存したファイルをタップする
3. 「提供元不明のアプリ」の許可を求められたら許可する
   （Android 8 以降はアプリごとの許可。ブラウザやファイル管理アプリに出す）
4. インストールして開く

> [!TIP]
> **完全オフライン動作**: 外部通信権限（インターネット権限）を一切持たず、RAW現像から色の波（RGB波形モニター）、9種の手動マスク、スタッキング合成まで端末ローカルで高速動作します。
>
> [!IMPORTANT]
> **先行配布版（APK直接共有）**: デバッグ鍵で署名されているため、端末のセキュリティ警告が出た場合は「続行 / インストール」を選択してください。将来 Google Play 版を導入する場合は一度アンインストールが必要になります。

---

## 概要

**light_rumor** は、Adobe Lightroom Classic、Capture One、DaVinci Resolveの強みを統合し、完全オフラインで動作する次世代RAW & 汎用画像現像・写真編集アプリケーションです。

RAW（ARW / CR3 / NEF / DNG等）だけでなく一般的なJPEG/PNG/HEIC画像への完全対応、ワンタップ写真許可Photo Picker、色の波（RGB波形モニター）、CSSアニメーション物理イージング、AI特有の安っぽいデザインの完全排除、トリミング・回転・ターゲット調整ツール・レンズデータベース・プリセット同期・テザー撮影・スタッキング・印刷ソフトプルーフまで、プロが求める全6フェーズの全機能を実装レベルで体系化しています。

---

## 開発思想 & 厳格な規範
1. **完全手動・プロフェッショナル制御**: AIによる自動補正やおまかせ機能に頼らず、すべての光・色彩・数学的合成を写真家自身がコントロール可能。
2. **AI風デザインの完全排除（Anti-AI Aesthetic）**: ネオングラデーション、無駄な角丸、グラスモーフィズム、キラキラアイコンを厳禁。ライカやハッセルブラッドのようなマットブラック（#0A0A0C）とチタングレーの硬派な測定計器デザインを徹底。
3. **CSSアニメーション & 物理イージング**: cubic-bezierによる機械的で吸い付くような操作モーションと120Hz追従。
4. **RAW & 汎用画像（JPEG/HEIC/PNG）の統一高画質現像**: スマホ撮影の非RAW写真も32bitリニア色空間で階調豊かに現像。
5. **超低摩擦な写真許可**: Android Photo Pickerにより、面倒なストレージ権限なしで写真を選ぶだけで即座に現像開始。
6. **シネマ級の計器「色の波」**: RGB波形モニター（Waveform / RGB Parade）で光と色の分布をリアルタイム可視化。
7. **絵文字の完全排除と実写サムネイルの採用**: UIおよびシステム全体で絵文字アイコンの使用を一切禁止。実写写真サムネイルおよび精密な計器グラフィックを採用。
8. **段階的実機査定（Phase-by-Phase Verification）**: 全6フェーズの査定基準（Acceptance Criteria）をネイティブテストで100%パス。
9. **完全オフライン & 速度最優先**: 通信を一切行わず、C++20 + Vulkan Compute Shaderによる32bit浮動小数点リニア演算で高速出力を維持。

---

## 全フェーズ実装機能一覧（全6フェーズ完了）

* [phase1.md](./phase1.md): 【Phase 1】画像入出力・RAW & 汎用画像現像・高画質エクスポートエンジン
  * RAW（ARW/CR3/NEF/DNG等）および非RAW（JPEG/HEIC/PNG/WebP/TIFF）対応、タイル分割レンダリング（OOM完全回避）、32bitリニア現像、色空間変換、TPDFディザリング、JPEG(4:4:4)/16bit TIFF/WebP/DNG出力、Exif完全保持、Foreground Service。
* [phase2.md](./phase2.md): 【Phase 2】OS即時起動Intent・Photo Pickerワンタップ許可・ゼロ遅延カリング・カタログ・バッチ同期
  * Photo Pickerによるワンタップ写真アクセス、撮影時/保存時の即時起動Intent、RAW内包JPEG非同期0msカリング、レーティング評価、4画面連動100%等倍ズーム比較、選択パラメータ一括同期（Batch Sync）。
* [phase3.md](./phase3.md): 【Phase 3】冒険写真家の計器デザイン・色の波（RGB波形モニター）・人間工学サムゾーンUI
  * AI風デザイン排除規範、CSSアニメーション物理イージング、色の波（リアルタイムRGB波形モニター / RGB Parade）、Obsidian Black（純黒#0A0A0C）＆ Arctic Minimal（白）、実写写真サムネイルUI、下部35%集約サムゾーン、フリック＋±0.01精密回転ダイヤル、ゼロ点触覚フィードバック（Haptics）、RGBヒストグラム直接ドラッグ、4種のBefore/After比較。
* [phase4.md](./phase4.md): 【Phase 4】トリミング・変形・全現像コア・60fps Vulkanパイプライン
  * 自由/固定アスペクト比クロップ（XPan等対応）、水平出しルーラー、反転、ガイド付き変形パース補正。
  * WB（Kelvin/Tint/スポイト）、原色キャリブレーション、基本トーン、自然な彩度、ガイドフィルタ明瞭度/テクスチャ/Dehaze。
  * 8色カラーミキサー（HSL）、本格モノクロ現像（実写フィルター作例サムネイル表示）。
  * 輝度ノイズ低減、カラーノイズ低減、マスキング付きシャープネス。
  * トーンカーブ（RGBマスター＋各色＋パラメトリック）＆写真直感ターゲット調整ツール（TAT）。
  * 3ウェイカラーホイール、3D LUT(.cube)、Lensfunレンズ補正。
* [phase5.md](./phase5.md): 【Phase 5】超拡張手動マスク・レタッチ・XMPプリセット・無制限リール巻き戻し
  * 9種の手動マスク（線形、円形/楕円、ベジェ/多角形、筆圧ブラシ、輝度、カラー、Depth、エッジ、ブール演算）。
  * コピースタンプ、手動テクスチャ修復ブラシ。
  * XMPプリセット完全互換インポート/エクスポート、実写プレビューサムネイル付きプリセットブラウザ、適用量スライダー（0〜200%）。
  * アナログリール風タイムラインUndo/Redo、履歴ツリー分岐。
* [phase6.md](./phase6.md): 【Phase 6】撮影現場支援計器・数学スタッキング・カラーチェッカー・プロマスター出力
  * USB-Cテザー撮影、フォルスカラー、ゼブラパターン、フォーカスピーキング、波形モニター/RGBパレード/ベクトルスコープ。
  * フォーカススタッキング（深度合成）、星景追尾スタック、ND不要長時間露光合成、ピクセルシフト超解像、多重露光。
  * 24色カラーチェッカー測定、ICCソフトプルーフ、マルチレシピ一括書き出し、Exif自動電子透かし印字。

---

## ビルド手順

### 1. Android APKビルド (Gradle)
```bash
./gradlew assembleRelease
# 生成先: app/build/outputs/apk/release/light_rumor.apk
```

### 2. デスクトップネイティブテスト実行 (C++20 / CMake)
```bash
cmake -B build -G Ninja
cmake --build build --target light_rumor_phase6_test
./build/light_rumor_phase6_test
```

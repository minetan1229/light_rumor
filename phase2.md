# Task: 【Phase 2】OS即時起動Intent・Photo Pickerワンタップ許可・ゼロ遅延カリング・カタログ・バッチ同期

## 1. 目的 & ゴール
ユーザーが面倒な権限設定に迷うことなく、**Android標準のPhoto Pickerで写真を選択・許可するだけで即座に編集を開始できる導線**、およびカメラ撮影時やストレージ保存時に本アプリが即座に編集候補の最上位として立ち上がるOS連携、さらにRAWおよび非RAW（JPEG/HEIC等）を待機時間0ミリ秒でめくってセレクトできる**「超高速カリングシステム」**を実装します。

---

## 2. 必須技術スタック & 制約
* ターゲット環境: Android (Google Pixel 9a 最適化) ＋ PC (デスクトップビルド)
* 実行環境: 完全オフライン（ローカルファイルのみを対象）
* 写真アクセス導線: Android 13/14+ 最新 `ActivityResultContracts.PickVisualMedia` (Photo Picker)
* UIフレームワーク: Compose Multiplatform (Jetpack Compose / Desktop Compose)
* キャッシュエンジン: 双方向LRUキャッシュ（メモリ ＋ ディスクSSDキャッシュ）
* ファイル監視: Android FileObserver / MediaStore ContentObserver

---

## 3. 実装要件（Photo Access, Culling & Workflow Specifications）

### ① ワンタップ写真許可 & Photo Picker連携（超低摩擦オンボーディング）
* アプリ起動時、または「写真を開く」タップ時に、Android標準の `PickVisualMedia`（単一選択）および `PickMultipleVisualMedia`（複数選択）を呼び出し。
* 従来のストレージ全体アクセス権限（`MANAGE_EXTERNAL_STORAGE`等）を一切要求せず、**ユーザーが選択した写真だけをOSが安全に一時アクセス許可する最新のプライバシー準拠設計**。
* ユーザーがギャラリーから写真をタップした瞬間、0.1秒以内に現像プレビュー画面へシームレスに遷移。
* PC版（Desktop）では、洗練されたネイティブファイルダイアログおよびドラッグ＆ドロップ受け入れを完全サポート。

### ② OS即時起動 Intent Filter（撮影・保存時のシームレスな起動）
* Android Manifestに以下のIntent Filterを完全登録：
  * `android.intent.action.SEND` (MIMEタイプ: `image/*`)
  * `android.intent.action.SEND_MULTIPLE` (複数画像の一括受け取り)
  * `android.intent.action.EDIT` (編集アプリとしての起動候補)
  * `android.intent.action.VIEW` (画像ビューワーとしての起動)
* Pixel CameraやProCamなどの他社カメラアプリで撮影直後に「共有」または「編集」を押した際、本アプリが候補の最上位に表示され、選択後0.2秒以内に編集画面へ遷移すること。

### ③ ゼロ遅延カリングエンジン（RAW & 汎用画像対応）
* RAWファイル内部の埋め込みプレビューJPEG、および通常のJPEG/PNG/HEICサムネイルを非同期先読みキャッシュ。
* 前後5枚の画像を常時メモリキャッシュし、フリック時の待機時間0ミリ秒（ローディングスピナーなし）の超高速めくりを実現。
* プロ用レーティング操作:
  * スワイプジェスチャーまたは画面タップで、★1〜5評価、採用/除外フラグ、5色カラーラベルを即時付与。
  * 各種メタデータを非破壊サイドカー（.xmp）に即座に非同期永続化。

### ④ 4画面連動同期比較（Sync Zoom Comparison）
* 似たような連写カットを2画面または最大4画面で分割並列表示。
* 1本の指でピンチズーム・パン（移動）した際、全画面が完全に連動して同一のピクセル座標を100%等倍ズーム。
* 微妙な被写体ブレやピントの甘さを即座に見極め、ベストショットだけを残すプロの現場作業を短縮。

### ⑤ 選択パラメータ一括同期（Batch Sync）
* 1枚編集したパラメータをコピーし、複数枚の写真に一括ペースト。
* 全パラメータを上書きするのではなく、同期したい項目（例: 「ホワイトバランスとレンズ補正のみ」「トーンカーブのみ」「クロップは除外」など）をチェックボックスで個別に指定して一括適用。

---

## 4. 出力成果物（生成すべきコード）
1. 写真アクセス & 起動ハンドラ:
   * PhotoPickerLauncher.kt: 最新Photo Picker契約（Single/Multiple）とURIパーミッション保持管理。
   * AndroidManifest.xml: Intent Filter定義。
   * IntentHandlerActivity.kt: 渡されたURI（単一/複数）を解析し、適切な画面へルーティング。
2. カリングエンジン (Kotlin / Coroutines):
   * ThumbnailLoader.kt: RAWおよび非RAW画像の高速埋め込みサムネイル抽出器。
   * CullingCacheManager.kt: 前後プリフェッチとLRUメモリキャッシュ。
3. Compose UI コンポーネント:
   * QuickOpenScreen.kt: 写真許可・Photo Picker呼び出しエントリー画面。
   * CullingScreen.kt: ゼロ遅延めくり、★/フラグ付与ジェスチャー。
   * MultiCompareView.kt: 2画面/4画面連動同期ズームコンポーネント。
   * BatchSyncDialog.kt: 同期項目選択モーダルダイアログ。

---

## 5. 動作査定基準（Acceptance Criteria）
* [ ] アプリ内の「写真を開く」を押すと、Android標準のPhoto Pickerが瞬時に開き、選択した写真の編集画面へ迷わず遷移できること。
* [ ] 端末の標準カメラから写真撮影後、「共有」ボタンから本アプリが即座に起動し、画像が表示されること。
* [ ] RAWだけでなくJPEG/HEIC画像も、連続スワイプ時に一切の読み込み待機なくスムーズに表示されること。
* [ ] 4画面比較モードで、ピンチズーム時に4枚の画像が遅延なく同期して拡大・移動すること。
* [ ] 1枚の写真の調整を別写真に一括同期（Sync）した際、選択した項目だけが正確に反映されること。

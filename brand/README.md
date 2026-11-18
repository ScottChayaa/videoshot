# VideoShot 標誌套件 v1.0

「書籤那一秒」：把讓你停下來的那一秒存成書籤。書籤裡是播放鍵，底部缺口是 V。

## 色彩
| 名稱 | HEX | RGB | CMYK（換算） | Pantone（近似，需比對色票） |
|---|---|---|---|---|
| 暖炭 Warm Charcoal | #1D1A17 | 29 26 23 | 0 10 21 89 | ≈ Black 4 C |
| 暖黃 Warm Yellow | #FFBE3D | 255 190 61 | 0 25 76 0 | ≈ 1235 C |
| 奶油白 Cream | #FFF7EA | 255 247 234 | 0 3 8 0 | — |

## 資料夾
- `01-symbol/`：符號（yellow／charcoal／black／white），以及 32 px 以下用的 `symbol-small*`
- `02-horizontal/`：橫式圖文組合（預設版本）；`logo-horizontal-compact-*` 是精簡版，符號只有字高的 1.8 倍（預設版約 2.4 倍），給網頁標頭這類扁長的位置
- `03-stacked/`：直式圖文組合
- `04-wordmark/`：字標
- `05-app-and-web-icons/`：App 圖示、favicon.ico／svg、apple-touch-icon、PWA 圖示、`site.webmanifest`、`head-snippet.html`
- `06-presentation/`：應用示意簡報（HTML，另附每頁 PNG）
- `VideoShot-brand-guidelines.pdf`：一頁使用規範；原始檔是同名的 `.html`（直接引用各資料夾的 SVG），改完用 Chrome 列印成 PDF（A4、邊界無、背景圖形）

## 版本怎麼選
- `-charcoal`：淺色背景
- `-on-dark`：深色背景（黃色符號＋奶油白字，背景透明）
- `-on-dark-bg`：已帶暖炭色背景
- `-black` / `-white`：單色印刷、影片或照片浮水印

## 備註
- 每個檔案都有 SVG 向量母檔，PNG 是透明背景的點陣輸出。
- 字標以 Nunito Bold（SIL OFL，Google Fonts 免費下載）排版，字距 −0.5%，V 與 i 之間另收緊 0.1 em（Nunito 的 V 斜筆離 i 太遠），已轉成外框路徑，不需要安裝字型。字高、基線與邊界沿用原本 Poppins 版，寬度跟著文字變窄。
- 正式註冊或大量使用前，建議先做專業的商標檢索。

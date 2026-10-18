# 背屏工坊（BackScreen Studio）

STEPX Neo 点阵背屏的内容工作站：自定义文字、二维码、像素图，并把 Codex 状态搬上背屏预览。

- 包名：`com.stepx.backscreen`
- 版本：1.1.0（versionCode 2）
- minSdk / targetSdk：23 / 34
- 体积：约 257 KB（含 ZXing 二维码库）
- 无 AndroidX、无 Gradle：`aapt2 + javac + d8 + apksigner` 直接出包

## 功能

### 1. 文字
任意文本（含中文、多行），用系统字库渲染成高分辨率位图后按背屏点阵二值化采样，
所以中文不会被缺字库卡住；可切换粗体，字号自动适配行数与点阵规格。

### 2. 二维码
- **识别**：从相册选一张二维码图片，ZXing 解码出内容；
- **重生成**：按当前点阵规格（16/24/32/48）重新编码，优先最高纠错等级、
  最大静区，模块数放不下时自动降级并给出提示；
- 点阵屏容量有限：32×32 最多容纳 25 模块（约 32 字节级别的内容），
  长链接会提示「内容过长」，可加大点阵或缩短内容。

### 3. 像素图
- 直接在网格上画（画笔 / 橡皮，支持拖动连续绘制）；
- 从相册导入图片转点阵：居中裁剪 + 区域平均采样 + 可调阈值 + Bayer 抖动；
- 每 8 格辅助线，方便对齐图案。

### 4. Codex 状态
读取一份很轻的 JSON：

```json
{
  "active": 2,
  "waiting": 0,
  "status": "developing",
  "detail": "ecom-after-sales-local",
  "updated": "2026-10-18T20:05:00+08:00"
}
```

- `active`：进行中会话数量
- `status`：`developing`（开发中）/ `waiting`（等待人工回答）/ `idle`（摸鱼中）
- 打开「背屏优先显示 Codex 状态」后，预览会自动切成状态画面（图形 + 会话数 + 状态词），每 60 秒刷新一次
- 数据源地址可在「状态」「设置」里改，默认指向 `amoo-status` 仓库的 `status.json`

### 5. 接口探测（1.1.0 新增）
枚举全机的 ContentProvider / Service / Receiver，按「背屏、点阵、LED 矩阵、
secondary display」等关键字筛出相关组件：
- 命中即说明系统已把接口开放给应用，可继续对接；
- 未命中说明这一版仍未开放；
- 报告自动写入 `Download/BackScreenProbe.txt`，设置页可重扫、复制、导出。

同一页还提供「尝试推送」（向命中的导出组件投递点阵数据）和「预览通知」
（把点阵作为大图通知发出，适用于背屏展示关键通知的机型）。

### 6. 输出
「保存点阵图」把当前画面渲染成 PNG（每格 20px、带光晕）写入相册
`Pictures/BackScreen`，可直接分享，或在系统背屏自定义 / Step Studio 作品里选用。

## 背屏写入的现状

截至 2026-10，Step AOS 的点阵背屏只对系统服务与 Step Studio 运行时开放，
普通第三方 APK 没有公开的写入接口（原子能力平台 360 项能力里也没有背屏相关项）。
因此本应用把「生成 + 预览 + 导出」做全，写入通道留成 `BackScreenSink`：
后续系统一旦开放，只需在这里补一个实现，其余部分不用改。

## 构建

```bash
# 依赖：JDK 17、Android SDK build-tools 34.0.0 + platforms/android-34
KEYSTORE=/path/to/keystore.jks KS_ALIAS=alias KS_PASS=口令 \
VERSION_CODE=1 VERSION_NAME=1.0.0 bash build.sh
```

产物：`out/BackScreen.apk`

## 更新记录

### 1.1.0
- 新增背屏接口探测：全机组件扫描、报告落盘 `Download/BackScreenProbe.txt`、启动自动扫描
- 新增组件投递尝试（receiver / service / provider 三类）
- 新增通知镜像预览（BigPictureStyle），走「背屏展示关键通知」这条链路
- 新增 `QUERY_ALL_PACKAGES`、`POST_NOTIFICATIONS` 权限

### 1.0.0
- 首个版本：文字 / 二维码 / 像素图 / Codex 状态，点阵预览与导出

## 结构

```
AndroidManifest.xml
build.sh                       # 免 Gradle 构建
libs/zxing-core-3.5.3.jar      # 二维码编解码（纯 Java）
res/                           # 应用名、主题、多密度图标
src/com/stepx/backscreen/
  MainActivity.java            # 页面与交互
  Matrix.java                  # 点阵模型、文本/图片转换、渲染
  DotMatrixView.java           # 背屏预览
  PixelEditorView.java         # 像素画布
  QrUtil.java                  # 二维码识别与重生成
  StatusClient.java            # Codex 状态拉取
  BackScreenSink.java          # 输出（相册导出 / 分享）
  Store.java / Ui.java         # 配置与视觉
```

配套的 `status/` 目录（不含在 APK 内）是状态发布器：`statusd.py` 采集会话活动并生成
`status.json`，`statusctl.py` 供 Amoo 主动上报状态。

package com.stepx.backscreen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int[] SIZE_OPTIONS = {16, 24, 32, 48};
    private static final String[] MODE_NAMES = {"文字", "二维码", "像素", "状态", "设置"};
    private static final int REQ_QR_IMAGE = 1001;
    private static final int REQ_PIXEL_IMAGE = 1002;

    private Store store;
    private DotMatrixView preview;
    private TextView hintView;
    private TextView sizeChip;
    private LinearLayout chipsRow;
    private LinearLayout panelHost;
    private final List<TextView> chipViews = new ArrayList<>();
    private Matrix pixelMatrix;
    private StatusClient.Snapshot snapshot = new StatusClient.Snapshot();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean statusRefreshRunning;
    private final Runnable statusLoop = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing()) refreshStatus(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        pixelMatrix = decodePixel(store.pixel(), store.cols(), store.rows());
        buildUi();
        applyMode(store.mode());
        restoreCachedStatus();
        refreshStatusIfNeeded();
        handler.postDelayed(this::runProbe, 1200);
    }

    // ------------------------------------------------------------- 界面搭建

    private void buildUi() {
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout content = Ui.column(this);
        int pad = (int) Ui.dp(this, 18);
        content.setPadding(pad, (int) Ui.dp(this, 16), pad, (int) Ui.dp(this, 28));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题行
        LinearLayout titleRow = Ui.row(this, 0);
        TextView title = Ui.label(this, "背屏工坊", 22, Ui.TEXT, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sizeChip = Ui.label(this, sizeText(), 13, Ui.MUTED, false);
        sizeChip.setPadding((int) Ui.dp(this, 10), (int) Ui.dp(this, 6),
                (int) Ui.dp(this, 10), (int) Ui.dp(this, 6));
        sizeChip.setBackground(Ui.roundStroke(this, 10, Ui.CARD, 0xFF2A2E35, 1));
        sizeChip.setOnClickListener(v -> showSizePicker());
        titleRow.addView(sizeChip);
        content.addView(titleRow);

        TextView sub = Ui.label(this, "点阵预览 · 自定义文字 / 二维码 / 像素图 · Codex 状态", 12, Ui.MUTED, false);
        sub.setPadding(0, (int) Ui.dp(this, 6), 0, 0);
        content.addView(sub);

        // 预览
        FrameLayout previewWrap = new FrameLayout(this);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapLp.topMargin = (int) Ui.dp(this, 16);
        content.addView(previewWrap, wrapLp);

        preview = new DotMatrixView(this);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int size = Math.min(screenW - pad * 2, (int) Ui.dp(this, 340));
        previewWrap.addView(preview, new FrameLayout.LayoutParams(size, size, Gravity.CENTER_HORIZONTAL));

        hintView = Ui.label(this, "", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = (int) Ui.dp(this, 10);
        content.addView(hintView, hintLp);

        // 模式切换
        chipsRow = Ui.row(this, 0);
        chipsRow.setPadding(0, (int) Ui.dp(this, 18), 0, 0);
        for (int i = 0; i < MODE_NAMES.length; i++) {
            final int idx = i;
            TextView chip = Ui.label(this, MODE_NAMES[i], 13, Ui.MUTED, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, (int) Ui.dp(this, 9), 0, (int) Ui.dp(this, 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = (int) Ui.dp(this, 8);
            chipsRow.addView(chip, lp);
            chip.setOnClickListener(v -> applyMode(idx));
            chipViews.add(chip);
        }
        content.addView(chipsRow);

        // 面板容器
        panelHost = Ui.column(this);
        LinearLayout.LayoutParams phLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        phLp.topMargin = (int) Ui.dp(this, 14);
        content.addView(panelHost, phLp);

        // 跟随状态
        LinearLayout followRow = Ui.row(this, 0);
        followRow.setPadding((int) Ui.dp(this, 14), (int) Ui.dp(this, 4),
                (int) Ui.dp(this, 12), (int) Ui.dp(this, 4));
        followRow.setBackground(Ui.round(this, 14, Ui.CARD));
        TextView followLabel = Ui.label(this, "背屏优先显示 Codex 状态", 14, Ui.TEXT, false);
        followRow.addView(followLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(store.followStatus());
        sw.setOnCheckedChangeListener((b, checked) -> {
            store.setFollowStatus(checked);
            refreshPreview();
        });
        followRow.addView(sw);
        LinearLayout.LayoutParams frLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        frLp.topMargin = (int) Ui.dp(this, 18);
        content.addView(followRow, frLp);

        // 主按钮
        TextView push = Ui.button(this, "保存点阵图", true);
        push.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pushLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Ui.dp(this, 50));
        pushLp.topMargin = (int) Ui.dp(this, 12);
        content.addView(push, pushLp);
        push.setOnClickListener(v -> exportCurrent());

        setContentView(scroll);
    }

    private String sizeText() {
        return store.cols() + " × " + store.rows() + "  ⌄";
    }

    private void showSizePicker() {
        String[] items = new String[SIZE_OPTIONS.length];
        for (int i = 0; i < SIZE_OPTIONS.length; i++) {
            items[i] = SIZE_OPTIONS[i] + " × " + SIZE_OPTIONS[i];
        }
        new AlertDialog.Builder(this)
                .setTitle("点阵规格")
                .setItems(items, (d, which) -> {
                    int s = SIZE_OPTIONS[which];
                    store.setSize(s, s);
                    pixelMatrix = pixelMatrix.resized(s, s);
                    store.setPixel(encodePixel(pixelMatrix));
                    sizeChip.setText(sizeText());
                    applyMode(store.mode());
                    refreshPreview();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyMode(int mode) {
        store.setMode(mode);
        for (int i = 0; i < chipViews.size(); i++) {
            boolean active = i == mode;
            TextView chip = chipViews.get(i);
            chip.setTextColor(active ? 0xFF160B00 : Ui.MUTED);
            chip.setBackground(active ? Ui.round(this, 10, Ui.ACCENT) : Ui.round(this, 10, Ui.CARD));
        }
        panelHost.removeAllViews();
        View panel;
        switch (mode) {
            case 0:
                panel = buildTextPanel();
                break;
            case 1:
                panel = buildQrPanel();
                break;
            case 2:
                panel = buildPixelPanel();
                break;
            case 3:
                panel = buildStatusPanel();
                break;
            default:
                panel = buildSettingsPanel();
                break;
        }
        panelHost.addView(panel);
        refreshPreview();
    }

    private LinearLayout card() {
        LinearLayout l = Ui.column(this);
        int p = (int) Ui.dp(this, 14);
        l.setPadding(p, p, p, p);
        l.setBackground(Ui.round(this, 16, Ui.CARD));
        return l;
    }

    // ------------------------------------------------------------- 各面板

    private View buildTextPanel() {
        LinearLayout card = card();
        TextView label = Ui.label(this, "显示文本", 13, Ui.MUTED, false);
        card.addView(label);

        EditText et = new EditText(this);
        et.setText(store.text());
        et.setSelection(et.getText().length());
        et.setTextColor(Ui.TEXT);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setHintTextColor(Ui.MUTED);
        et.setHint("输入要显示在背屏上的文字，支持换行");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setBackground(Ui.round(this, 12, Ui.CARD_2));
        et.setPadding((int) Ui.dp(this, 12), (int) Ui.dp(this, 10),
                (int) Ui.dp(this, 12), (int) Ui.dp(this, 10));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = (int) Ui.dp(this, 8);
        card.addView(et, etLp);
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                store.setText(s.toString());
                refreshPreview();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        LinearLayout tools = Ui.row(this, 0);
        TextView bold = Ui.button(this, store.bold() ? "粗体：开" : "粗体：关", false);
        bold.setOnClickListener(v -> {
            store.setBold(!store.bold());
            bold.setText(store.bold() ? "粗体：开" : "粗体：关");
            refreshPreview();
        });
        tools.addView(bold);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toolsLp.topMargin = (int) Ui.dp(this, 10);
        card.addView(tools, toolsLp);
        return card;
    }

    private View buildQrPanel() {
        LinearLayout card = card();
        card.addView(Ui.label(this, "二维码内容", 13, Ui.MUTED, false));

        TextView content = Ui.label(this, store.qrContent(), 14, Ui.TEXT, false);
        content.setPadding((int) Ui.dp(this, 12), (int) Ui.dp(this, 11),
                (int) Ui.dp(this, 12), (int) Ui.dp(this, 11));
        content.setBackground(Ui.round(this, 12, Ui.CARD_2));
        content.setMaxLines(3);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = (int) Ui.dp(this, 8);
        card.addView(content, cLp);

        LinearLayout row = Ui.row(this, 0);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = (int) Ui.dp(this, 10);
        TextView edit = Ui.button(this, "编辑内容", false);
        edit.setOnClickListener(v -> editQrContent(content));
        row.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView scan = Ui.button(this, "识别图片", true);
        scan.setOnClickListener(v -> pickImage(REQ_QR_IMAGE));
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        scanLp.leftMargin = (int) Ui.dp(this, 10);
        row.addView(scan, scanLp);
        card.addView(row, rowLp);

        TextView info = Ui.label(this, "", 12, Ui.MUTED, false);
        info.setPadding(0, (int) Ui.dp(this, 10), 0, 0);
        card.addView(info);
        updateQrInfo(info);
        qrInfoView = info;
        return card;
    }

    private TextView qrInfoView;

    private void updateQrInfo(TextView info) {
        QrUtil.Encoded e = QrUtil.encode(store.qrContent(), store.cols(), store.rows());
        String text;
        if (store.qrContent().trim().isEmpty()) {
            text = "输入内容后自动生成二维码";
        } else if (!e.fits) {
            text = "内容过长：" + e.moduleCount + " 模块放不进 " + store.cols() + " 格屏，请缩短内容或选更大的点阵";
        } else {
            text = "当前 " + e.moduleCount + "×" + e.moduleCount + " 模块 · 静区 " + e.quietZone
                    + " 格 · 纠错 " + e.level;
        }
        if (info != null) info.setText(text);
    }

    private void editQrContent(TextView contentView) {
        EditText et = new EditText(this);
        et.setText(store.qrContent());
        et.setTextColor(Ui.TEXT);
        et.setHint("链接、文本或任意可编码内容");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this)
                .setTitle("二维码内容")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    store.setQrContent(et.getText().toString().trim());
                    contentView.setText(store.qrContent());
                    updateQrInfo(qrInfoView);
                    refreshPreview();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View buildPixelPanel() {
        LinearLayout card = card();
        card.addView(Ui.label(this, "点阵画布", 13, Ui.MUTED, false));

        PixelEditorView editor = new PixelEditorView(this);
        editor.setMatrix(pixelMatrix);
        editor.setListener(m -> {
            store.setPixel(encodePixel(m));
            refreshPreview();
        });
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        eLp.topMargin = (int) Ui.dp(this, 10);
        card.addView(editor, eLp);

        LinearLayout row = Ui.row(this, 0);
        TextView[] tools = new TextView[4];
        tools[0] = Ui.button(this, "画笔", true);
        tools[1] = Ui.button(this, "橡皮", false);
        tools[2] = Ui.button(this, "清空", false);
        tools[3] = Ui.button(this, "导入图片", false);
        tools[0].setOnClickListener(v -> {
            editor.setDrawMode(true);
            tools[0].setBackground(Ui.round(this, 12, Ui.ACCENT));
            tools[0].setTextColor(0xFF160B00);
            tools[1].setBackground(Ui.roundStroke(this, 12, Ui.CARD_2, 0xFF2A2E35, 1));
            tools[1].setTextColor(Ui.TEXT);
        });
        tools[1].setOnClickListener(v -> {
            editor.setDrawMode(false);
            tools[1].setBackground(Ui.round(this, 12, Ui.ACCENT));
            tools[1].setTextColor(0xFF160B00);
            tools[0].setBackground(Ui.roundStroke(this, 12, Ui.CARD_2, 0xFF2A2E35, 1));
            tools[0].setTextColor(Ui.TEXT);
        });
        tools[2].setOnClickListener(v -> {
            pixelMatrix.clear();
            editor.setMatrix(pixelMatrix);
            store.setPixel(encodePixel(pixelMatrix));
            refreshPreview();
        });
        tools[3].setOnClickListener(v -> pickImage(REQ_PIXEL_IMAGE));

        for (int i = 0; i < tools.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = (int) Ui.dp(this, 8);
            row.addView(tools[i], lp);
        }
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = (int) Ui.dp(this, 10);
        card.addView(row, rowLp);
        return card;
    }

    private View buildStatusPanel() {
        LinearLayout card = card();
        card.addView(Ui.label(this, "Amoo · Codex 状态", 13, Ui.MUTED, false));

        TextView big = Ui.label(this, "", 26, Ui.TEXT, true);
        big.setPadding(0, (int) Ui.dp(this, 8), 0, 0);
        card.addView(big);

        TextView detail = Ui.label(this, "", 13, Ui.MUTED, false);
        detail.setPadding(0, (int) Ui.dp(this, 6), 0, 0);
        card.addView(detail);

        LinearLayout row = Ui.row(this, 0);
        TextView refresh = Ui.button(this, "刷新状态", true);
        refresh.setOnClickListener(v -> {
            refreshStatus(true);
            Toast.makeText(this, "正在拉取状态", Toast.LENGTH_SHORT).show();
        });
        row.addView(refresh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView cfg = Ui.button(this, "数据源设置", false);
        cfg.setOnClickListener(v -> showStatusUrlDialog());
        LinearLayout.LayoutParams cfgLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cfgLp.leftMargin = (int) Ui.dp(this, 10);
        row.addView(cfg, cfgLp);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = (int) Ui.dp(this, 12);
        card.addView(row, rowLp);

        statusBigView = big;
        statusDetailView = detail;
        bindStatusViews();
        return card;
    }

    private TextView statusBigView;
    private TextView statusDetailView;

    private View buildSettingsPanel() {
        LinearLayout card = card();
        card.addView(Ui.label(this, "点阵规格", 13, Ui.MUTED, false));
        LinearLayout sizeRow = Ui.row(this, 0);
        for (int i = 0; i < SIZE_OPTIONS.length; i++) {
            final int s = SIZE_OPTIONS[i];
            TextView b = Ui.button(this, s + "×" + s, store.cols() == s);
            b.setOnClickListener(v -> {
                store.setSize(s, s);
                pixelMatrix = pixelMatrix.resized(s, s);
                store.setPixel(encodePixel(pixelMatrix));
                sizeChip.setText(sizeText());
                applyMode(4);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = (int) Ui.dp(this, 8);
            sizeRow.addView(b, lp);
        }
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srLp.topMargin = (int) Ui.dp(this, 8);
        card.addView(sizeRow, srLp);

        TextView urlLabel = Ui.label(this, "Codex 状态数据源（返回 JSON）", 13, Ui.MUTED, false);
        urlLabel.setPadding(0, (int) Ui.dp(this, 16), 0, 0);
        card.addView(urlLabel);

        EditText url = new EditText(this);
        url.setText(store.statusUrl());
        url.setHint("https://example.com/codex-status.json");
        url.setTextColor(Ui.TEXT);
        url.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        url.setSingleLine(true);
        url.setBackground(Ui.round(this, 12, Ui.CARD_2));
        url.setPadding((int) Ui.dp(this, 12), (int) Ui.dp(this, 10),
                (int) Ui.dp(this, 12), (int) Ui.dp(this, 10));
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        urlLp.topMargin = (int) Ui.dp(this, 8);
        card.addView(url, urlLp);

        TextView saveUrl = Ui.button(this, "保存数据源", true);
        saveUrl.setOnClickListener(v -> {
            store.setStatusUrl(url.getText().toString().trim());
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            refreshStatus(true);
        });
        LinearLayout.LayoutParams suLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        suLp.topMargin = (int) Ui.dp(this, 10);
        card.addView(saveUrl, suLp);

        // 图片转点阵阈值
        TextView thLabel = Ui.label(this, "图片转点阵阈值 " + store.threshold(), 13, Ui.MUTED, false);
        thLabel.setPadding(0, (int) Ui.dp(this, 16), 0, 0);
        card.addView(thLabel);
        SeekBar sb = new SeekBar(this);
        sb.setMax(255);
        sb.setProgress(store.threshold());
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                store.setThreshold(progress);
                thLabel.setText("图片转点阵阈值 " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        card.addView(sb);

        LinearLayout ditherRow = Ui.row(this, 0);
        ditherRow.setPadding(0, (int) Ui.dp(this, 8), 0, 0);
        ditherRow.addView(Ui.label(this, "抖动保留灰阶", 14, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch dither = new Switch(this);
        dither.setChecked(store.dither());
        dither.setOnCheckedChangeListener((b, checked) -> store.setDither(checked));
        ditherRow.addView(dither);
        card.addView(ditherRow);

        TextView note = Ui.label(this,
                "背屏写入说明：Step AOS 目前只对系统服务与 Step Studio 运行时开放背屏接口，"
                        + "第三方应用还没有公开写入通道。保存的点阵图会进相册 Pictures/BackScreen，"
                        + "可直接在系统背屏自定义或 Studio 作品里使用。",
                12, Ui.MUTED, false);
        note.setLineSpacing(0, 1.25f);
        note.setPadding(0, (int) Ui.dp(this, 18), 0, 0);
        card.addView(note);

        LinearLayout wrap = Ui.column(this);
        wrap.addView(card);
        LinearLayout.LayoutParams probeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        probeLp.topMargin = (int) Ui.dp(this, 12);
        wrap.addView(buildProbeSection(), probeLp);
        return wrap;
    }

    // ------------------------------------------------------------- 接口探测

    private java.util.List<BackScreenProbe.Entry> probeEntries = new java.util.ArrayList<>();
    private TextView probeResultView;

    private View buildProbeSection() {
        LinearLayout card = card();
        card.addView(Ui.label(this, "背屏接口探测", 13, Ui.MUTED, false));

        TextView desc = Ui.label(this,
                "枚举设备上所有 ContentProvider / Service / Receiver，筛出与背屏、点阵相关的组件。"
                        + "命中即说明系统已把接口开放给应用；没有命中说明这一版仍未开放。",
                12, Ui.MUTED, false);
        desc.setLineSpacing(0, 1.25f);
        desc.setPadding(0, (int) Ui.dp(this, 8), 0, 0);
        card.addView(desc);

        LinearLayout row1 = Ui.row(this, 0);
        TextView scan = Ui.button(this, "扫描接口", true);
        scan.setOnClickListener(v -> runProbe());
        row1.addView(scan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView copy = Ui.button(this, "复制报告", false);
        copy.setOnClickListener(v -> {
            String report = BackScreenProbe.report(this, probeEntries);
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("背屏接口探测", report));
            Toast.makeText(this, "报告已复制", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = (int) Ui.dp(this, 10);
        row1.addView(copy, copyLp);
        LinearLayout.LayoutParams row1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row1Lp.topMargin = (int) Ui.dp(this, 12);
        card.addView(row1, row1Lp);

        LinearLayout row2 = Ui.row(this, 0);
        TextView push = Ui.button(this, "尝试推送", false);
        push.setOnClickListener(v -> {
            if (probeEntries.isEmpty()) {
                Toast.makeText(this, "请先扫描接口", Toast.LENGTH_SHORT).show();
                return;
            }
            String msg = BackScreenSink.tryPush(this, probeEntries, buildCurrentMatrix());
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
        row2.addView(push, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView notify = Ui.button(this, "预览通知", false);
        notify.setOnClickListener(v -> sendPreviewNotification());
        LinearLayout.LayoutParams notifyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        notifyLp.leftMargin = (int) Ui.dp(this, 10);
        row2.addView(notify, notifyLp);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = (int) Ui.dp(this, 10);
        card.addView(row2, row2Lp);

        probeResultView = Ui.label(this, "尚未扫描", 12, Ui.MUTED, false);
        probeResultView.setPadding((int) Ui.dp(this, 12), (int) Ui.dp(this, 10),
                (int) Ui.dp(this, 12), (int) Ui.dp(this, 10));
        probeResultView.setBackground(Ui.round(this, 12, Ui.CARD_2));
        probeResultView.setTypeface(android.graphics.Typeface.MONOSPACE);
        probeResultView.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pvLp.topMargin = (int) Ui.dp(this, 10);
        card.addView(probeResultView, pvLp);
        return card;
    }

    private void runProbe() {
        if (probeResultView == null) return;
        probeResultView.setText("扫描中…");
        new Thread(() -> {
            final java.util.List<BackScreenProbe.Entry> found = BackScreenProbe.scan(this);
            runOnUiThread(() -> {
                probeEntries = found;
                StringBuilder sb = new StringBuilder();
                if (found.isEmpty()) {
                    sb.append("未发现背屏相关组件\n这一版系统还没有对第三方应用开放点阵屏接口。");
                } else {
                    sb.append("命中 ").append(found.size()).append(" 个组件：\n");
                    for (BackScreenProbe.Entry e : found) {
                        sb.append("· ").append(e.toString()).append("\n");
                    }
                }
                String text = sb.toString().trim();
                probeResultView.setText(text);
                writeProbeReport(BackScreenProbe.report(this, found));
            });
        }, "probe").start();
    }

    /** 把探测报告写进 Download，便于外部读取核对。 */
    private void writeProbeReport(String text) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentResolver cr = getContentResolver();
                android.net.Uri collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                try (android.database.Cursor c = cr.query(collection, new String[]{
                        android.provider.MediaStore.Downloads._ID},
                        android.provider.MediaStore.Downloads.DISPLAY_NAME + "=?",
                        new String[]{"BackScreenProbe.txt"}, null)) {
                    while (c != null && c.moveToNext()) {
                        cr.delete(android.net.Uri.withAppendedPath(collection,
                                String.valueOf(c.getLong(0))), null, null);
                    }
                }
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "BackScreenProbe.txt");
                v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain");
                v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download");
                android.net.Uri uri = cr.insert(collection, v);
                if (uri != null) {
                    java.io.OutputStream os = cr.openOutputStream(uri);
                    if (os != null) {
                        os.write(text.getBytes("UTF-8"));
                        os.close();
                    }
                }
            } else {
                java.io.File f = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), "BackScreenProbe.txt");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                fos.write(text.getBytes("UTF-8"));
                fos.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void sendPreviewNotification() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2001);
            return;
        }
        BackScreenSink.Result r = BackScreenSink.notifyPreview(this, buildCurrentMatrix(), "背屏预览");
        Toast.makeText(this, r.message, Toast.LENGTH_SHORT).show();
    }

    private void showStatusUrlDialog() {
        EditText et = new EditText(this);
        et.setText(store.statusUrl());
        et.setHint("https://.../status.json");
        et.setTextColor(Ui.TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Codex 状态数据源")
                .setMessage("返回 {active, waiting, status, detail, updated} 的 JSON 地址")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    store.setStatusUrl(et.getText().toString().trim());
                    refreshStatus(true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ------------------------------------------------------------- 预览

    private void refreshPreview() {
        int cols = store.cols();
        int rows = store.rows();
        Matrix m;
        if (store.followStatus() || store.mode() == 3) {
            m = statusMatrix(snapshot);
            hintView.setText("显示源：Codex 状态 · " + snapshot.stateLabel()
                    + (snapshot.ok() && !snapshot.updated.isEmpty() ? " · " + snapshot.updated : ""));
        } else {
            switch (store.mode()) {
                case 1:
                    m = QrUtil.encode(store.qrContent(), cols, rows).matrix;
                    hintView.setText("显示源：二维码 · " + store.qrContent());
                    break;
                case 2:
                    m = pixelMatrix.resized(cols, rows);
                    hintView.setText("显示源：像素图");
                    break;
                default:
                    m = Matrix.fromText(this, store.text(), cols, rows, store.bold());
                    hintView.setText("显示源：自定义文字");
                    break;
            }
        }
        preview.setMatrix(m);
    }

    private Matrix statusMatrix(StatusClient.Snapshot s) {
        int cols = store.cols();
        int rows = store.rows();
        Matrix m = new Matrix(cols, rows);
        if (cols < 20) {
            m.stampStateGlyph(s.glyph(), Math.max(0, (cols - 11) / 2), Math.max(0, (rows - 11) / 2));
            return m;
        }
        int glyphTop = 1;
        m.stampStateGlyph(s.glyph(), (cols - 11) / 2, glyphTop);

        int numHeight = Math.max(6, rows / 4);
        Matrix num = Matrix.fromText(this, String.valueOf(s.active), cols, numHeight, true);
        m.blit(num, 0, glyphTop + 12);

        if (rows >= 28) {
            int labelHeight = Math.max(5, rows / 4);
            Matrix label = Matrix.fromText(this, s.stateLabel(), cols, labelHeight, false);
            m.blit(label, 0, rows - labelHeight - 1);
        }
        return m;
    }

    // ------------------------------------------------------------- 状态同步

    private void restoreCachedStatus() {
        String cached = store.cachedStatus();
        if (!cached.isEmpty()) {
            snapshot = StatusClient.Snapshot.fromJson(cached);
            bindStatusViews();
        }
    }

    private void bindStatusViews() {
        if (statusBigView != null) {
            statusBigView.setText(snapshot.stateLabel());
        }
        if (statusDetailView != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("进行中会话 ").append(snapshot.active).append(" 个");
            if (snapshot.waiting > 0) sb.append(" · 待回答 ").append(snapshot.waiting);
            if (!snapshot.updated.isEmpty()) sb.append("\n更新于 ").append(snapshot.updated);
            if (!snapshot.ok()) sb.append("\n").append(snapshot.error);
            else if (!snapshot.detail.isEmpty()) sb.append("\n").append(snapshot.detail);
            statusDetailView.setText(sb.toString());
        }
    }

    private void refreshStatusIfNeeded() {
        if (store.statusUrl().isEmpty()) return;
        refreshStatus(false);
    }

    private void refreshStatus(boolean manual) {
        final String url = store.statusUrl();
        if (url.isEmpty()) {
            if (manual) Toast.makeText(this, "请先在设置里填入数据源地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (statusRefreshRunning) return;
        statusRefreshRunning = true;
        new Thread(() -> {
            final StatusClient.Snapshot s = StatusClient.fetch(url, 8000);
            runOnUiThread(() -> {
                statusRefreshRunning = false;
                if (s.ok()) {
                    snapshot = s;
                    store.setCachedStatus(s.toJson());
                } else if (!snapshot.ok()) {
                    snapshot = s;
                }
                bindStatusViews();
                refreshPreview();
                handler.removeCallbacks(statusLoop);
                handler.postDelayed(statusLoop, 60_000);
            });
        }, "codex-status").start();
    }

    // ------------------------------------------------------------- 图片与导出

    private void pickImage(int requestCode) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选择图片"), requestCode);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if (requestCode == REQ_QR_IMAGE) {
                Result r = QrUtil.decode(bmp);
                if (r == null) {
                    Toast.makeText(this, "没有识别到二维码", Toast.LENGTH_SHORT).show();
                } else {
                    store.setQrContent(r.getText());
                    applyMode(1);
                    Toast.makeText(this, "已识别并按 " + store.cols() + " 格重新生成", Toast.LENGTH_LONG).show();
                }
            } else if (requestCode == REQ_PIXEL_IMAGE) {
                pixelMatrix = Matrix.fromBitmap(bmp, store.cols(), store.rows(),
                        store.threshold(), store.dither());
                store.setPixel(encodePixel(pixelMatrix));
                applyMode(2);
            }
            bmp.recycle();
            refreshPreview();
        } catch (Exception e) {
            Toast.makeText(this, "读取图片失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportCurrent() {
        Matrix m = buildCurrentMatrix();
        BackScreenSink.Result r = BackScreenSink.exportPng(this, m, "backscreen");
        if (!r.ok) {
            Toast.makeText(this, r.message, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("已保存")
                .setMessage(r.message + "\n\n系统背屏接口尚未对第三方应用开放，"
                        + "可以先在系统背屏自定义或 Step Studio 作品里选用这张点阵图；"
                        + "也可以直接分享出去。")
                .setPositiveButton("分享", (d, w) -> BackScreenSink.share(this, r.uri))
                .setNegativeButton("知道了", null)
                .show();
    }

    private Matrix buildCurrentMatrix() {
        int cols = store.cols();
        int rows = store.rows();
        if (store.followStatus() || store.mode() == 3) return statusMatrix(snapshot);
        switch (store.mode()) {
            case 1:
                return QrUtil.encode(store.qrContent(), cols, rows).matrix;
            case 2:
                return pixelMatrix.resized(cols, rows);
            default:
                return Matrix.fromText(this, store.text(), cols, rows, store.bold());
        }
    }

    // ------------------------------------------------------------- 像素编码

    private static String encodePixel(Matrix m) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < m.rows; y++) {
            for (int x = 0; x < m.cols; x++) {
                sb.append(m.get(x, y) ? '1' : '0');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static Matrix decodePixel(String data, int cols, int rows) {
        Matrix m = new Matrix(cols, rows);
        if (data == null || data.isEmpty()) return m;
        String[] lines = data.split("\n");
        for (int y = 0; y < Math.min(rows, lines.length); y++) {
            String line = lines[y];
            for (int x = 0; x < Math.min(cols, line.length()); x++) {
                m.set(x, y, line.charAt(x) == '1');
            }
        }
        return m;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

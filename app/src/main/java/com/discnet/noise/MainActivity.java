package com.discnet.noise;

import android.app.Activity;
import android.os.*;
import android.content.Intent;
import android.net.Uri;
import android.graphics.*;
import android.media.ExifInterface;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final String[] NAMES = {"Saturação", "Vibração", "Exposição", "Contraste", "Highlights", "Branco", "Preto", "Ruído", "Matiz"};
    private static final int[][] GROUPS = {{0, 1, 8}, {2, 3, 4, 5, 6}, {7}};
    private final float[] values = new float[9];
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger loads = new AtomicInteger();
    private final SeekBar[] sliders = new SeekBar[9];
    private final TextView[] valueLabels = new TextView[9], tabs = new TextView[3];
    private final LinearLayout[] rows = new LinearLayout[9];
    private Bitmap original;
    private EditorSurface preview;
    private TextView status, save, open, compare, stageBadge;
    private LinearLayout empty, controls;
    private Switch invertSwitch;
    private ScrollView controlScroll;
    private Uri input;
    private boolean comparing, exporting, loading, destroyed, resetting, comparisonTouch;
    private int selectedGroup, maxTexture = 4096;
    private int dp(float n) { return Glass.dp(this, n); }
    private TextView text(String content, int size, int color) {
        TextView view = new TextView(this); view.setText(content); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern"); return view;
    }
    private TextView action(String content, boolean primary) {
        TextView view = text(content, 14, primary ? 0xff102119 : Glass.INK);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); view.setGravity(Gravity.CENTER);
        view.setClickable(true); view.setFocusable(true); Glass.button(view, primary); return view;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private void gap(LinearLayout l, int height) { l.addView(new View(this), lp(1, dp(height))); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(0xff080e1d);
        Glass.Backdrop background = new Glass.Backdrop(this); setContentView(background);
        LinearLayout root = vertical(); background.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setPadding(dp(18), dp(10), dp(18), dp(12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(dp(18) + insets.getSystemWindowInsetLeft(), dp(10) + insets.getSystemWindowInsetTop(),
                dp(18) + insets.getSystemWindowInsetRight(), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); root.addView(header, lp(-1, dp(60)));
        LinearLayout brand = vertical(); header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text("Noise!", 32, Glass.INK); title.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC)); brand.addView(title);
        TextView caption = text("PHOTO STUDIO", 9, Glass.CYAN); caption.setLetterSpacing(.3f); brand.addView(caption);
        open = action("＋", false); open.setTextSize(25); open.setContentDescription("Abrir imagem"); header.addView(open, lp(dp(48), dp(48)));
        save = action("Salvar ↗", true); LinearLayout.LayoutParams saveParams = lp(dp(100), dp(48)); saveParams.leftMargin = dp(9); header.addView(save, saveParams);
        open.setOnClickListener(v -> pickImage()); save.setOnClickListener(v -> chooseOutput());
        gap(root, 14);

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(Glass.panel(this, 0x57344967, 0x3021304f, 26, 0x57bed8ff)); stage.setPadding(dp(12), dp(8), dp(12), dp(10));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        preview = new EditorSurface(this, new EditorSurface.Listener() {
            public void ready(int maximum) { maxTexture = Math.min(4096, maximum); }
            public void failed(String message) { if (!destroyed) status.setText(message); }
        });
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(-1, -1); imageParams.topMargin = dp(22);
        stage.addView(preview, imageParams);
        // Overlay the empty state instead of removing the GL surface: context stays warm.
        empty = vertical(); empty.setGravity(Gravity.CENTER); empty.setBackgroundColor(0xff0a0d17);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(-1, -1); emptyParams.topMargin = dp(22);
        stage.addView(empty, emptyParams);
        TextView icon = text("＋", 42, Glass.CYAN); icon.setGravity(Gravity.CENTER);
        icon.setBackground(Glass.panel(this, 0x6538556e, 0x60312c65, 22, 0x887abedc)); empty.addView(icon, lp(dp(70), dp(70)));
        gap(empty, 14); TextView prompt = text("Dê um novo tom.", 22, Glass.INK); prompt.setTypeface(Typeface.DEFAULT_BOLD); empty.addView(prompt);
        gap(empty, 8); TextView hint = text("Abra uma foto para começar", 13, Glass.MUTED); empty.addView(hint);
        empty.setOnClickListener(v -> pickImage()); empty.setContentDescription("Abrir uma foto para começar");
        // A separate row above the stage avoids overlapping the SurfaceView.
        stageBadge = text("PRÉVIA  /  ORIGINAL", 10, Glass.CYAN); stageBadge.setLetterSpacing(.14f);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, dp(22)); badgeParams.leftMargin = dp(4); stage.addView(stageBadge, badgeParams);
        gap(root, 8);
        status = text("Seu próximo visual começa aqui.", 11, Glass.MUTED); status.setSingleLine(true); root.addView(status, lp(-1, dp(20)));

        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL); root.addView(tools, lp(-1, dp(54)));
        compare = action("◐  Original", false); tools.addView(compare, new LinearLayout.LayoutParams(0, dp(48), 1));
        compare.setContentDescription("Segure para comparar com a imagem original");
        compare.setOnTouchListener((v, event) -> {
            if (original == null) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) { comparing = true; publish(); }
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                comparing = false; publish(); if (event.getAction() == MotionEvent.ACTION_UP) { comparisonTouch = true; v.performClick(); comparisonTouch = false; }
            }
            return true;
        });
        // Accessibility/keyboard click provides a toggle; touch remains press-and-hold.
        compare.setOnClickListener(v -> { if (!comparisonTouch && original != null) { comparing = !comparing; publish(); } });
        invertSwitch = new Switch(this); invertSwitch.setText("Inverter  "); invertSwitch.setTextSize(14); invertSwitch.setTextColor(Glass.INK);
        invertSwitch.setShowText(false); invertSwitch.setSwitchMinWidth(dp(42)); invertSwitch.setContentDescription("Inverter cores");
        Glass.button(invertSwitch, false);
        invertSwitch.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{Glass.LIME, 0xffadb9d0}));
        invertSwitch.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{0xff547646, 0xff38425c}));
        LinearLayout.LayoutParams invertParams = new LinearLayout.LayoutParams(0, dp(48), 1); invertParams.leftMargin = dp(10); tools.addView(invertSwitch, invertParams);
        invertSwitch.setOnCheckedChangeListener((v, checked) -> publish());
        gap(root, 12);

        LinearLayout panel = vertical(); panel.setPadding(dp(16), dp(8), dp(16), dp(8));
        panel.setBackground(Glass.panel(this, 0x68485372, 0x3823314c, 26, 0x55c6d8ff)); root.addView(panel, lp(-1, dp(250)));
        LinearLayout section = new LinearLayout(this); section.setGravity(Gravity.CENTER_VERTICAL); panel.addView(section, lp(-1, dp(32)));
        TextView heading = text("Ajustes", 18, Glass.INK); heading.setTypeface(Typeface.DEFAULT_BOLD); section.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView reset = text("↺  Redefinir", 12, Glass.LIME); reset.setGravity(Gravity.CENTER); reset.setPadding(dp(6), 0, dp(6), 0); section.addView(reset, lp(-2, dp(32)));
        reset.setContentDescription("Zerar todos os ajustes e desligar a inversão"); reset.setOnClickListener(v -> reset());
        LinearLayout categories = new LinearLayout(this); panel.addView(categories, lp(-1, dp(39)));
        String[] labels = {"◉  Cor", "☼  Luz", "⁙  Textura"};
        for (int i = 0; i < 3; i++) {
            final int group = i; tabs[i] = text(labels[i], 13, Glass.MUTED); tabs[i].setGravity(Gravity.CENTER); tabs[i].setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1); if (i != 0) params.leftMargin = dp(7); categories.addView(tabs[i], params);
            tabs[i].setOnClickListener(v -> showGroup(group)); tabs[i].setFocusable(true);
        }
        View rule = new View(this); rule.setBackgroundColor(0x24c9dcff); panel.addView(rule, lp(-1, dp(1))); gap(panel, 4);
        controlScroll = new ScrollView(this); controlScroll.setFillViewport(false); controlScroll.setVerticalScrollBarEnabled(false);
        panel.addView(controlScroll, new LinearLayout.LayoutParams(-1, 0, 1)); controls = vertical(); controlScroll.addView(controls);
        for (int i = 0; i < 9; i++) buildAdjustment(i);
        showGroup(0); updateActions();
        if (state != null) {
            float[] saved = state.getFloatArray("values"); resetting = true;
            if (saved != null && saved.length == 9) for (int i = 0; i < 9; i++) sliders[i].setProgress(Math.round(saved[i] * 100) + 100);
            invertSwitch.setChecked(state.getBoolean("invert")); resetting = false; showGroup(state.getInt("group", 0)); publish();
            String uri = state.getString("input"); if (uri != null) load(Uri.parse(uri));
        }
    }
    private void buildAdjustment(int index) {
        LinearLayout row = vertical(); rows[index] = row;
        LinearLayout labelRow = new LinearLayout(this); labelRow.setGravity(Gravity.CENTER_VERTICAL); row.addView(labelRow, lp(-1, dp(22)));
        TextView label = text(NAMES[index], 13, Glass.INK); labelRow.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("0", 12, Glass.CYAN); value.setGravity(Gravity.CENTER); value.setTypeface(Typeface.MONOSPACE);
        value.setBackground(Glass.panel(this, 0x422c4059, 0x422c3059, 8, 0x327aa4bf)); valueLabels[index] = value; labelRow.addView(value, lp(dp(61), dp(24)));
        value.setContentDescription("Zerar " + NAMES[index]); value.setOnClickListener(v -> sliders[index].setProgress(100));
        Glass.Slider slider = new Glass.Slider(this, index == 7 ? Glass.LIME : index == 8 ? Glass.VIOLET : Glass.CYAN); sliders[index] = slider;
        slider.setContentDescription(NAMES[index]); row.addView(slider, lp(-1, dp(30)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int progress, boolean user) {
                values[index] = (progress - 100) / 100f; valueLabels[index].setText(formatValue(index)); publish();
            }
            public void onStartTrackingTouch(SeekBar seek) {}
            public void onStopTrackingTouch(SeekBar seek) {}
        });
    }
    private String formatValue(int index) {
        if (index == 2) return String.format(Locale.US, "%+.1f EV", values[index] * 3);
        if (index == 8) return String.format(Locale.US, "%+d°", Math.round(values[index] * 180));
        int n = Math.round(values[index] * 100); return n == 0 ? "0" : String.format(Locale.US, "%+d", n);
    }
    private void showGroup(int group) {
        selectedGroup = Math.max(0, Math.min(2, group)); controls.removeAllViews();
        for (int index : GROUPS[selectedGroup]) controls.addView(rows[index]);
        if (selectedGroup == 2) {
            gap(controls, 12); TextView note = text("−  Suavizar                         Granular  +", 12, Glass.LIME); controls.addView(note);
            gap(controls, 8); TextView explanation = text("Deslize para a esquerda para reduzir o ruído. Para a direita, adicione textura à foto.", 12, Glass.MUTED); explanation.setLineSpacing(dp(3), 1); controls.addView(explanation);
        }
        for (int i = 0; i < 3; i++) {
            boolean active = i == selectedGroup; tabs[i].setSelected(active); tabs[i].setTextColor(active ? Glass.LIME : Glass.MUTED);
            tabs[i].setBackground(Glass.panel(this, active ? 0x543c5c3b : 0x17222c45, active ? 0x30283e32 : 0x12222c45, 12, active ? 0x99caff70 : 0x25c6d8ff));
        }
        controlScroll.scrollTo(0, 0);
    }
    private void publish() {
        if (preview == null || resetting) return;
        preview.setEditState(new EditState(values, invertSwitch != null && invertSwitch.isChecked(), comparing));
        if (stageBadge != null) stageBadge.setText(comparing ? "PRÉVIA  /  ORIGINAL" : "PRÉVIA  /  EDITADA");
        if (compare != null) compare.setText(comparing ? "◑  Original" : "◐  Comparar");
    }
    private void reset() {
        resetting = true; for (SeekBar slider : sliders) slider.setProgress(100); invertSwitch.setChecked(false); comparing = false; resetting = false; publish();
    }
    private void updateActions() {
        boolean available = original != null && !loading;
        save.setEnabled(available && !exporting); save.setAlpha(save.isEnabled() ? 1 : .4f);
        open.setEnabled(!loading); open.setAlpha(loading ? .4f : 1);
        compare.setEnabled(available); compare.setAlpha(available ? 1 : .45f); invertSwitch.setEnabled(available);
        for (SeekBar slider : sliders) slider.setEnabled(available);
    }
    private void pickImage() {
        if (loading) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, 1);
    }
    private void chooseOutput() {
        if (original == null || exporting || loading) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.setType("image/png"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "Noise-" + System.currentTimeMillis() + ".png"); startActivityForResult(intent, 2);
    }
    private void load(Uri uri) {
        int token = loads.incrementAndGet(), limit = maxTexture; loading = true; status.setText("Abrindo imagem…"); updateActions();
        worker.execute(() -> {
            try {
                Bitmap bitmap = Build.VERSION.SDK_INT >= 28 ? decodeModern(uri, limit) : decodeLegacy(uri, limit);
                if (bitmap == null) throw new IOException("Imagem inválida");
                runOnUiThread(() -> {
                    if (destroyed || token != loads.get()) { bitmap.recycle(); return; }
                    input = uri; setImage(bitmap); loading = false; updateActions();
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> { if (!destroyed && token == loads.get()) { loading = false; status.setText("Não foi possível abrir. Tente uma imagem menor."); updateActions(); } });
            }
        });
    }
    // Also used by the instrumented test to feed a deterministic image through the real renderer.
    void setImage(Bitmap bitmap) {
        original = bitmap; preview.setImage(bitmap); empty.setVisibility(View.GONE);
        status.setText(bitmap.getWidth() + " × " + bitmap.getHeight() + "  ·  Ajuste enquanto arrasta"); publish(); updateActions();
    }
    @android.annotation.TargetApi(28) private Bitmap decodeModern(Uri uri, int limit) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (decoder, info, source) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE); decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
            if (max > limit) { float ratio = (float)limit / max; decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * ratio)), Math.max(1, Math.round(info.getSize().getHeight() * ratio))); }
        });
    }
    private Bitmap decodeLegacy(Uri uri, int limit) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(stream, null, options); }
        options.inJustDecodeBounds = false; options.inSampleSize = 1; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > limit) options.inSampleSize *= 2;
        Bitmap bitmap; try (InputStream stream = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(stream, null, options); }
        if (bitmap == null) throw new IOException("Imagem inválida");
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(stream); Matrix matrix = new Matrix();
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case 2: matrix.setScale(-1, 1); break;
                case 3: matrix.setRotate(180); break;
                case 4: matrix.setScale(1, -1); break;
                case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case 6: matrix.setRotate(90); break;
                case 7: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                case 8: matrix.setRotate(-90); break;
            }
            if (!matrix.isIdentity()) { Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true); if (rotated != bitmap) bitmap.recycle(); bitmap = rotated; }
        } catch (IOException ignored) { /* Non-EXIF formats are valid images too. */ }
        return bitmap;
    }
    private void export(Uri uri) {
        Bitmap bitmap = original; EditState state = new EditState(values, invertSwitch.isChecked(), false);
        exporting = true; updateActions(); status.setText("Salvando sua imagem…");
        worker.execute(() -> {
            Bitmap output = null;
            try {
                output = GpuExporter.render(this, bitmap, state);
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null || !output.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("PNG");
                }
                runOnUiThread(() -> { if (!destroyed) { exporting = false; updateActions(); status.setText("PNG salvo. Seu novo visual está pronto."); Toast.makeText(this, "Imagem salva!", Toast.LENGTH_SHORT).show(); } });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> { if (!destroyed) { exporting = false; updateActions(); status.setText("Não foi possível salvar. Tente outra pasta ou imagem menor."); } });
            } finally { if (output != null) output.recycle(); }
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == 1) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
            load(uri);
        } else if (request == 2 && original != null && !exporting) export(uri);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putFloatArray("values", values.clone()); state.putBoolean("invert", invertSwitch.isChecked());
        state.putInt("group", selectedGroup); if (input != null) state.putString("input", input.toString());
    }
    @Override protected void onResume() { super.onResume(); if (preview != null) preview.onResume(); }
    @Override protected void onPause() { comparing = false; publish(); if (preview != null) preview.onPause(); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; loads.incrementAndGet(); worker.shutdown(); super.onDestroy(); }
}

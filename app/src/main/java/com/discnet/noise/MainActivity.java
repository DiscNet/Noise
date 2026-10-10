package com.discnet.noise;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.text.*;
import android.text.InputType;
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
    private static final String[] NAMES = {"Saturação", "Vibração", "Exposição", "Contraste", "Highlights", "Branco", "Preto", "Ruído", "Matiz", "Dither", "Brilho difuso", "Desvio RGB", "Fade", "Tom de pele", "Poeira", "Vinheta", "Aberrações", "Névoa", "Nitidez", "Desfoque rotativo", "Profundidade", "Posição X", "Posição Y", "Escala", "Densidade", "Ondulação"};
    private static final String[] ICONS = {"☼","◉","✧","◐","✦","◯","●","⁙","◌","≋","✧","◎",
        "◑","◕","⁙","◉","◎","≈","◇","⟳","▤","↔","↕","⌗","∴","〰"};
    private static final int[][] GROUPS = {{0, 3, 1, 2, 4, 5, 6, 8}, {7}, {9, 11, 20, 21, 22, 23, 24, 25}, {12, 13, 14, 15, 16, 17, 10, 18, 19}};
    private final float[] values = new float[9];
    private final float[] effects = new float[3];
    private final float[] filters = new float[8];
    private final float[] ditherControls = EditState.DITHER_DEFAULTS.clone();
    private int outputWidth, outputHeight, restoreWidth, restoreHeight;
    private ImageView resizeButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger loads = new AtomicInteger();
    private final SeekBar[] sliders = new SeekBar[26];
    private final TextView[] valueLabels = new TextView[26], tabs = new TextView[4];
    private final LinearLayout[] rows = new LinearLayout[26];
    private Bitmap original;
    private EditorSurface preview;
    private TextView status, compare, stageBadge;
    private ImageView save, open;
    private Glass.Backdrop appBackground;
    private GalleryScreen galleryScreen;
    private static final int GALLERY_PERMISSION=73;
    private static final int SAVE_PERMISSION=74;
    private CropEditor cropEditor;
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
        getWindow().setStatusBarColor(0xff09080f); getWindow().setNavigationBarColor(0xff09080f);
        Glass.Backdrop background = new Glass.Backdrop(this); appBackground=background; setContentView(background);
        LinearLayout root = vertical(); background.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setPadding(dp(10), dp(4), dp(10), dp(8));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(dp(10) + insets.getSystemWindowInsetLeft(), dp(4) + insets.getSystemWindowInsetTop(),
                dp(10) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, lp(-1, dp(54)));
        ImageView settings = IconArt.button(this,IconArt.SETTINGS,"Configurações");
        header.addView(settings, lp(dp(46), dp(46)));
        settings.setOnClickListener(v -> showOptions(settings));
        TextView title = text("Noise!", 30, Glass.INK);
        title.setGravity(Gravity.CENTER); title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        open=IconArt.button(this,IconArt.GALLERY,"Galeria de fotos");
        header.addView(open, lp(dp(46), dp(46)));
        resizeButton=IconArt.button(this,IconArt.RESIZE,"Recortar imagem pelos cantos");
        LinearLayout.LayoutParams resParams=lp(dp(44),dp(44));resParams.leftMargin=dp(3);
        header.addView(resizeButton,resParams);
        save=IconArt.button(this,IconArt.SAVE,"Salvar PNG");
        LinearLayout.LayoutParams saveParams = lp(dp(44), dp(44)); saveParams.leftMargin = dp(3);
        header.addView(save, saveParams);
        open.setOnClickListener(v -> pickImage());
        resizeButton.setOnClickListener(v -> showResizeDialog());
        save.setOnClickListener(v -> chooseOutput());
        LinearLayout categories = new LinearLayout(this);
        categories.setPadding(dp(3), dp(3), dp(3), dp(3));
        categories.setBackground(Glass.panel(this, 0xc12a2536, 0xc3211d2c, 28, 0x56bba5d9));
        LinearLayout.LayoutParams catParams = lp(-1, dp(46));
        catParams.leftMargin = dp(10); catParams.rightMargin = dp(10);
        root.addView(categories, catParams);
        String[] labels = {"Básico", "Ruído", "Dither", "Efeitos"};
        for (int i=0;i<4;i++) {
            final int group=i;
            tabs[i]=text(labels[i],14,Glass.MUTED); tabs[i].setGravity(Gravity.CENTER);
            categories.addView(tabs[i], new LinearLayout.LayoutParams(0,-1,1));
            tabs[i].setOnClickListener(v -> showGroup(group));
            tabs[i].setFocusable(true);
        }
        gap(root, 8);

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(Glass.panel(this, 0xff10101a, 0xff18121e, 22, 0x36c4a4ec));
        root.addView(stage, new LinearLayout.LayoutParams(-1,0,1));
        preview = new EditorSurface(this, new EditorSurface.Listener() {
            public void ready(int maximum) { maxTexture = Math.min(4096, maximum); }
            public void failed(String message) { if (!destroyed) status.setText(message); }
        });
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(-1, -1); imageParams.topMargin = 0;
        stage.addView(preview, imageParams);
        // Overlay the empty state instead of removing the GL surface: context stays warm.
        empty = vertical(); empty.setGravity(Gravity.CENTER); empty.setBackgroundColor(0xff0a0d17);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(-1, -1); emptyParams.topMargin = 0;
        stage.addView(empty, emptyParams);
        TextView icon = text("＋", 42, Glass.INK); icon.setGravity(Gravity.CENTER);
        icon.setBackground(Glass.panel(this, 0x6538556e, 0x60312c65, 22, 0x887abedc)); empty.addView(icon, lp(dp(70), dp(70)));
        gap(empty, 14); TextView prompt = text("Dê um novo tom.", 22, Glass.INK); prompt.setTypeface(Typeface.DEFAULT_BOLD); empty.addView(prompt);
        gap(empty, 8); TextView hint = text("Abra uma foto para começar", 13, Glass.MUTED); empty.addView(hint);
        empty.setOnClickListener(v -> pickImage()); empty.setContentDescription("Abrir uma foto para começar");
        stageBadge = text("", 10, Glass.MUTED);
        status = text("Abra uma imagem para começar", 11, Glass.MUTED);
        status.setGravity(Gravity.CENTER);
        root.addView(status, lp(-1, dp(20)));
        // Compact floating graphite glass panel, controls grouped by the tabs above.
        LinearLayout panel = vertical();
        panel.setPadding(dp(15), dp(10), dp(15), dp(10));
        panel.setBackground(Glass.frosted(this, 28));
        panel.setElevation(dp(12)); panel.setClipToOutline(true);
        LinearLayout.LayoutParams panelParams = lp(-1, dp(300));
        root.addView(panel, panelParams);
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL); panel.addView(tools, lp(-1,dp(38)));
        compare=action("Original",false);
        decorateAction(compare,IconArt.ORIGINAL);
        tools.addView(compare,new LinearLayout.LayoutParams(0,dp(34),1));
        compare.setContentDescription("Segure para comparar com a imagem original");
        compare.setOnTouchListener((v,event) -> {
            if(original==null)return false;
            if(event.getAction()==MotionEvent.ACTION_DOWN){comparing=true;publish();}
            else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){
                comparing=false;publish();
                if(event.getAction()==MotionEvent.ACTION_UP){comparisonTouch=true;v.performClick();comparisonTouch=false;}
            }
            return true;
        });
        compare.setOnClickListener(v->{if(!comparisonTouch&&original!=null){comparing=!comparing;publish();}});
        TextView reset = action("Reset",false); decorateAction(reset,IconArt.RESET); tools.addView(reset,lp(dp(90),dp(34)));
        reset.setOnClickListener(v->reset());
        View rule = new View(this); rule.setBackgroundColor(0x2cffffff); panel.addView(rule,lp(-1,dp(1)));
        controlScroll = new ScrollView(this); controlScroll.setFillViewport(false); controlScroll.setVerticalScrollBarEnabled(false);
        panel.addView(controlScroll, new LinearLayout.LayoutParams(-1, 0, 1)); controls = vertical(); controlScroll.addView(controls);
        for (int i = 0; i < 26; i++) buildAdjustment(i);
        invertSwitch = new Switch(this);
        invertSwitch.setText("  Inverter cores");
        android.graphics.drawable.Drawable toggleIcon=new IconArt(IconArt.ORIGINAL,Glass.INK);
        toggleIcon.setBounds(0,0,dp(23),dp(23));
        invertSwitch.setCompoundDrawables(toggleIcon,null,null,null); invertSwitch.setTextSize(14); invertSwitch.setTextColor(Glass.INK);
        invertSwitch.setSwitchMinWidth(dp(45));
        invertSwitch.setContentDescription("Inverter cores");
        invertSwitch.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xffd3c0ff,0xffd3d0dc}));
        invertSwitch.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff7a628f,0xff43404e}));
        panel.addView(invertSwitch,lp(-1,dp(44)));
        invertSwitch.setOnCheckedChangeListener((v, checked)->publish());
        showGroup(0); updateActions();
        if (state != null) {
            float[] saved = state.getFloatArray("values"); resetting = true;
            if (saved != null && saved.length == 9) for (int i = 0; i < 9; i++) sliders[i].setProgress(Math.round(saved[i] * 100) + 100);
            invertSwitch.setChecked(state.getBoolean("invert"));
            float[] savedEffects=state.getFloatArray("effects");
            if(savedEffects!=null&&savedEffects.length==3)
                for(int i=0;i<3;i++)sliders[9+i].setProgress(Math.round(savedEffects[i]*200));
            float[] savedFilters=state.getFloatArray("filters");
            if(savedFilters!=null&&(savedFilters.length==7||savedFilters.length==8))
                for(int i=0;i<savedFilters.length;i++)sliders[12+i].setProgress(Math.round(savedFilters[i]*200));
            float[] savedDither=state.getFloatArray("ditherControls");
            if(savedDither!=null&&savedDither.length==6)
                for(int i=0;i<6;i++)sliders[20+i].setProgress(Math.round(savedDither[i]*200));
            restoreWidth=state.getInt("outputWidth",0);
            restoreHeight=state.getInt("outputHeight",0);
            resetting = false; showGroup(state.getInt("group", 0)); publish();
            String uri = state.getString("input"); if (uri != null) load(Uri.parse(uri));
        }
        if(state==null||state.getString("input")==null)showGallery();
    }
    private void buildAdjustment(int index) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL); rows[index]=row;
        View glyph=IconArt.inline(this,index);
        row.addView(glyph,lp(dp(27),dp(45)));
        TextView label = text(NAMES[index],13,Glass.INK);
        label.setSingleLine(true); label.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label,lp(dp(94),dp(45)));
        Glass.Slider slider=new Glass.Slider(this,
            index==9?0xffff64ca:index==10?0xffffb76c:index==11?0xff89aaff:index>=20?0xfffc89f7:index==19?0xffffae88:0xffd5baff);
        sliders[index]=slider; slider.setContentDescription(NAMES[index]);
        slider.setMax(200); slider.setProgress((index<9||index>=20)?100:0);
        row.addView(slider,new LinearLayout.LayoutParams(0,dp(43),1));
        TextView value=text("0",12,Glass.INK); value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        valueLabels[index]=value; row.addView(value,lp(dp(49),dp(43)));
        value.setOnClickListener(v->sliders[index].setProgress((index<9||index>=20)?100:0));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seek,int progress,boolean user){
                if(index<9)values[index]=(progress-100)/100f;
                else if(index<12)effects[index-9]=progress/200f;
                else if(index<20)filters[index-12]=progress/200f;
                else ditherControls[index-20]=progress/200f;
                valueLabels[index].setText(formatValue(index));publish();
            }
            public void onStartTrackingTouch(SeekBar seek){}
            public void onStopTrackingTouch(SeekBar seek){}
        });
    }
    private String formatValue(int index) {
        if(index>=20)return String.format(Locale.US,"%d",Math.round(ditherControls[index-20]*100));
        if(index>=12)return String.format(Locale.US,"%d",Math.round(filters[index-12]*100));
        if(index>=9)return String.format(Locale.US,"%d",Math.round(effects[index-9]*100));
        if(index==2)return String.format(Locale.US,"%+.1f EV",values[index]*3);
        if(index==8)return String.format(Locale.US,"%+d°",Math.round(values[index]*180));
        int n=Math.round(values[index]*100);
        return n==0?"0":String.format(Locale.US,"%+d",n);
    }
    private void showGroup(int group) {
        selectedGroup=Math.max(0,Math.min(3,group));
        controls.removeAllViews();
        for(int index:GROUPS[selectedGroup]){
            controls.addView(rows[index]);
            View separator=new View(this);separator.setBackgroundColor(0x20ffffff);
            controls.addView(separator,lp(-1,dp(1)));
        }
        if(selectedGroup==1){
            TextView hint=text("− Suavizar     /     + Granular",12,Glass.MUTED);
            controls.addView(hint,lp(-1,dp(32)));
        }
        if(selectedGroup==2){
            TextView preset=action("✦   Aplicar Neon da referência",false);
            preset.setContentDescription("Aplicar estilo neon ondulado com brilhos e pontos");
            LinearLayout.LayoutParams presetParams=lp(-1,dp(42));
            controls.addView(preset,presetParams);
            preset.setOnClickListener(v->{
                sliders[9].setProgress(188);  // 94% dither: connected wavy traces
                sliders[10].setProgress(132); // 66% glow: luminous halos
                sliders[11].setProgress(26);  // 13% RGB shift: subtle chromatic fringes
                for(int i=20;i<26;i++)sliders[i].setProgress(100);
                status.setText("Neon da referência aplicado · ajuste a intensidade");
            });
        }
        for(int i=0;i<4;i++){
            boolean active=i==selectedGroup;
            tabs[i].setSelected(active);tabs[i].setTextColor(active?Glass.INK:Glass.MUTED);
            tabs[i].setBackground(Glass.panel(this,active?0xdda44fbc:0x00303040,
                active?0xc33e344d:0x00222a40,23,active?0xaff7a3ec:0x18ffffff));
        }
        controlScroll.scrollTo(0,0);
    }
    private void publish() {
        if (preview == null || resetting) return;
        preview.setEditState(new EditState(values, effects, filters, ditherControls, invertSwitch != null && invertSwitch.isChecked(), comparing));
        if (stageBadge != null) stageBadge.setText(comparing ? "PRÉVIA  /  ORIGINAL" : "PRÉVIA  /  EDITADA");
        if (compare != null) compare.setText(comparing ? "Original" : "Comparar");
    }
    private void reset() {
        resetting = true; for (int i=0;i<sliders.length;i++)sliders[i].setProgress((i<9||i>=20)?100:0); invertSwitch.setChecked(false); comparing = false; resetting = false; publish();
    }
    private void updateActions() {
        boolean available = original != null && !loading;
        save.setEnabled(available && !exporting); save.setAlpha(save.isEnabled() ? 1 : .4f);
        open.setEnabled(!loading); open.setAlpha(loading ? .4f : 1);
        resizeButton.setEnabled(available && !exporting); resizeButton.setAlpha(resizeButton.isEnabled()?1:.4f);
        compare.setEnabled(available); compare.setAlpha(available ? 1 : .45f); invertSwitch.setEnabled(available);
        for (SeekBar slider : sliders) slider.setEnabled(available);
    }
    private void decorateAction(TextView view,int which){
        android.graphics.drawable.Drawable icon=new IconArt(which,Glass.INK);
        icon.setBounds(0,0,dp(19),dp(19));
        view.setCompoundDrawablePadding(dp(6));
        view.setCompoundDrawables(icon,null,null,null);
    }
    private void updateSizeLabel(){
        if(original==null)return;
        status.setText(String.format(Locale.US,"%d × %d px  ·  exportação %d × %d",
            original.getWidth(),original.getHeight(),outputWidth,outputHeight));
    }
    private void showOptions(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("Abrir foto"); menu.getMenu().add("Recortar");
        menu.getMenu().add("Salvar PNG"); menu.getMenu().add("Redefinir ajustes");
        menu.setOnMenuItemClickListener(item->{
            String title=item.getTitle().toString();
            if(title.equals("Abrir foto"))pickImage();
            else if(title.equals("Recortar"))showResizeDialog();
            else if(title.equals("Salvar PNG"))chooseOutput();
            else reset();
            return true;
        });
        menu.show();
    }
    /** Export-only dimensions: the source bitmap is never destructively changed. */
    /** A full-screen in-app crop canvas replaces numeric resizing and sliders.
     * Dragging a handle chooses exactly which pixels to keep.
     */
    private void showResizeDialog(){
        if(original==null||loading||exporting){
            Toast.makeText(this,"Abra uma foto primeiro",Toast.LENGTH_SHORT).show();return;
        }
        if(cropEditor!=null)return;
        Bitmap current=original;
        cropEditor=new CropEditor(this,current,new CropEditor.Listener(){
            public void cancel(){closeCropEditor();}
            public void apply(Rect area){
                if(area.width()<1||area.height()<1)return;
                if(area.left==0&&area.top==0&&
                   area.width()==current.getWidth()&&area.height()==current.getHeight()){
                    closeCropEditor();return;
                }
                Bitmap trimmed=Bitmap.createBitmap(current,area.left,area.top,
                    area.width(),area.height());
                // Cropping is actual bitmap geometry. Both the editor preview
                // and GPU export now see exactly the same selected pixels.
                closeCropEditor();
                if(trimmed!=current){
                    restoreWidth=restoreHeight=0;
                    setImage(trimmed);
                    status.setText("Recorte aplicado · "+
                         trimmed.getWidth()+" × "+trimmed.getHeight()+" px");
                }
            }
        });
        appBackground.addView(cropEditor,new FrameLayout.LayoutParams(-1,-1));
        cropEditor.bringToFront();
    }
    private void closeCropEditor(){
        if(cropEditor!=null){
            appBackground.removeView(cropEditor);
            cropEditor=null;
        }
    }
    private void pickImage(){if(!loading)showGallery();}
    /** Opens the Android system Photo Picker or the device's Gallery activity,
      * never ACTION_OPEN_DOCUMENT / the files UI. */
    private void pickFromSystemGallery() {
        try{
            Intent intent;
            if(Build.VERSION.SDK_INT>=33){
                intent=new Intent(MediaStore.ACTION_PICK_IMAGES);
                intent.setType("image/*");
            }else{
                intent=new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent,1);
        }catch(android.content.ActivityNotFoundException error){
            Toast.makeText(this,"Galeria padrão indisponível",Toast.LENGTH_SHORT).show();
        }
    }
    private boolean hasGalleryPermission(){
        if(Build.VERSION.SDK_INT>=34)
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED
                ||checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;
        if(Build.VERSION.SDK_INT>=33)
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    private void grantGalleryPermission(){
        if(hasGalleryPermission()){if(galleryScreen!=null)galleryScreen.refresh(true);return;}
        if(Build.VERSION.SDK_INT>=34)
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},GALLERY_PERMISSION);
        else if(Build.VERSION.SDK_INT>=33)
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},GALLERY_PERMISSION);
        else
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},GALLERY_PERMISSION);
    }
    private void showGallery(){
        if(galleryScreen==null){
            galleryScreen=new GalleryScreen(this,new GalleryScreen.Listener(){
                public void onPhoto(Uri uri){load(uri);}
                public void onSystemGallery(){pickFromSystemGallery();}
                public void onGrantPermission(){grantGalleryPermission();}
                public void onClose(){
                    if(original!=null)closeGallery();
                    else Toast.makeText(MainActivity.this,"Selecione uma foto para editar",Toast.LENGTH_SHORT).show();
                }
            });
            appBackground.addView(galleryScreen,new FrameLayout.LayoutParams(-1,-1));
        }
        galleryScreen.setVisibility(View.VISIBLE);
        galleryScreen.bringToFront();
        galleryScreen.refresh(hasGalleryPermission());
        if(!hasGalleryPermission())grantGalleryPermission();
    }
    private void closeGallery(){
        if(galleryScreen!=null)galleryScreen.setVisibility(View.GONE);
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){
        super.onRequestPermissionsResult(request,names,grants);
        if(request==GALLERY_PERMISSION && galleryScreen!=null)
            galleryScreen.refresh(hasGalleryPermission());
        if(request==SAVE_PERMISSION && grants.length>0 &&
            grants[0]==PackageManager.PERMISSION_GRANTED)chooseOutput();
    }
    @Override public void onBackPressed(){
        if(cropEditor!=null){closeCropEditor();return;}
        if(galleryScreen!=null&&galleryScreen.getVisibility()==View.VISIBLE && original!=null){
            closeGallery();return;
        }
        if(original!=null){showGallery();return;}
        super.onBackPressed();
    }
    private void chooseOutput() {
        if(original==null||exporting||loading)return;
        if(Build.VERSION.SDK_INT<29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                 !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},SAVE_PERMISSION);
            return;
        }
        exportTo(null,true);
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
        closeGallery();
        outputWidth=restoreWidth>0?restoreWidth:bitmap.getWidth();
        outputHeight=restoreHeight>0?restoreHeight:bitmap.getHeight();
        restoreWidth=restoreHeight=0;
        updateSizeLabel(); publish(); updateActions();
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
    /** Retained for instrumentation and for writing a caller-owned URI. */
    private void export(Uri uri){exportTo(uri,false);}

    /** Store directly in public Pictures/Noise! (NOT cache or Android/data).
     * MediaStore entries remain in the user's gallery after app uninstall.
     */
    private void exportTo(Uri suppliedUri,boolean publicGallery){
        if(original==null||loading||exporting)return;
        Bitmap bitmap=original;
        EditState state=new EditState(values,effects,filters,ditherControls,
            invertSwitch.isChecked(),false);
        final int width=outputWidth,height=outputHeight;
        exporting=true;updateActions();status.setText("Salvando em Imagens/Noise!…");
        worker.execute(()->{
            Bitmap output=null;
            Uri inserted=null,uri=suppliedUri;
            File legacyFile=null;
            boolean pending=false;
            try{
                output=GpuExporter.render(this,bitmap,state,width,height);
                String name="Noise-"+System.currentTimeMillis()+".png";
                if(publicGallery){
                    if(Build.VERSION.SDK_INT>=29){
                        ContentValues values=new ContentValues();
                        values.put(Images.Media.DISPLAY_NAME,name);
                        values.put(Images.Media.MIME_TYPE,"image/png");
                        values.put(Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES+"/Noise!");
                        values.put(Images.Media.IS_PENDING,1);
                        inserted=getContentResolver().insert(
                            Images.Media.EXTERNAL_CONTENT_URI,values);
                        if(inserted==null)throw new IOException("MediaStore indisponível");
                        uri=inserted; pending=true;
                    }else{
                        File pictures=Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES);
                        File folder=new File(pictures,"Noise!");
                        if(!folder.exists()&&!folder.mkdirs())
                            throw new IOException("Não foi possível criar a pasta Noise!");
                        legacyFile=new File(folder,name);
                    }
                }
                if(legacyFile!=null){
                    try(OutputStream stream=new FileOutputStream(legacyFile)){
                        if(!output.compress(Bitmap.CompressFormat.PNG,100,stream))
                            throw new IOException("Falha na codificação PNG");
                    }
                    MediaScannerConnection.scanFile(this,
                        new String[]{legacyFile.getAbsolutePath()},
                        new String[]{"image/png"},null);
                }else{
                    if(uri==null)throw new IOException("Destino não informado");
                    try(OutputStream stream=getContentResolver().openOutputStream(uri,"w")){
                        if(stream==null||!output.compress(Bitmap.CompressFormat.PNG,100,stream))
                            throw new IOException("Não foi possível salvar PNG");
                    }
                    if(pending){
                        ContentValues complete=new ContentValues();
                        complete.put(Images.Media.IS_PENDING,0);
                        getContentResolver().update(uri,complete,null,null);
                        pending=false;
                    }
                }
                runOnUiThread(()->{
                    if(!destroyed){
                        exporting=false;updateActions();
                        status.setText("Salvo em Imagens/Noise! · disponível na Galeria");
                        Toast.makeText(this,"Imagem salva em Imagens/Noise!",Toast.LENGTH_LONG).show();
                    }
                });
            }catch(Exception|OutOfMemoryError problem){
                if(inserted!=null){
                    try{getContentResolver().delete(inserted,null,null);}catch(Exception ignored){}
                }
                if(legacyFile!=null)legacyFile.delete();
                runOnUiThread(()->{
                    if(!destroyed){
                        exporting=false;updateActions();
                        status.setText("Erro ao salvar na pasta Noise!");
                        Toast.makeText(this,"Não foi possível salvar a imagem",Toast.LENGTH_LONG).show();
                    }
                });
            }finally{if(output!=null)output.recycle();}
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == 1) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
            load(uri);
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putFloatArray("values", values.clone()); state.putFloatArray("effects",effects.clone()); state.putFloatArray("filters",filters.clone()); state.putFloatArray("ditherControls",ditherControls.clone());
        state.putInt("outputWidth",outputWidth); state.putInt("outputHeight",outputHeight); state.putBoolean("invert", invertSwitch.isChecked());
        state.putInt("group", selectedGroup); if (input != null) state.putString("input", input.toString());
    }
    @Override protected void onResume() { super.onResume(); if (preview != null) preview.onResume(); }
    @Override protected void onPause() { comparing = false; publish(); if (preview != null) preview.onPause(); super.onPause(); }
    @Override protected void onDestroy() { closeCropEditor(); destroyed = true; loads.incrementAndGet(); worker.shutdown(); if(galleryScreen!=null)galleryScreen.dispose(); super.onDestroy(); }
}

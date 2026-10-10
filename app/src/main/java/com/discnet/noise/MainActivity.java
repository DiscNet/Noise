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
    private static final String[] NAMES = {"Saturação", "Vibração", "Exposição", "Contraste", "Highlights", "Branco", "Preto", "Ruído", "Matiz", "Dither", "Glow sem blur", "Desvio RGB", "Fade", "Tom de pele", "Poeira", "Vinheta", "Aberrações", "Névoa", "Nitidez", "Realce de bordas", "Profundidade", "Posição X", "Posição Y", "Escala", "Densidade", "Ondulação", "Intensidade", "Espaçamento", "Espessura", "Centro X", "Centro Y", "Desgaste", "Intensidade", "Frequência", "Suavidade", "Curvatura", "Vinheta CRT", "Tom do fósforo", "Intensidade", "Faixas", "Deslocamento", "Falhas", "Estática", "Monocromia", "Densidade", "Contraste ASCII", "Brilho ASCII", "Limiar", "Espaçamento", "Tamanho"};
    private static final String[] ICONS = {"☼","◉","✧","◐","✦","◯","●","⁙","◌","≋","✧","◎",
        "◑","◕","⁙","◉","◎","≈","◇","⟳","▤","↔","↕","⌗","∴","〰"};
    private static final int[][] ART_GROUPS = {{26,27,28,29,30,31},{32,33,34,35,36,37},{38,39,40,41,42,43}};
    private static final String[] ART_TITLES={"Anéis", "CRT", "Glitch"};
    private static final String[] ART_DESCRIPTIONS={
        "Gravação concêntrica • linhas finas e desgaste analógico",
        "Tela curva • varredura de fósforo e bordas suaves",
        "Sinal corrompido • rupturas e interferência monocromática"
    };
    private static final int[][] GROUPS = {{0, 3, 1, 2, 4, 5, 6, 8}, {7}, {9, 11, 20, 21, 22, 23, 24, 25}, {12, 13, 14, 15, 16, 17, 10, 18, 19}};
    private final float[] values = new float[9];
    private final float[] effects = new float[3];
    private final float[] filters = new float[8];
    private final float[] ditherControls = EditState.DITHER_DEFAULTS.clone();
    private final float[] artControls = EditState.ART_DEFAULTS.clone();
    private final float[] asciiControls = EditState.ASCII_DEFAULTS.clone();
    private int selectedArtMode=0;
    private int outputWidth, outputHeight, restoreWidth, restoreHeight;
    private ImageView resizeButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger loads = new AtomicInteger();
    private final SeekBar[] sliders = new SeekBar[50];
    private final TextView[] valueLabels = new TextView[50], tabs = new TextView[6];
    private final LinearLayout[] rows = new LinearLayout[50];
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
    private Switch invertSwitch, asciiSwitch, asciiColoredSwitch, asciiDitherSwitch, asciiSymbolsSwitch;
    private ScrollView controlScroll;
    private HorizontalScrollView categoryScroll;
    private LinearLayout topBar, actionDock, adjustmentPanel;
    private FrameLayout editorStage;
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
    /** Compact single-line actions; keep the full 48dp card touch target. */
    private LinearLayout dockAction(ImageView icon, String caption, Runnable command) {
        LinearLayout item=new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBackgroundColor(0xff222329);
        item.setPadding(dp(5),0,dp(5),0);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(6),dp(6),dp(6),dp(6));
        item.addView(icon,lp(dp(33),dp(40)));
        TextView name=text(caption,12,Glass.INK);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        item.addView(name,lp(-2,dp(40)));
        item.setClickable(true);item.setFocusable(true);
        item.setContentDescription(caption);
        item.setOnClickListener(v->command.run());
        return item;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff09080f); getWindow().setNavigationBarColor(0xff09080f);
        Glass.Backdrop background = new Glass.Backdrop(this); appBackground=background; setContentView(background);
        LinearLayout root = vertical(); background.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setPadding(dp(8), dp(2), dp(8), dp(3));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(dp(8) + insets.getSystemWindowInsetLeft(), dp(2) + insets.getSystemWindowInsetTop(),
                dp(8) + insets.getSystemWindowInsetRight(), dp(3) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(6),0,dp(4),0);
        root.addView(topBar,lp(-1,dp(48)));
        LinearLayout titleBlock=vertical();
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Noise!",20,Glass.INK);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        titleBlock.addView(title,lp(-1,dp(42)));
        topBar.addView(titleBlock,new LinearLayout.LayoutParams(0,dp(44),1));
        // Lightroom-like editing workflow: compact top actions, tools at bottom.
        open=IconArt.button(this,IconArt.GALLERY,"Abrir imagem");
        resizeButton=IconArt.button(this,IconArt.RESIZE,"Recortar imagem");
        save=IconArt.button(this,IconArt.SAVE,"Salvar PNG");
        ImageView settings=IconArt.button(this,IconArt.SETTINGS,"Mais opções");
        topBar.addView(open,lp(dp(42),dp(42)));
        topBar.addView(save,lp(dp(42),dp(42)));
        topBar.addView(settings,lp(dp(42),dp(42)));
        open.setOnClickListener(v->pickImage());
        resizeButton.setOnClickListener(v->showResizeDialog());
        save.setOnClickListener(v->chooseOutput());
        settings.setOnClickListener(v->showOptions(settings));

        categoryScroll=new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setFillViewport(false);
        categoryScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout categories=new LinearLayout(this);
        categories.setGravity(Gravity.CENTER_VERTICAL);
        categories.setPadding(dp(4),dp(5),dp(4),dp(5));
        categories.setBackgroundColor(0xff16171b);
        categoryScroll.addView(categories,new android.widget.FrameLayout.LayoutParams(-2,dp(56)));
        LinearLayout.LayoutParams catParams=lp(-1,dp(56));
        catParams.leftMargin=dp(2);catParams.rightMargin=dp(2);
        // Added after the editor controls: categories stay below the sliders.
        String[] labels={"Básico","Ruído","Dither","Efeitos","Arte","ASCII"};
        for(int i=0;i<labels.length;i++){
            final int group=i;
            tabs[i]=text(labels[i],13,Glass.MUTED);
            tabs[i].setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            tabs[i].setGravity(Gravity.CENTER);
            tabs[i].setContentDescription("Categoria "+labels[i]);
            LinearLayout.LayoutParams tabParams=lp(dp(87),dp(46));
            tabParams.leftMargin=dp(2);tabParams.rightMargin=dp(2);
            categories.addView(tabs[i],tabParams);
            tabs[i].setOnClickListener(v->showGroup(group));
            tabs[i].setClickable(true);tabs[i].setFocusable(true);
        }
        TextView cropTool=text("Recortar",13,Glass.MUTED);
        cropTool.setGravity(Gravity.CENTER);
        cropTool.setClickable(true);cropTool.setFocusable(true);
        cropTool.setContentDescription("Abrir recorte por gestos");
        cropTool.setOnClickListener(v->showResizeDialog());
        LinearLayout.LayoutParams cropTabParams=lp(dp(94),dp(46));
        cropTabParams.leftMargin=dp(3);
        categories.addView(cropTool,cropTabParams);

        FrameLayout stage = new FrameLayout(this);
        editorStage=stage;
        stage.setBackgroundColor(0xff101115);
        root.addView(stage, new LinearLayout.LayoutParams(-1,0,0.82f));
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
        TextView icon = text("+", 25, Glass.INK);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon,lp(dp(36),dp(36)));
        gap(empty,8);
        TextView prompt=text("Abrir imagem",17,Glass.INK);
        prompt.setGravity(Gravity.CENTER);
        empty.addView(prompt);
        gap(empty,4);
        TextView hint=text("Selecione uma foto para editar",12,Glass.MUTED);
        hint.setGravity(Gravity.CENTER);
        empty.addView(hint);
        empty.setOnClickListener(v -> pickImage()); empty.setContentDescription("Abrir uma foto para começar");
        stageBadge = text("", 10, Glass.MUTED);
        status = text("Abra uma imagem para começar", 11, Glass.MUTED);
        status.setGravity(Gravity.CENTER);
        root.addView(status, lp(-1, dp(21)));
        // Compact floating graphite glass panel, controls grouped by the tabs above.
        LinearLayout panel = vertical();
        adjustmentPanel=panel;
        panel.setPadding(dp(12),dp(8),dp(12),dp(3));
        panel.setBackgroundColor(0xff18191e);
        root.addView(panel,new LinearLayout.LayoutParams(-1,0,1.18f));
        root.addView(categoryScroll,catParams);
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(tools,lp(-1,dp(43)));
        TextView section=text("AJUSTES",11,Glass.MUTED);
        section.setLetterSpacing(.12f);
        tools.addView(section,new LinearLayout.LayoutParams(0,dp(40),1));
        compare=action("Original",false);
        compare.setTextSize(12);
        tools.addView(compare,lp(dp(88),dp(40)));
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
        TextView reset=action("Redefinir",false);
        reset.setTextSize(12);
        LinearLayout.LayoutParams resetParams=lp(dp(86),dp(40));
        resetParams.leftMargin=dp(4);
        tools.addView(reset,resetParams);
        reset.setOnClickListener(v->reset());
        View rule=new View(this);
        rule.setBackgroundColor(0xff2c2d33);
        panel.addView(rule,lp(-1,dp(1)));
        controlScroll = new ScrollView(this); controlScroll.setFillViewport(false); controlScroll.setVerticalScrollBarEnabled(false);
        panel.addView(controlScroll, new LinearLayout.LayoutParams(-1, 0, 1)); controls = vertical(); controlScroll.addView(controls);
        for (int i = 0; i < 50; i++) buildAdjustment(i);
        invertSwitch = new Switch(this);
        invertSwitch.setText("  Inverter cores");
        android.graphics.drawable.Drawable toggleIcon=new IconArt(IconArt.ORIGINAL,Glass.INK);
        toggleIcon.setBounds(0,0,dp(23),dp(23));
        invertSwitch.setCompoundDrawables(toggleIcon,null,null,null); invertSwitch.setTextSize(14); invertSwitch.setTextColor(Glass.INK);
        invertSwitch.setSwitchMinWidth(dp(45));
        invertSwitch.setContentDescription("Inverter cores");
        invertSwitch.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xfff1eef8,0xffc6c6cf}));
        invertSwitch.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff686678,0xff454550}));
        // Invert is shown only in Basic controls instead of consuming a permanent row.
        invertSwitch.setOnCheckedChangeListener((v, checked)->publish());
        asciiSwitch = asciiToggle("Ativar efeito ASCII");
        asciiColoredSwitch = asciiToggle("Usar cores da foto");
        asciiDitherSwitch = asciiToggle("Cores do Dither");
        asciiDitherSwitch.setContentDescription("Cores do Dither: azul, laranja e vermelho");
        asciiSymbolsSwitch = asciiToggle("Conjunto alternativo de caracteres");
        asciiSwitch.setOnCheckedChangeListener((v,checked)->publish());
        asciiColoredSwitch.setOnCheckedChangeListener((v,checked)->{
            if(checked)asciiDitherSwitch.setChecked(false);
            publish();
        });
        asciiDitherSwitch.setOnCheckedChangeListener((v,checked)->{
            if(checked){
                asciiColoredSwitch.setChecked(false);
                if(!resetting)asciiSwitch.setChecked(true);
            }
            publish();
        });
        asciiSymbolsSwitch.setOnCheckedChangeListener((v,checked)->publish());
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
            float[] savedArt=state.getFloatArray("artControls");
            if(savedArt!=null && savedArt.length==18)
                for(int i=0;i<18;i++)sliders[26+i].setProgress(Math.round(savedArt[i]*200));
            selectedArtMode=Math.min(2,Math.max(0,state.getInt("artMode",0)));
            float[] savedAscii=state.getFloatArray("asciiControls");
            if(savedAscii!=null&&savedAscii.length==6)
                for(int i=0;i<6;i++)sliders[44+i].setProgress(Math.round(savedAscii[i]*200));
            asciiSwitch.setChecked(state.getBoolean("asciiEnabled",false));
            asciiColoredSwitch.setChecked(state.getBoolean("asciiColored",false));
            asciiDitherSwitch.setChecked(state.getBoolean("asciiDither",false));
            asciiSymbolsSwitch.setChecked(state.getBoolean("asciiSymbols",false));
            restoreWidth=state.getInt("outputWidth",0);
            restoreHeight=state.getInt("outputHeight",0);
            resetting = false; showGroup(state.getInt("group", 0)); publish();
            String uri = state.getString("input"); if (uri != null) load(Uri.parse(uri));
        }
        if(state==null||state.getString("input")==null)showGallery();
    }
    private Switch asciiToggle(String label) {
        Switch toggle=new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(13);
        toggle.setTextColor(Glass.INK);
        toggle.setSwitchMinWidth(dp(45));
        toggle.setPadding(dp(5),dp(2),dp(5),dp(2));
        toggle.setThumbTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
            new int[]{0xfff1eef8,0xffc7c7d1}));
        toggle.setTrackTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
            new int[]{0xff686678,0xff43434f}));
        return toggle;
    }
    private int defaultProgress(int index) {
        if(index>=44)return Math.round(EditState.ASCII_DEFAULTS[index-44]*200);
        if(index>=26)return Math.round(EditState.ART_DEFAULTS[index-26]*200);
        return (index<9||index>=20)?100:0;
    }
    /** Only effect strength knobs exceed 100%. Positions and spatial controls do not. */
    private boolean hasExtendedRange(int index) {
        return index==9||index==10 || (index>=12&&index<=15)
            || index==17||index==18||index==19
            || index==26||index==32||index==38;
    }
    private void buildAdjustment(int index) {
        LinearLayout row=vertical();
        row.setPadding(dp(4),dp(2),dp(4),dp(3));
        row.setMinimumHeight(dp(60));
        rows[index]=row;
        LinearLayout heading=new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(heading,lp(-1,dp(25)));
        TextView label=text(NAMES[index],13,Glass.INK);
        label.setSingleLine(true);label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,dp(25),1);
        nameParams.leftMargin=dp(2);
        heading.addView(label,nameParams);
        TextView value=text("0",12,Glass.MUTED);
        value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        valueLabels[index]=value;
        heading.addView(value,lp(dp(72),dp(25)));
        Glass.Slider slider=new Glass.Slider(this,0xffd0c5e4);
        sliders[index]=slider;slider.setContentDescription(NAMES[index]);
        slider.setMax(hasExtendedRange(index)?400:200);slider.setProgress(defaultProgress(index));
        row.addView(slider,lp(-1,dp(35)));
        value.setOnClickListener(v->sliders[index].setProgress(defaultProgress(index)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seek,int progress,boolean user){
                if(index<9)values[index]=(progress-100)/100f;
                else if(index<12)effects[index-9]=progress/200f;
                else if(index<20)filters[index-12]=progress/200f;
                else if(index<26)ditherControls[index-20]=progress/200f;
                else if(index<44)artControls[index-26]=progress/200f;
                else asciiControls[index-44]=progress/200f;
                valueLabels[index].setText(formatValue(index));publish();
            }
            public void onStartTrackingTouch(SeekBar seek){}
            public void onStopTrackingTouch(SeekBar seek){}
        });
    }
    private String formatValue(int index) {
        if(hasExtendedRange(index)){
            float amount=index<12?effects[index-9]:
                index<20?filters[index-12]:artControls[index-26];
            return Math.round(amount*100f)+"%";
        }
        if(index==44)return Math.round(24+86*asciiControls[0])+" col";
        if(index>=44)return String.format(Locale.US,"%d",Math.round(asciiControls[index-44]*100));
        if(index>=26)return String.format(Locale.US,"%d",Math.round(artControls[index-26]*100));
        if(index>=20)return String.format(Locale.US,"%d",Math.round(ditherControls[index-20]*100));
        if(index>=12)return String.format(Locale.US,"%d",Math.round(filters[index-12]*100));
        if(index>=9)return String.format(Locale.US,"%d",Math.round(effects[index-9]*100));
        if(index==2)return String.format(Locale.US,"%+.1f EV",values[index]*3);
        if(index==8)return String.format(Locale.US,"%+d°",Math.round(values[index]*180));
        int n=Math.round(values[index]*100);
        return n==0?"0":String.format(Locale.US,"%+d",n);
    }
    private void showGroup(int group){
        selectedGroup=Math.max(0,Math.min(5,group));
        if(selectedGroup==4){showArtPanel();return;}
        if(selectedGroup==5){showAsciiPanel();return;}
        controls.removeAllViews();
        for(int index:GROUPS[selectedGroup]){
            controls.addView(rows[index]);
            View separator=new View(this);
            separator.setBackgroundColor(0xff26272e);
            controls.addView(separator,lp(-1,dp(1)));
        }
        if(selectedGroup==0)controls.addView(invertSwitch,lp(-1,dp(48)));
        if(selectedGroup==1){
            TextView hint=text("− Suavizar     /     + Granular",12,Glass.MUTED);
            controls.addView(hint,lp(-1,dp(32)));
        }
        if(selectedGroup==2){
            TextView preset=action("Aplicar Neon",false);
            preset.setContentDescription("Aplicar estilo neon ondulado com brilhos e pontos");
            controls.addView(preset,lp(-1,dp(38)));
            preset.setOnClickListener(v->{
                sliders[9].setProgress(188);
                sliders[10].setProgress(132);
                sliders[11].setProgress(26);
                for(int i=20;i<26;i++)sliders[i].setProgress(100);
                status.setText("Neon da referência aplicado · ajuste a intensidade");
            });
        }
        updateTabs();
        controlScroll.scrollTo(0,0);
    }
    private void updateTabs(){
        for(int i=0;i<6;i++){
            boolean active=i==selectedGroup;
            tabs[i].setSelected(active);tabs[i].setTextColor(active?Glass.INK:Glass.MUTED);
            tabs[i].setBackground(Glass.panel(this,active?0xff303137:0xff16171b,
                active?0xff303137:0xff16171b,12,0));
        }
        if(categoryScroll!=null){
            int destination=tabs[selectedGroup].getLeft()
                -(categoryScroll.getWidth()-tabs[selectedGroup].getWidth())/2;
            categoryScroll.post(()->categoryScroll.smoothScrollTo(Math.max(0,destination),0));
        }
    }
    private void showArtPanel(){
        controls.removeAllViews();
        LinearLayout strip=new LinearLayout(this);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(3),dp(3),dp(3),dp(3));
        strip.setBackgroundColor(0xff222329);
        LinearLayout.LayoutParams stripParams=lp(-1,dp(46));
        stripParams.topMargin=dp(6);stripParams.bottomMargin=dp(9);
        controls.addView(strip,stripParams);
        for(int j=0;j<3;j++){
            final int chosen=j;
            TextView chip=action(ART_TITLES[j],false);
            chip.setTextSize(13);
            chip.setTextColor(j==selectedArtMode?Glass.INK:Glass.MUTED);
            chip.setBackground(Glass.panel(this,
                j==selectedArtMode?0xff3b3c44:0xff222329,
                j==selectedArtMode?0xff3b3c44:0xff222329,10,0));
            strip.addView(chip,new LinearLayout.LayoutParams(0,dp(38),1));
            chip.setOnClickListener(v->{selectedArtMode=chosen;showArtPanel();});
        }
        TextView caption=text(ART_DESCRIPTIONS[selectedArtMode],12,Glass.MUTED);
        caption.setGravity(Gravity.CENTER);
        controls.addView(caption,lp(-1,dp(27)));
        for(int idx:ART_GROUPS[selectedArtMode]){
            controls.addView(rows[idx]);
            View line=new View(this);line.setBackgroundColor(0x1fffffff);
            controls.addView(line,lp(-1,dp(1)));
        }
        TextView apply=action("Ativar efeito",false);
        LinearLayout.LayoutParams presetParams=lp(-1,dp(38));presetParams.topMargin=dp(4);
        controls.addView(apply,presetParams);
        apply.setOnClickListener(v->sliders[26+selectedArtMode*6].setProgress(200));
        updateTabs();controlScroll.scrollTo(0,0);
    }
    private void showAsciiPanel(){
        controls.removeAllViews();
        controls.addView(asciiSwitch,lp(-1,dp(48)));
        TextView description=text(
            "Imagem feita de caracteres. Cores do Dither: azul, laranja e vermelho.",12,Glass.MUTED);
        description.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(description,lp(-1,dp(31)));
        controls.addView(asciiDitherSwitch,lp(-1,dp(43)));
        controls.addView(asciiColoredSwitch,lp(-1,dp(43)));
        for(int i=44;i<50;i++){
            controls.addView(rows[i]);
            View line=new View(this);line.setBackgroundColor(0x1fffffff);
            controls.addView(line,lp(-1,dp(1)));
        }
        controls.addView(asciiSymbolsSwitch,lp(-1,dp(43)));
        TextView reference=action("Estilo de referência",false);
        LinearLayout.LayoutParams presetParams=lp(-1,dp(38));
        presetParams.topMargin=dp(3);
        controls.addView(reference,presetParams);
        reference.setOnClickListener(v->{
            asciiColoredSwitch.setChecked(false);
            asciiDitherSwitch.setChecked(false);
            asciiSymbolsSwitch.setChecked(false);
            for(int i=0;i<6;i++)sliders[44+i].setProgress(defaultProgress(44+i));
            asciiSwitch.setChecked(true);
            status.setText("ASCII branco sobre preto · ajuste cada caractere");
        });
        updateTabs();controlScroll.scrollTo(0,0);
    }
    private EditState snapshotEdits(boolean showOriginal) {
        return new EditState(values, effects, filters, ditherControls, artControls,
            asciiControls,asciiSwitch.isChecked(),asciiColoredSwitch.isChecked(),
            asciiDitherSwitch.isChecked(),asciiSymbolsSwitch.isChecked(),
            invertSwitch != null && invertSwitch.isChecked(),showOriginal);
    }
    private void publish() {
        if (preview == null || resetting) return;
        preview.setEditState(snapshotEdits(comparing));
        if (stageBadge != null) stageBadge.setText(comparing ? "PRÉVIA  /  ORIGINAL" : "PRÉVIA  /  EDITADA");
        if (compare != null) compare.setText(comparing ? "Original" : "Comparar");
    }
    private void reset() {
        resetting = true;
        for (int i=0;i<sliders.length;i++)sliders[i].setProgress(defaultProgress(i));
        invertSwitch.setChecked(false);
        asciiSwitch.setChecked(false);asciiColoredSwitch.setChecked(false);
        asciiDitherSwitch.setChecked(false);
        asciiSymbolsSwitch.setChecked(false);
        comparing = false; resetting = false; publish();
    }
    private void updateActions() {
        boolean available = original != null && !loading;
        save.setEnabled(available && !exporting); save.setAlpha(save.isEnabled() ? 1 : .4f);
        open.setEnabled(!loading); open.setAlpha(loading ? .4f : 1);
        resizeButton.setEnabled(available && !exporting); resizeButton.setAlpha(resizeButton.isEnabled()?1:.4f);
        compare.setEnabled(available); compare.setAlpha(available ? 1 : .45f); invertSwitch.setEnabled(available);
        for (SeekBar slider : sliders) slider.setEnabled(available);
        asciiSwitch.setEnabled(available);asciiColoredSwitch.setEnabled(available);
        asciiDitherSwitch.setEnabled(available);
        asciiSymbolsSwitch.setEnabled(available);
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
        EditState state=snapshotEdits(false);
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
        state.putFloatArray("artControls",artControls.clone());state.putInt("artMode",selectedArtMode);
        state.putFloatArray("asciiControls",asciiControls.clone());
        state.putBoolean("asciiEnabled",asciiSwitch.isChecked());
        state.putBoolean("asciiColored",asciiColoredSwitch.isChecked());
        state.putBoolean("asciiDither",asciiDitherSwitch.isChecked());
        state.putBoolean("asciiSymbols",asciiSymbolsSwitch.isChecked());
        state.putInt("outputWidth",outputWidth); state.putInt("outputHeight",outputHeight); state.putBoolean("invert", invertSwitch.isChecked());
        state.putInt("group", selectedGroup); if (input != null) state.putString("input", input.toString());
    }
    @Override protected void onResume() { super.onResume(); if (preview != null) preview.onResume(); }
    @Override protected void onPause() { comparing = false; publish(); if (preview != null) preview.onPause(); super.onPause(); }
    @Override protected void onDestroy() { closeCropEditor(); destroyed = true; loads.incrementAndGet(); worker.shutdown(); if(galleryScreen!=null)galleryScreen.dispose(); super.onDestroy(); }
}

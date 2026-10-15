package com.discnet.noise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

/** Contextual tools only; the document remains the sole owner of values. */
final class EditorPanel extends LinearLayout {
    interface Host extends PrecisionControl.Listener {EditorDocument document();void action(String action);void updated();void openEffects();}
    final TextView handle;
    final ScrollView scroll;
    private final LinearLayout controls;
    private final Host host;
    private final PrecisionControl[] rows=new PrecisionControl[AdjustmentSpec.COUNT];
    int category,effect;
    EditorPanel(Context c,Host host){super(c);this.host=host;setOrientation(VERTICAL);setBackground(EditorTheme.surface(c,28));
        handle=new TextView(c);handle.setTypeface(NoiseTypography.medium(c));handle.setTextColor(EditorTheme.MUTED);handle.setTextSize(12);handle.setGravity(Gravity.CENTER);handle.setContentDescription("Arraste para ampliar ou recolher o painel. Toque para alternar tamanho.");handle.setFocusable(true);addView(handle,new LayoutParams(-1,dp(48)));
        scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);scroll.setSmoothScrollingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        addView(scroll,new LayoutParams(-1,0,1));
        controls=new LinearLayout(c);controls.setPadding(dp(12),0,dp(12),dp(18));controls.setOrientation(VERTICAL);scroll.addView(controls);
        for(int i=0;i<rows.length;i++)rows[i]=new PrecisionControl(c,i,host);
    }
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
    void select(int group){boolean switched=category!=group;category=group;rebuild();if(switched)scroll.scrollTo(0,0);}
    void bind(int id){rows[id].bind(host.document());}
    void rebuild(){
        int scrollY=scroll.getScrollY();
        controls.removeAllViews();handle.setText("━   "+AdjustmentSpec.CATEGORIES[category]+"   ·   deslize para expandir");EditorDocument d=host.document();
        String name=category==3?AdjustmentSpec.EFFECTS[effect]:AdjustmentSpec.CATEGORIES[category];
        String description=category==0?"Iluminação e contraste":category==1?"Cores e tons":
            category==2?"Textura e definição":category==3?"Efeitos ajustáveis e combináveis":
            category==4?"Padrões e paletas":category==5?"Arte em caracteres":"Transformação e projetos";
        LinearLayout heading=new LinearLayout(getContext());heading.setOrientation(VERTICAL);
        heading.setPadding(dp(10),dp(1),dp(10),dp(12));
        TextView head=new TextView(getContext());head.setText(name);head.setTextColor(EditorTheme.INK);
        head.setTypeface(NoiseTypography.bold(getContext()));head.setTextSize(21);
        heading.addView(head,new LayoutParams(-1,dp(32)));
        TextView sub=new TextView(getContext());sub.setText(description);sub.setTextColor(EditorTheme.MUTED);
        sub.setTypeface(NoiseTypography.regular(getContext()));sub.setTextSize(12);
        heading.addView(sub,new LayoutParams(-1,dp(20)));
        controls.addView(heading,new LayoutParams(-1,-2));
        if(category<3){
            int[] ids=AdjustmentSpec.BASIC[category];
            for(int i=0;i<ids.length;i++){if(i==4&&ids.length>5)groupTitle("Avançados");add(ids[i]);}
            if(category==1)toggle("Inverter cores",d.invert,checked->change(()->d.invert=checked));
            if(category==2)toggle("Grão colorido",d.grainColored,checked->change(()->d.grainColored=checked));
        }else if(category==3){
            TextView gestureHint=new TextView(getContext());
            gestureHint.setText("Borda esquerda → deslize para abrir a roda de efeitos");
            gestureHint.setTextSize(12);
            gestureHint.setTextColor(EditorTheme.MUTED);
            gestureHint.setTypeface(NoiseTypography.medium(getContext()));
            gestureHint.setPadding(dp(8),dp(8),dp(8),dp(16));
            controls.addView(gestureHint,new LayoutParams(-1,-2));
            int[] ids=AdjustmentSpec.EFFECT_PARAMS[effect];effectSwitch(d,ids[0],AdjustmentSpec.EFFECTS[effect]);for(int i:ids)add(i);
        }else if(category==4){
            choices(new String[]{"Neon","Bayer 4×4","Threshold"},d.ditherMethod,n->change(()->{d.ditherMethod=n;if(n>0)d.values[9]=Math.min(1,d.values[9]);rebuild();}));
            effectSwitch(d,9,"Dither");
            rows[9].slider.setMax(d.ditherMethod==0?2000:1000);add(9);
            if(d.ditherMethod>0)choices(new String[]{"Nonqual","P&B","RGB"},d.ditherPalette,n->change(()->{d.ditherPalette=n;rebuild();}));
            for(int i:new int[]{20,23,24})add(i);
            if(d.ditherMethod==0)for(int i:new int[]{21,22,25})add(i);
            if(d.ditherMethod==0)button("Aplicar Nonqual",()->host.action("Neon"));
        }else if(category==5){
            toggle("ASCII",d.asciiEnabled,v->change(()->d.asciiEnabled=v));
            choices(new String[]{"Monocromático","Foto","Nonqual"},d.asciiDither?2:d.asciiColored?1:0,n->change(()->{d.asciiColored=n==1;d.asciiDither=n==2;d.asciiEnabled=true;rebuild();}));
            for(int i=44;i<50;i++)add(i);
            toggle("Símbolos alternativos",d.asciiSymbols,v->change(()->d.asciiSymbols=v));
        }else{
            groupTitle("Transformar");
            for(String a:new String[]{"Recortar","Redimensionar","Girar 90°","Espelhar horizontal","Espelhar vertical"})button(a,()->host.action(a));
            groupTitle("Arquivo");
            button("Redimensionar / exportar",()->host.action("Redimensionar / exportar"));
            groupTitle("Biblioteca");
            for(String a:new String[]{"Presets","Copiar ajustes","Colar ajustes","Salvar projeto","Meus projetos"})button(a,()->host.action(a));
        }
        scroll.post(()->scroll.scrollTo(0,Math.max(0,scrollY)));
        if(category<6){TextView hint=new TextView(getContext());hint.setText("Toque no valor para digitar · dois toques no nome restauram · segure para pausar");hint.setTextSize(11);hint.setTextColor(EditorTheme.MUTED);hint.setPadding(dp(4),dp(12),dp(4),0);controls.addView(hint,new LayoutParams(-1,-2));}
    }
    private void groupTitle(String title){TextView label=new TextView(getContext());label.setText(title.toUpperCase(java.util.Locale.getDefault()));
        label.setTextSize(10.5f);label.setLetterSpacing(.1f);label.setTextColor(EditorTheme.MUTED);
        label.setTypeface(NoiseTypography.medium(getContext()));label.setPadding(dp(8),dp(12),0,dp(8));
        controls.addView(label,new LayoutParams(-1,-2));}
    private void add(int id){PrecisionControl row=rows[id];
        if(row.getParent()!=null)((ViewGroup)row.getParent()).removeView(row);
        row.bind(host.document());LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(8);controls.addView(row,p);}
    private void change(Runnable r){host.begin();r.run();host.updated();host.end();}
    private void effectSwitch(EditorDocument d,int id,String name){
        toggle(name,!d.disabled[id]&&d.values[id]>0,enabled->change(()->{d.disabled[id]=!enabled;if(enabled&&d.values[id]==0)d.values[id]=id==9||id==26||id==32||id==38?1:.5f;bind(id);}));
    }
    private interface Checked{void set(boolean b);}
    private void toggle(String name,boolean state,Checked changed){Switch v=new Switch(getContext());v.setText(name);v.setTextSize(14);v.setTextColor(EditorTheme.INK);v.setPadding(dp(4),dp(6),dp(4),dp(6));v.setMinHeight(dp(48));v.setChecked(state);v.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{EditorTheme.LIME,0xffb6b9c1}));v.setTrackTintList(ColorStateList.valueOf(0xff38343f));controls.addView(v,new LayoutParams(-1,-2));v.setOnCheckedChangeListener((b,c)->changed.set(c));}
    private interface Selected{void set(int i);}
    private void choices(String[] names,int active,Selected select){HorizontalScrollView h=new HorizontalScrollView(getContext());h.setHorizontalScrollBarEnabled(false);LinearLayout line=new LinearLayout(getContext());h.addView(line);controls.addView(h,new LayoutParams(-1,dp(50)));for(int i=0;i<names.length;i++){final int n=i;TextView b=new TextView(getContext());b.setText(names[i]);b.setTextSize(12);b.setTextColor(i==active?EditorTheme.LIME:EditorTheme.MUTED);b.setGravity(Gravity.CENTER);b.setPadding(dp(14),0,dp(14),0);b.setBackground(EditorTheme.panel(getContext(),i==active?0xff3b2d49:0xff17161d,0,8,0));b.setSelected(i==active);b.setFocusable(true);b.setContentDescription(names[i]+(i==active?", selecionado":""));line.addView(b,new LayoutParams(-2,dp(48)));b.setOnClickListener(v->select.set(n));}}
    private void button(String title,Runnable r){TextView b=new TextView(getContext());b.setText(title);b.setTypeface(NoiseTypography.medium(getContext()));b.setTextSize(14);b.setTextColor(EditorTheme.INK);b.setGravity(Gravity.CENTER_VERTICAL);EditorTheme.button(b,false);b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setFocusable(true);LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(6);b.setMinHeight(dp(48));controls.addView(b,p);b.setOnClickListener(v->r.run());}
}

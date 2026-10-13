package com.discnet.noise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

/** Contextual tools only; the document remains the sole owner of values. */
final class EditorPanel extends LinearLayout {
    interface Host extends PrecisionControl.Listener {EditorDocument document();void action(String action);void updated();}
    final TextView handle;
    final ScrollView scroll;
    private final LinearLayout controls;
    private final Host host;
    private final PrecisionControl[] rows=new PrecisionControl[AdjustmentSpec.COUNT];
    int category,effect;
    EditorPanel(Context c,Host host){super(c);this.host=host;setOrientation(VERTICAL);setBackgroundColor(0xff1b1d22);
        handle=new TextView(c);handle.setTextColor(EditorTheme.MUTED);handle.setTextSize(12);handle.setGravity(Gravity.CENTER);handle.setContentDescription("Arraste para ampliar ou recolher o painel. Toque para alternar tamanho.");handle.setFocusable(true);addView(handle,new LayoutParams(-1,dp(48)));
        scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.setClipToPadding(false);addView(scroll,new LayoutParams(-1,0,1));
        controls=new LinearLayout(c);controls.setPadding(dp(12),0,dp(12),dp(16));controls.setOrientation(VERTICAL);scroll.addView(controls);
        for(int i=0;i<rows.length;i++)rows[i]=new PrecisionControl(c,i,host);
    }
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
    void select(int group){category=group;rebuild();scroll.scrollTo(0,0);}
    void bind(int id){rows[id].bind(host.document());}
    void rebuild(){
        controls.removeAllViews();handle.setText("―   "+AdjustmentSpec.CATEGORIES[category]+"   ⌃");EditorDocument d=host.document();
        if(category<3){for(int i:AdjustmentSpec.BASIC[category])add(i);
            if(category==1)toggle("Inverter cores",d.invert,checked->change(()->d.invert=checked));
            if(category==2)toggle("Grão colorido",d.grainColored,checked->change(()->d.grainColored=checked));
        }else if(category==3){
            choices(AdjustmentSpec.EFFECTS,effect,n->{effect=n;rebuild();scroll.scrollTo(0,0);});
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
            for(String a:new String[]{"Recortar","Girar 90°","Espelhar horizontal","Espelhar vertical","Redimensionar / exportar","Presets","Copiar ajustes","Colar ajustes","Salvar projeto","Meus projetos"})button(a,()->host.action(a));
        }
        if(category<6){TextView hint=new TextView(getContext());hint.setText("Toque no valor para digitar · dois toques no nome restauram · segure para pausar");hint.setTextSize(11);hint.setTextColor(EditorTheme.MUTED);hint.setPadding(dp(4),dp(12),dp(4),0);controls.addView(hint,new LayoutParams(-1,-2));}
    }
    private void add(int id){PrecisionControl row=rows[id];if(row.getParent()!=null)((ViewGroup)row.getParent()).removeView(row);row.bind(host.document());controls.addView(row,new LayoutParams(-1,-2));}
    private void change(Runnable r){host.begin();r.run();host.updated();host.end();}
    private void effectSwitch(EditorDocument d,int id,String name){
        toggle(name,!d.disabled[id]&&d.values[id]>0,enabled->change(()->{d.disabled[id]=!enabled;if(enabled&&d.values[id]==0)d.values[id]=id==9||id==26||id==32||id==38?1:.5f;bind(id);}));
    }
    private interface Checked{void set(boolean b);}
    private void toggle(String name,boolean state,Checked changed){Switch v=new Switch(getContext());v.setText(name);v.setTextSize(14);v.setTextColor(EditorTheme.INK);v.setPadding(dp(4),dp(6),dp(4),dp(6));v.setMinHeight(dp(48));v.setChecked(state);v.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{EditorTheme.LIME,0xffb6b9c1}));v.setTrackTintList(ColorStateList.valueOf(0xff454953));controls.addView(v,new LayoutParams(-1,-2));v.setOnCheckedChangeListener((b,c)->changed.set(c));}
    private interface Selected{void set(int i);}
    private void choices(String[] names,int active,Selected select){HorizontalScrollView h=new HorizontalScrollView(getContext());h.setHorizontalScrollBarEnabled(false);LinearLayout line=new LinearLayout(getContext());h.addView(line);controls.addView(h,new LayoutParams(-1,dp(50)));for(int i=0;i<names.length;i++){final int n=i;TextView b=new TextView(getContext());b.setText(names[i]);b.setTextSize(12);b.setTextColor(i==active?EditorTheme.LIME:EditorTheme.MUTED);b.setGravity(Gravity.CENTER);b.setPadding(dp(14),0,dp(14),0);b.setBackground(EditorTheme.panel(getContext(),i==active?0xff303641:0xff1b1d22,0,8,0));b.setSelected(i==active);b.setFocusable(true);b.setContentDescription(names[i]+(i==active?", selecionado":""));line.addView(b,new LayoutParams(-2,dp(48)));b.setOnClickListener(v->select.set(n));}}
    private void button(String title,Runnable r){TextView b=new TextView(getContext());b.setText(title);b.setTextSize(14);b.setTextColor(EditorTheme.INK);b.setGravity(Gravity.CENTER_VERTICAL);EditorTheme.button(b,false);b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setFocusable(true);LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(6);b.setMinHeight(dp(48));controls.addView(b,p);b.setOnClickListener(v->r.run());}
}

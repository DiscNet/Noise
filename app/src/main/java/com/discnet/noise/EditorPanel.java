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
    private final boolean[] advancedExpanded={false,false,false};
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
    /** Excludes the visible sliders' touch zones from edge navigation. */
    boolean isOverAdjustmentBar(int screenX,int screenY){
        android.graphics.Rect hit=new android.graphics.Rect();
        for(PrecisionControl control:rows){
            if(control==null||!control.slider.isShown())continue;
            if(!control.slider.getGlobalVisibleRect(hit))continue;
            // Pad the narrow drawn track, preserving its comfortable touch area.
            hit.inset(0,-EditorTheme.dp(getContext(),8));
            if(hit.contains(screenX,screenY))return true;
        }
        return false;
    }
    void select(int group){
        boolean switched=category!=group;
        category=group;rebuild();
        if(switched)scroll.post(()->scroll.scrollTo(0,0));
    }
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
            int essential=category==0?Math.min(ids.length,6):Math.min(ids.length,4);
            for(int i=0;i<essential;i++)add(ids[i]);
            if(essential<ids.length){
                TextView expand=new TextView(getContext());
                expand.setText((advancedExpanded[category]?"⌄  ":"›  ")+"Ajustes avançados  ·  "+(ids.length-essential));
                expand.setTextSize(12);expand.setTypeface(NoiseTypography.medium(getContext()));
                expand.setTextColor(EditorTheme.LIME);expand.setGravity(Gravity.CENTER_VERTICAL);
                expand.setPadding(dp(12),0,dp(12),0);
                expand.setBackground(EditorTheme.panel(getContext(),0xff26212e,0,12,0));
                LayoutParams ep=new LayoutParams(-1,dp(43));ep.bottomMargin=dp(7);
                controls.addView(expand,ep);
                LinearLayout extras=new LinearLayout(getContext());extras.setOrientation(VERTICAL);
                extras.setVisibility(advancedExpanded[category]?View.VISIBLE:View.GONE);
                controls.addView(extras,new LayoutParams(-1,-2));
                for(int i=essential;i<ids.length;i++)addTo(ids[i],extras);
                final int group=category;
                expand.setOnClickListener(v->{
                    advancedExpanded[group]=!advancedExpanded[group];
                    expand.setText((advancedExpanded[group]?"⌄  ":"›  ")+"Ajustes avançados  ·  "+(ids.length-essential));
                    extras.setVisibility(advancedExpanded[group]?View.VISIBLE:View.GONE);
                    // Expand with a short layout transition; no GPU render is triggered.
                });
                android.animation.LayoutTransition transition=new android.animation.LayoutTransition();
                transition.setDuration(190);
                extras.setLayoutTransition(transition);
                controls.setLayoutTransition(transition);
            }
            if(category==1){
                toggle("Inverter cores",d.invert,checked->change(()->d.invert=checked));
                colorGradeTools(d);
            }
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
    /** Color wheels and curves are actual persistent per-pixel GPU controls. */
    private void colorGradeTools(EditorDocument doc){
        groupTitle("Três rodas cromáticas");
        LinearLayout wheels=new LinearLayout(getContext());
        wheels.setOrientation(HORIZONTAL);
        controls.addView(wheels,new LayoutParams(-1,-2));
        String[] labels={"Sombras","Meios-tons","Realces"};
        for(int i=0;i<3;i++){
            final int index=i,base=i*2;
            LinearLayout cell=new LinearLayout(getContext());cell.setOrientation(VERTICAL);
            LayoutParams layout=new LayoutParams(0,-2,1);
            layout.setMargins(dp(3),0,dp(3),dp(5));
            wheels.addView(cell,layout);
            TextView title=new TextView(getContext());title.setText(labels[i]);title.setTextSize(11.5f);
            title.setTextColor(EditorTheme.INK);title.setTypeface(NoiseTypography.medium(getContext()));
            title.setGravity(Gravity.CENTER);
            cell.addView(title,new LayoutParams(-1,dp(27)));
            ColorWheelControl wheel=new ColorWheelControl(getContext(),new ColorWheelControl.Listener(){
                public void begin(){host.begin();}
                public void change(float x,float y){
                    doc.colorWheels[base]=x;doc.colorWheels[base+1]=y;host.updated();
                }
                public void end(){host.end();}
            });
            wheel.bind(doc.colorWheels[base],doc.colorWheels[base+1]);
            cell.addView(wheel,new LayoutParams(-1,dp(132)));
            TextView restore=new TextView(getContext());restore.setText("↺  Neutro");
            restore.setTextColor(EditorTheme.MUTED);restore.setTextSize(11.5f);
            restore.setGravity(Gravity.CENTER);restore.setBackground(
                EditorTheme.panel(getContext(),0xff29232f,0,11,0));
            cell.addView(restore,new LayoutParams(-1,dp(40)));
            restore.setOnClickListener(v->{
                host.begin();doc.colorWheels[base]=doc.colorWheels[base+1]=0;
                wheel.bind(0,0);host.updated();host.end();
            });
        }
        groupTitle("Curvas de cor");
        String[] types={"Hue × Hue","Saturação × Hue","Saturação × Saturação"};
        for(int type=0;type<3;type++){
            final int group=type,offset=type*6;
            TextView label=new TextView(getContext());label.setText(types[type]);
            label.setTextSize(12);label.setTextColor(EditorTheme.INK);
            label.setTypeface(NoiseTypography.medium(getContext()));
            label.setPadding(dp(7),dp(8),dp(7),dp(4));
            controls.addView(label,new LayoutParams(-1,-2));
            ColorCurveControl curve=new ColorCurveControl(getContext(),type!=2,
                new ColorCurveControl.Listener(){
                    public void begin(){host.begin();}
                    public void change(int knot,float value){
                        doc.colorCurves[offset+knot]=value;host.updated();
                    }
                    public void end(){host.end();}
                });
            curve.bind(doc.colorCurves,offset);
            controls.addView(curve,new LayoutParams(-1,dp(154)));
            TextView restore=new TextView(getContext());restore.setText("↺  Restaurar curva");
            restore.setGravity(Gravity.CENTER);restore.setTextColor(EditorTheme.MUTED);
            restore.setTextSize(11.5f);restore.setBackground(EditorTheme.panel(getContext(),0xff28232e,0,11,0));
            LayoutParams restoreLayout=new LayoutParams(-1,dp(41));restoreLayout.bottomMargin=dp(6);
            controls.addView(restore,restoreLayout);
            restore.setOnClickListener(v->{
                host.begin();
                for(int k=0;k<6;k++)doc.colorCurves[offset+k]=0;
                curve.bind(doc.colorCurves,offset);host.updated();host.end();
            });
        }
        groupTitle("Mesclagem de cor e luz");
        choices(new String[]{"Normal","Multiplicar","Tela","Sobrepor","Luz suave"},
            doc.blendMode,index->change(()->{
                doc.blendMode=index;rebuild();
            }));
        add(64);
    }
    private void groupTitle(String title){TextView label=new TextView(getContext());label.setText(title.toUpperCase(java.util.Locale.getDefault()));
        label.setTextSize(10.5f);label.setLetterSpacing(.1f);label.setTextColor(EditorTheme.MUTED);
        label.setTypeface(NoiseTypography.medium(getContext()));label.setPadding(dp(8),dp(12),0,dp(8));
        controls.addView(label,new LayoutParams(-1,-2));}
    private void add(int id){addTo(id,controls);}
    private void addTo(int id,LinearLayout destination){
        PrecisionControl row=rows[id];
        if(row.getParent()!=null)((ViewGroup)row.getParent()).removeView(row);
        row.bind(host.document());
        LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(8);
        destination.addView(row,p);
    }
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

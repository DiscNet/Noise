package com.discnet.noise;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;

/** In-app, Manrope-styled settings. Preferences are not stored in projects. */
final class SettingsScreen extends FrameLayout {
    static final String PREFS="noise_ui_settings";
    static final String RESIZE_PANEL="resize_adjustment_panel";
    interface Listener {void close();void panelResizeChanged(boolean enabled);}
    private final SharedPreferences prefs;
    private final Listener listener;
    private final TextView toggle,state;
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
    static boolean resizePanelEnabled(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean(RESIZE_PANEL,false);
    }
    SettingsScreen(Context c,Listener listener){
        super(c);this.listener=listener;
        prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        setBackgroundColor(0xff0c0b10);
        setClickable(true);setFocusableInTouchMode(true);
        LinearLayout page=new LinearLayout(c);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20),dp(14),dp(20),dp(16));
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        addView(scroll,new LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(c);bar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(bar,new LinearLayout.LayoutParams(-1,dp(64)));
        ImageView back=IconArt.button(c,IconArt.BACK,"Voltar");
        bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->listener.close());
        TextView heading=text("Configurações",22,true);
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,-2,1);
        headingParams.leftMargin=dp(9);bar.addView(heading,headingParams);

        TextView section=text("INTERFACE DE EDIÇÃO",11,false);
        section.setLetterSpacing(.10f);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(49));
        sp.topMargin=dp(17);page.addView(section,sp);

        LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15),dp(13),dp(15),dp(14));
        card.setBackground(EditorTheme.panel(c,0xff1c1a23,0,16,0));
        page.addView(card,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row,new LinearLayout.LayoutParams(-1,dp(53)));
        TextView label=text("Redimensionar painel de ajustes",14,true);
        row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        toggle=text("",12.5f,true);toggle.setGravity(Gravity.CENTER);
        toggle.setMinWidth(dp(82));toggle.setMinHeight(dp(44));
        row.addView(toggle,new LinearLayout.LayoutParams(dp(86),dp(44)));
        TextView description=text("Permite subir, descer ou alternar a altura da caixa de ajustes. Desativado, o painel mantém seu tamanho.",12,false);
        description.setLineSpacing(dp(3),1f);
        LinearLayout.LayoutParams descriptionParams=new LinearLayout.LayoutParams(-1,-2);
        descriptionParams.topMargin=dp(6);card.addView(description,descriptionParams);
        state=text("",11.5f,false);
        LinearLayout.LayoutParams stateParams=new LinearLayout.LayoutParams(-1,dp(36));
        card.addView(state,stateParams);
        card.setOnClickListener(v->setEnabledState(!enabledState()));
        toggle.setOnClickListener(v->setEnabledState(!enabledState()));
        card.setFocusable(true);card.setContentDescription("Ativar ou desativar redimensionamento do painel");
        refresh();

        TextView fluidHeading=text("FUNDO DA TELA INICIAL",11,false);
        fluidHeading.setLetterSpacing(.10f);
        LinearLayout.LayoutParams fluidHeadingParams=new LinearLayout.LayoutParams(-1,dp(49));
        fluidHeadingParams.topMargin=dp(12);page.addView(fluidHeading,fluidHeadingParams);
        LinearLayout fluidCard=new LinearLayout(c);fluidCard.setOrientation(LinearLayout.VERTICAL);
        fluidCard.setPadding(dp(15),dp(10),dp(15),dp(12));
        fluidCard.setBackground(EditorTheme.panel(c,0xff1c1a23,0,7,0));
        page.addView(fluidCard,new LinearLayout.LayoutParams(-1,-2));
        fluidCard.addView(text("Qualidade da animação",14,true));
        LinearLayout choices=new LinearLayout(c);choices.setOrientation(LinearLayout.VERTICAL);
        fluidCard.addView(choices,new LinearLayout.LayoutParams(-1,-2));
        String[] values={"auto","high","balanced","eco"};
        String[] labels={"Automática","Alto","Equilibrado","Econômico"};
        TextView[] buttons=new TextView[4];
        Runnable refreshQuality=()->{
            String selected=prefs.getString(OrganicBackdrop.QUALITY,"auto");
            for(int i=0;i<buttons.length;i++){
                boolean chosen=values[i].equals(selected);
                buttons[i].setText((chosen?"●  ":"○  ")+labels[i]);
                buttons[i].setSelected(chosen);
                buttons[i].setTextColor(chosen?EditorTheme.LIME:EditorTheme.INK);
                buttons[i].setContentDescription(labels[i]+(chosen?", selecionado":""));
            }
        };
        for(int i=0;i<buttons.length;i++){
            final String value=values[i];buttons[i]=text("",13,true);
            buttons[i].setMinHeight(dp(44));buttons[i].setFocusable(true);
            choices.addView(buttons[i],new LinearLayout.LayoutParams(-1,dp(44)));
            buttons[i].setOnClickListener(v->{prefs.edit().putString(OrganicBackdrop.QUALITY,value).apply();refreshQuality.run();});
        }
        refreshQuality.run();
        TextView fluidInfo=text("A qualidade se ajusta à carga e à economia de bateria. As formas continuam se fundindo em todos os perfis.",12,false);
        fluidInfo.setPadding(0,dp(6),0,dp(8));fluidCard.addView(fluidInfo);
        TextView motion=text("",13,true);motion.setMinHeight(dp(48));motion.setFocusable(true);
        Runnable refreshMotion=()->{
            boolean reduced=prefs.getBoolean(OrganicBackdrop.REDUCE_MOTION,false);
            motion.setText("Reduzir movimento: "+(reduced?"ativado":"desativado"));
            motion.setContentDescription(motion.getText());
        };
        motion.setOnClickListener(v->{prefs.edit().putBoolean(OrganicBackdrop.REDUCE_MOTION,
            !prefs.getBoolean(OrganicBackdrop.REDUCE_MOTION,false)).apply();refreshMotion.run();});
        fluidCard.addView(motion);refreshMotion.run();

        TextView info=text("As preferências desta tela são guardadas no dispositivo e não alteram os projetos salvos.",11.5f,false);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);
        ip.topMargin=dp(20);page.addView(info,ip);
    }
    private TextView text(String text,float size,boolean bold){
        TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);
        v.setTextColor(bold?EditorTheme.INK:EditorTheme.MUTED);
        v.setTypeface(bold?NoiseTypography.bold(getContext()):NoiseTypography.regular(getContext()));
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private boolean enabledState(){return prefs.getBoolean(RESIZE_PANEL,false);}
    private void setEnabledState(boolean enabled){
        prefs.edit().putBoolean(RESIZE_PANEL,enabled).apply();
        refresh();listener.panelResizeChanged(enabled);
    }
    private void refresh(){
        boolean enabled=enabledState();
        toggle.setText(enabled?"Ativado":"Desativado");
        toggle.setTextColor(enabled?0xff26142e:EditorTheme.MUTED);
        toggle.setBackground(EditorTheme.panel(getContext(),
            enabled?EditorTheme.LIME:0xff332f3b,0,10,0));
        state.setText(enabled?"Arraste a alça para ajustar o painel.":"O painel permanecerá no tamanho atual.");
        toggle.setContentDescription("Redimensionamento do painel "+(enabled?"ativado":"desativado"));
    }
}

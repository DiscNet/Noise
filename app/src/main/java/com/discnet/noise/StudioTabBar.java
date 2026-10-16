package com.discnet.noise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One persistent, fully readable navigation rail shared by the three studio pages. */
final class StudioTabBar extends LinearLayout {
    interface Listener { void select(int page); }
    static final int HOME=0,GALLERY=1,PROJECTS=2;
    private final LinearLayout[] tabs=new LinearLayout[3];
    private final ImageView[] icons=new ImageView[3];
    private final TextView[] titles=new TextView[3];
    private int selected=-1;
    private final Listener callback;
    private final int[] drawings={IconArt.PHOTO,IconArt.GALLERY,IconArt.FOLDER};
    private final String[] labels={"Início","Galeria","Projetos"};
    StudioTabBar(Context context,Listener callback){
        super(context);this.callback=callback;
        setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(5),dp(5),dp(5),dp(5));
        setBackground(shape(OrbitalMaterial.BLACK,OrbitalMaterial.DARK_PURPLE,5));
        setElevation(dp(8));
        for(int i=0;i<3;i++){
            final int index=i;
            LinearLayout item=new LinearLayout(context);
            item.setOrientation(VERTICAL);item.setGravity(Gravity.CENTER);
            item.setClickable(true);item.setFocusable(true);
            item.setContentDescription("Abrir "+labels[i]);
            ImageView glyph=new ImageView(context);
            item.addView(glyph,new LayoutParams(dp(21),dp(21)));
            TextView title=new TextView(context);
            title.setText(labels[i]);title.setTextSize(12);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(NoiseTypography.medium(context));
            LayoutParams caption=new LayoutParams(-1,dp(22));
            item.addView(title,caption);
            LayoutParams lp=new LayoutParams(0,-1,1);
            lp.setMargins(dp(2),0,dp(2),0);
            addView(item,lp);
            item.setOnClickListener(v->callback.select(index));
            tabs[i]=item;icons[i]=glyph;titles[i]=title;
        }
        select(HOME);
    }
    void select(int index){
        if(index<0||index>=tabs.length)return;
        selected=index;
        for(int i=0;i<tabs.length;i++){
            boolean current=i==index;
            tabs[i].setBackground(new RippleDrawable(
                ColorStateList.valueOf(current?0x237235ae:0x3cffffff),
                shape(current?OrbitalMaterial.WHITE:OrbitalMaterial.DEEP_PURPLE,
                      current?0xfff3ebfc:OrbitalMaterial.PURPLE,4),null));
            icons[i].setImageDrawable(new IconArt(drawings[i],
                current?OrbitalMaterial.BLACK:OrbitalMaterial.WHITE));
            titles[i].setTextColor(current?OrbitalMaterial.BLACK:OrbitalMaterial.WHITE);
            tabs[i].setSelected(current);
        }
    }
    int selectedTab(){return selected;}
    private GradientDrawable shape(int a,int b,float radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        d.setCornerRadius(dp(radius));return d;
    }
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
}

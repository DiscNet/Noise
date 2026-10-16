package com.discnet.noise;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Non-system save result; no Android AlertDialog after export. */
final class ExportResultOverlay extends FrameLayout {
    ExportResultOverlay(Context context,Runnable done,Runnable share){
        super(context);
        setBackgroundColor(0xf109070e);
        setClickable(true);
        setFocusableInTouchMode(true);
        LinearLayout content=new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(23),dp(25),dp(23),dp(25));
        GradientDrawable panel=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{OrbitalMaterial.DEEP_PURPLE,OrbitalMaterial.BLACK});
        panel.setCornerRadius(dp(6));
        content.setBackground(panel);
        LayoutParams p=new LayoutParams(-1,-2,Gravity.CENTER);
        p.leftMargin=dp(23);p.rightMargin=dp(23);
        addView(content,p);
        TextView title=text("Imagem salva",21,OrbitalMaterial.WHITE);
        title.setTypeface(NoiseTypography.bold(context));
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView detail=text("Disponível na Galeria do Noise! e em Pictures/Noise!.",13,OrbitalMaterial.MUTED);
        content.addView(detail,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView finished=text("Concluir",15,OrbitalMaterial.BLACK);
        finished.setBackground(EditorTheme.buttonSurface(context,4,true));
        finished.setGravity(Gravity.CENTER);
        content.addView(finished,new LinearLayout.LayoutParams(-1,dp(52)));
        finished.setOnClickListener(v->done.run());
        if(share!=null){
            TextView sharing=text("Compartilhar",15,OrbitalMaterial.WHITE);
            sharing.setGravity(Gravity.CENTER);
            GradientDrawable secondary=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{OrbitalMaterial.PURPLE,OrbitalMaterial.DARK_PURPLE});
            secondary.setCornerRadius(dp(4));
            sharing.setBackground(secondary);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));
            sp.topMargin=dp(10);
            content.addView(sharing,sp);
            sharing.setOnClickListener(v->share.run());
        }
    }
    private TextView text(String title,int size,int color){
        TextView t=new TextView(getContext());t.setText(title);t.setTextSize(size);
        t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
}

package com.discnet.noise;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small non-modal Noise confirmation; no system notification or full-screen dialog. */
final class ExportResultOverlay extends FrameLayout {
    ExportResultOverlay(Context context,Runnable done,Runnable share){
        super(context);
        setClickable(false);
        setFocusable(false);
        LinearLayout toast=new LinearLayout(context);
        toast.setOrientation(LinearLayout.HORIZONTAL);
        toast.setGravity(Gravity.CENTER_VERTICAL);
        toast.setPadding(dp(14),dp(7),dp(7),dp(7));
        GradientDrawable background=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xff30213e,0xff15121a});
        background.setCornerRadius(dp(10));
        background.setStroke(Math.max(1,dp(1)),0xff6e5782);
        toast.setBackground(background);
        toast.setElevation(dp(8));
        LayoutParams p=new LayoutParams(-1,dp(64),Gravity.BOTTOM);
        p.leftMargin=dp(17);p.rightMargin=dp(17);p.bottomMargin=dp(25);
        addView(toast,p);
        TextView status=text("Imagem salva em Pictures/Noise!",12,Color.WHITE);
        status.setSingleLine(false);
        toast.addView(status,new LinearLayout.LayoutParams(0,-1,1));
        TextView shareButton=text("Compartilhar",12,Color.WHITE);
        shareButton.setGravity(Gravity.CENTER);
        shareButton.setBackground(chip());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(102),dp(42));
        sp.leftMargin=dp(7);toast.addView(shareButton,sp);
        shareButton.setOnClickListener(v->share.run());
        TextView close=text("×",23,Color.WHITE);
        close.setGravity(Gravity.CENTER);
        toast.addView(close,new LinearLayout.LayoutParams(dp(32),dp(42)));
        close.setContentDescription("Fechar confirmação");
        close.setOnClickListener(v->done.run());
    }
    private GradientDrawable chip(){
        GradientDrawable d=new GradientDrawable();
        d.setColor(0xff53326e);d.setCornerRadius(dp(6));return d;
    }
    private TextView text(String value,int size,int color){
        TextView t=new TextView(getContext());t.setText(value);
        t.setTextColor(color);t.setTextSize(size);return t;
    }
    private int dp(float v){return EditorTheme.dp(getContext(),v);}
}

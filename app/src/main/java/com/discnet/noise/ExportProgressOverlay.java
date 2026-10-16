package com.discnet.noise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Modal progress while an image is actually being rendered and committed to storage. */
final class ExportProgressOverlay extends FrameLayout {
    private final TextView message;

    ExportProgressOverlay(Context context) {
        super(context);
        setBackgroundColor(0xe509070e);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Salvando foto, aguarde a conclusão");
        LinearLayout panel=new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(25),dp(24),dp(25),dp(24));
        GradientDrawable background=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{OrbitalMaterial.DEEP_PURPLE,OrbitalMaterial.BLACK});
        background.setCornerRadius(dp(5));
        background.setStroke(Math.max(1,dp(1)),0xff8d62b7);
        panel.setBackground(background);
        LayoutParams card=new LayoutParams(-1,-2,Gravity.CENTER);
        card.leftMargin=dp(32);
        card.rightMargin=dp(32);
        addView(panel,card);

        TextView title=new TextView(context);
        title.setText("SALVANDO FOTO");
        title.setTextColor(OrbitalMaterial.WHITE);
        title.setTextSize(17);
        title.setLetterSpacing(.10f);
        title.setTypeface(NoiseTypography.bold(context));
        title.setGravity(Gravity.CENTER);
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(38)));

        ProgressBar spinner=new ProgressBar(context,null,android.R.attr.progressBarStyleLarge);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(OrbitalMaterial.WHITE));
        LinearLayout.LayoutParams wheel=new LinearLayout.LayoutParams(dp(48),dp(48));
        wheel.topMargin=dp(12);
        wheel.bottomMargin=dp(15);
        panel.addView(spinner,wheel);

        message=new TextView(context);
        message.setText("Processando e exportando imagem…");
        message.setTextColor(0xffd9c7ee);
        message.setTextSize(12);
        message.setGravity(Gravity.CENTER);
        panel.addView(message,new LinearLayout.LayoutParams(-1,-2));

        TextView note=new TextView(context);
        note.setText("Aguarde a confirmação de salvamento.");
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        note.setTextColor(0xffb9a4cb);
        LinearLayout.LayoutParams noteParams=new LinearLayout.LayoutParams(-1,-2);
        noteParams.topMargin=dp(9);
        panel.addView(note,noteParams);
    }
    void setStage(String status){message.setText(status);}
    private int dp(float size){return EditorTheme.dp(getContext(),size);}
}

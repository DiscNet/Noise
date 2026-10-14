package com.discnet.noise;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Precise slider + editable numeric value; no per-frame jobs or debounce. */
final class PrecisionControl extends LinearLayout {
    interface Listener{void begin();void change(int id,float value);void end();void toggle(int id);}
    final SeekBar slider;private final TextView value,label;
    private final int id;private final Listener listener;private boolean binding;
    PrecisionControl(Context c,int id,Listener listener){super(c);this.id=id;this.listener=listener;setOrientation(VERTICAL);setPadding(dp(4),dp(2),dp(4),dp(6));
        LinearLayout title=new LinearLayout(c);title.setGravity(Gravity.CENTER_VERTICAL);addView(title,new LayoutParams(-1,-2));
        label=new TextView(c);label.setTypeface(NoiseTypography.medium(c));label.setText(AdjustmentSpec.NAMES[id]);label.setTextColor(EditorTheme.INK);label.setTextSize(14);label.setGravity(Gravity.CENTER_VERTICAL);label.setMinHeight(dp(44));label.setMaxLines(2);label.setPadding(dp(4),0,dp(8),0);title.addView(label,new LayoutParams(0,-2,1));
        value=new TextView(c);value.setTextSize(13);value.setTextColor(EditorTheme.LIME);value.setTypeface(Typeface.MONOSPACE);value.setGravity(Gravity.CENTER);value.setMinWidth(dp(78));value.setMinHeight(dp(44));value.setPadding(dp(6),0,dp(6),0);title.addView(value,new LayoutParams(-2,-2));value.setFocusable(true);value.setOnClickListener(v->numeric());
        slider=new EditorTheme.Slider(c,EditorTheme.LIME);slider.setMax(Math.round((AdjustmentSpec.max(id)-AdjustmentSpec.min(id))*1000));slider.setContentDescription(AdjustmentSpec.NAMES[id]);addView(slider,new LayoutParams(-1,dp(40)));
        ((EditorTheme.Slider)slider).neutral(Math.round((AdjustmentSpec.neutral(id)-AdjustmentSpec.min(id))*1000));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){listener.begin();}
            public void onProgressChanged(SeekBar s,int progress,boolean user){if(!binding){float n=AdjustmentSpec.min(id)+progress/1000f;value.setText(AdjustmentSpec.format(id,n));listener.change(id,n);}}
            public void onStopTrackingTouch(SeekBar s){listener.end();}
        });
        GestureDetector doubleTap=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(MotionEvent e){return true;}
            public boolean onDoubleTap(MotionEvent e){listener.begin();listener.change(id,AdjustmentSpec.neutral(id));listener.end();return true;}
            public void onLongPress(MotionEvent e){listener.toggle(id);}
        });
        label.setContentDescription(AdjustmentSpec.NAMES[id]+". Dois toques para restaurar; segure para ativar ou desativar.");
        label.setOnTouchListener((v,e)->doubleTap.onTouchEvent(e));label.setFocusable(true);
        label.setOnClickListener(v->numeric());
    }
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
    void bind(EditorDocument d){binding=true;slider.setProgress(Math.round((d.values[id]-AdjustmentSpec.min(id))*1000));value.setText(AdjustmentSpec.format(id,d.values[id]));label.setAlpha(d.disabled[id]?.5f:1);slider.setAlpha(d.disabled[id]?.4f:1);value.setContentDescription(AdjustmentSpec.NAMES[id]+": "+value.getText()+". Digitar valor.");binding=false;}
    private void numeric(){
        Context c=getContext();LinearLayout form=new LinearLayout(c);form.setPadding(dp(20),dp(8),dp(20),dp(8));form.setOrientation(VERTICAL);
        EditText input=new EditText(c);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        float n=AdjustmentSpec.min(id)+slider.getProgress()/1000f;input.setText(String.format(java.util.Locale.US,"%.2f",AdjustmentSpec.display(id,n)));input.selectAll();form.addView(input);
        TextView range=new TextView(c);range.setText("Intervalo: "+AdjustmentSpec.format(id,AdjustmentSpec.min(id))+" a "+AdjustmentSpec.format(id,AdjustmentSpec.max(id)));range.setTextColor(EditorTheme.MUTED);form.addView(range);
        LinearLayout fine=new LinearLayout(c);form.addView(fine);for(int sign:new int[]{-1,1}){Button b=new Button(c);b.setText(sign<0?"− Ajuste fino":"+ Ajuste fino");fine.addView(b,new LayoutParams(0,dp(48),1));b.setOnClickListener(v->{try{float x=Float.parseFloat(input.getText().toString().replace(',','.'));input.setText(String.format(java.util.Locale.US,"%.2f",x+sign*(id==2?.01f:1)));}catch(NumberFormatException ignored){input.setError("Digite um número");}});}
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(AdjustmentSpec.NAMES[id]).setView(form).setNegativeButton("Cancelar",null).setNeutralButton("Restaurar",(d,w)->{listener.begin();listener.change(id,AdjustmentSpec.neutral(id));listener.end();}).setPositiveButton("Aplicar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{float x=Float.parseFloat(input.getText().toString().replace(',','.'));if(!Float.isFinite(x)||x<AdjustmentSpec.display(id,AdjustmentSpec.min(id))||x>AdjustmentSpec.display(id,AdjustmentSpec.max(id)))throw new NumberFormatException();listener.begin();listener.change(id,AdjustmentSpec.fromDisplay(id,x));listener.end();dialog.dismiss();}catch(NumberFormatException ex){input.setError("Valor fora do intervalo");}}));dialog.show();
    }
}

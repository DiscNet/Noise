package com.discnet.noise.advancedstudio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Touch-first parametric field: scrub its label, drag slider, tap +/- or type an exact value.
 * No modal numeric picker. One undo checkpoint is created at the start of an interaction.
 */
public final class StudioInspectorField extends LinearLayout {
    public interface Callback {
        void begin();
        void preview(float value);
        void finish();
    }
    private final float min,max,step;
    private final Callback callback;
    private final SeekBar slider;
    private final EditText input;
    private float value;
    private boolean editing,dragging;
    private float touchX,initialValue;
    private final int accent=0xffbd95eb,ink=0xffeee9f2;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int fill){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(8));return d;
    }
    private TextView label(String text,int size){
        TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);
        v.setTextColor(ink);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private float clamp(float n){return Math.max(min,Math.min(max,n));}
    public StudioInspectorField(Context context,String name,String suffix,float current,
                                float min,float max,float step,Callback callback){
        super(context);
        this.min=min;this.max=max;this.step=step;this.callback=callback;
        this.value=clamp(current);
        setOrientation(VERTICAL);setPadding(dp(9),dp(5),dp(9),dp(3));
        setBackground(background(0xff27222d));
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header,new LayoutParams(-1,dp(34)));
        TextView title=label(name+"  ↔",12);
        title.setContentDescription(name+"; arraste na horizontal para ajustar");
        header.addView(title,new LayoutParams(0,-1,1));
        TextView minus=label("−",21);minus.setGravity(Gravity.CENTER);
        header.addView(minus,new LayoutParams(dp(30),-1));
        input=new EditText(context);input.setSingleLine(true);input.setGravity(Gravity.CENTER);
        input.setTextSize(12);input.setTextColor(accent);
        input.setPadding(dp(2),0,dp(2),0);
        input.setBackground(background(0xff18151e));
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|
            InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setContentDescription(name+"; editar valor numérico exato");
        header.addView(input,new LayoutParams(dp(70),dp(32)));
        TextView unit=label(suffix,10);unit.setTextColor(0xffafa5b9);
        unit.setGravity(Gravity.CENTER);header.addView(unit,new LayoutParams(dp(28),-1));
        TextView plus=label("+",19);plus.setGravity(Gravity.CENTER);
        header.addView(plus,new LayoutParams(dp(30),-1));
        slider=new SeekBar(context);slider.setMax(1000);
        slider.setProgressTintList(ColorStateList.valueOf(accent));
        slider.setThumbTintList(ColorStateList.valueOf(accent));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(0xff554a5e));
        slider.setContentDescription(name+"; controle deslizante");
        addView(slider,new LayoutParams(-1,dp(25)));
        refresh(false);
        minus.setOnClickListener(v->nudge(-step));
        plus.setOnClickListener(v->nudge(step));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            private boolean began;
            @Override public void onStartTrackingTouch(SeekBar b){began=true;callback.begin();}
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser){
                if(!fromUser)return;
                if(!began){began=true;callback.begin();}
                value=clamp(min+(max-min)*progress/1000f);
                refresh(true);callback.preview(value);
            }
            @Override public void onStopTrackingTouch(SeekBar b){
                if(began){began=false;callback.finish();}
            }
        });
        title.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    touchX=event.getRawX();initialValue=value;dragging=false;return true;
                case MotionEvent.ACTION_MOVE:
                    if(!dragging&&Math.abs(event.getRawX()-touchX)>dp(5)){
                        dragging=true;callback.begin();
                    }
                    if(dragging){
                        float span=event.getRawX()-touchX;
                        float delta=span/dp(5)*step;
                        value=clamp(initialValue+delta);
                        refresh(false);callback.preview(value);
                    }
                    return true;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                    if(dragging){callback.finish();dragging=false;}
                    else input.requestFocus();
                    return true;
                default:return false;
            }
        });
        input.setOnEditorActionListener((view,action,event)->{
            if(action==EditorInfo.IME_ACTION_DONE){
                commitInput();input.clearFocus();return true;
            }return false;
        });
        input.setOnFocusChangeListener((v,focused)->{
            if(!focused)commitInput();
        });
    }
    private void nudge(float difference){
        callback.begin();value=clamp(value+difference);
        refresh(false);callback.preview(value);callback.finish();
    }
    private void refresh(boolean fromSlider){
        if(!input.hasFocus())
            input.setText(String.format(Locale.ROOT,
                step>=1?"%.0f":step>=.1f?"%.1f":"%.2f",value));
        if(!fromSlider)
            slider.setProgress(Math.round((value-min)/(max-min)*1000f));
    }
    private void commitInput(){
        if(editing)return;
        editing=true;
        try{
            float exact=Float.parseFloat(input.getText().toString().trim().replace(',','.'));
            if(Float.isFinite(exact)&&Math.abs(clamp(exact)-value)>.0001f){
                callback.begin();value=clamp(exact);
                callback.preview(value);callback.finish();
            }
        }catch(NumberFormatException ignored){}
        input.clearFocus();editing=false;refresh(false);
    }
}

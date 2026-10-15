package com.discnet.noise;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Compact non-destructive control. The history transaction belongs to one drag. */
final class PrecisionControl extends LinearLayout {
    interface Listener { void begin();void change(int id,float value);void end();void toggle(int id); }
    final SeekBar slider;
    private final TextView label,value,reset;
    private final int id;private final Listener listener;
    private boolean binding,disabled;
    PrecisionControl(Context c,int id,Listener listener){
        super(c);this.id=id;this.listener=listener;
        setOrientation(VERTICAL);
        setPadding(dp(12),dp(7),dp(12),dp(5));
        setBackground(EditorTheme.panel(c,0xff1c1a23,0,16,0));
        setFocusable(false);
        LinearLayout title=new LinearLayout(c);title.setGravity(Gravity.CENTER_VERTICAL);
        addView(title,new LayoutParams(-1,dp(30)));
        label=new TextView(c);label.setText(AdjustmentSpec.NAMES[id]);label.setTextColor(EditorTheme.INK);
        label.setTypeface(NoiseTypography.medium(c));label.setTextSize(13.5f);label.setGravity(Gravity.CENTER_VERTICAL);
        label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setContentDescription(AdjustmentSpec.NAMES[id]+", dois toques para restaurar, segure para alternar");
        title.addView(label,new LayoutParams(0,-1,1));

        reset=new TextView(c);reset.setText("↺");reset.setTextColor(EditorTheme.MUTED);
        reset.setTextSize(20);reset.setGravity(Gravity.CENTER);reset.setContentDescription("Restaurar "+AdjustmentSpec.NAMES[id]);
        title.addView(reset,new LayoutParams(dp(44),dp(40)));
        reset.setOnClickListener(v->restore());

        value=new TextView(c);value.setTextColor(EditorTheme.LIME);value.setTextSize(12.5f);
        value.setTypeface(NoiseTypography.medium(c));value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        value.setMinWidth(dp(64));value.setPadding(dp(5),0,0,0);
        value.setContentDescription("Digitar valor de "+AdjustmentSpec.NAMES[id]);value.setFocusable(true);
        title.addView(value,new LayoutParams(-2,dp(44)));
        value.setOnClickListener(v->numeric());

        slider=new EditorTheme.Slider(c,EditorTheme.LIME);
        slider.setMax(Math.round((AdjustmentSpec.max(id)-AdjustmentSpec.min(id))*1000));
        ((EditorTheme.Slider)slider).neutral(Math.round((AdjustmentSpec.neutral(id)-AdjustmentSpec.min(id))*1000));
        slider.setContentDescription("Ajustar "+AdjustmentSpec.NAMES[id]);
        addView(slider,new LayoutParams(-1,dp(37)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){highlight(true);listener.begin();}
            public void onProgressChanged(SeekBar s,int progress,boolean user){
                if(user&&!binding){float n=AdjustmentSpec.min(id)+progress/1000f;
                    value.setText(AdjustmentSpec.format(id,n));listener.change(id,n);}
            }
            public void onStopTrackingTouch(SeekBar s){listener.end();highlight(false);}
        });
        android.view.GestureDetector gestures=new android.view.GestureDetector(c,
            new android.view.GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onDoubleTap(MotionEvent e){restore();return true;}
                @Override public void onLongPress(MotionEvent e){listener.toggle(id);}
            });
        label.setOnTouchListener((v,e)->gestures.onTouchEvent(e));
        label.setOnClickListener(v->numeric());
    }
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
    private void highlight(boolean active){
        setBackground(EditorTheme.panel(getContext(),active?0xff30263b:0xff1c1a23,0,16,0));
        label.setTextColor(active?EditorTheme.LIME:EditorTheme.INK);
    }
    private void restore(){listener.begin();listener.change(id,AdjustmentSpec.neutral(id));listener.end();}
    void bind(EditorDocument d){
        binding=true;disabled=d.disabled[id];
        slider.setProgress(Math.round((d.values[id]-AdjustmentSpec.min(id))*1000));
        value.setText(AdjustmentSpec.format(id,d.values[id]));
        label.setAlpha(disabled?.48f:1f);slider.setAlpha(disabled?.55f:1f);
        reset.setAlpha(disabled?.6f:1f);
        value.setContentDescription(AdjustmentSpec.NAMES[id]+": "+value.getText()+", toque para editar");
        binding=false;
    }
    private TextView action(String title,boolean primary){
        TextView b=new TextView(getContext());b.setText(title);b.setTextSize(13);b.setTypeface(NoiseTypography.medium(getContext()));
        b.setTextColor(primary?0xff1b1020:EditorTheme.INK);b.setGravity(Gravity.CENTER);
        b.setBackground(EditorTheme.buttonSurface(getContext(),14,primary));b.setMinHeight(dp(44));return b;
    }
    private void numeric(){
        Context c=getContext();
        Dialog dialog=new Dialog(c);
        LinearLayout sheet=new LinearLayout(c);sheet.setOrientation(VERTICAL);sheet.setPadding(dp(18),dp(18),dp(18),dp(18));
        sheet.setBackground(EditorTheme.panel(c,0xff17161d,0,24,0));
        TextView heading=new TextView(c);heading.setText(AdjustmentSpec.NAMES[id]);heading.setTextSize(19);
        heading.setTypeface(NoiseTypography.bold(c));heading.setTextColor(EditorTheme.INK);
        sheet.addView(heading,new LayoutParams(-1,dp(36)));
        TextView range=new TextView(c);
        range.setText("De "+AdjustmentSpec.format(id,AdjustmentSpec.min(id))+
            " até "+AdjustmentSpec.format(id,AdjustmentSpec.max(id)));
        range.setTextColor(EditorTheme.MUTED);range.setTextSize(12);
        sheet.addView(range,new LayoutParams(-1,dp(29)));
        EditText field=new EditText(c);
        field.setSingleLine(true);field.setTextSize(22);field.setTypeface(NoiseTypography.medium(c));
        field.setTextColor(EditorTheme.INK);field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL
            |(AdjustmentSpec.signed(id)?InputType.TYPE_NUMBER_FLAG_SIGNED:0));
        float current=AdjustmentSpec.min(id)+slider.getProgress()/1000f;
        field.setText(String.format(Locale.getDefault(),id==2?"%.2f":"%.1f",AdjustmentSpec.display(id,current)));
        field.setSelection(field.length());
        field.setBackground(EditorTheme.panel(c,0xff24212d,0,12,0));
        field.setPadding(dp(14),0,dp(14),0);
        sheet.addView(field,new LayoutParams(-1,dp(56)));
        LinearLayout nudge=new LinearLayout(c);nudge.setPadding(0,dp(9),0,dp(9));
        for(int sign:new int[]{-1,1}){
            TextView b=action(sign<0?"−":"+",false);
            LayoutParams bp=new LayoutParams(0,dp(44),1);bp.setMargins(dp(3),0,dp(3),0);
            nudge.addView(b,bp);
            b.setOnClickListener(v->{try{
                float x=Float.parseFloat(field.getText().toString().replace(',','.'));
                x=Math.max(AdjustmentSpec.display(id,AdjustmentSpec.min(id)),
                    Math.min(AdjustmentSpec.display(id,AdjustmentSpec.max(id)),x+sign*(id==2?.01f:1f)));
                field.setText(String.format(Locale.getDefault(),id==2?"%.2f":"%.1f",x));
                field.setSelection(field.length());
            }catch(NumberFormatException e){field.setError("Digite um número válido");}});
        }
        sheet.addView(nudge,new LayoutParams(-1,dp(62)));
        LinearLayout buttons=new LinearLayout(c);
        TextView restore=action("Restaurar",false),cancel=action("Cancelar",false),apply=action("Aplicar",true);
        for(TextView b:new TextView[]{restore,cancel,apply}){
            LayoutParams bp=new LayoutParams(0,dp(46),b==apply?1.25f:1f);
            bp.setMargins(dp(3),0,dp(3),0);buttons.addView(b,bp);
        }
        sheet.addView(buttons,new LayoutParams(-1,dp(48)));
        restore.setOnClickListener(v->{restore();dialog.dismiss();});
        cancel.setOnClickListener(v->dialog.dismiss());
        apply.setOnClickListener(v->{
            try{
                float x=Float.parseFloat(field.getText().toString().trim().replace(',','.'));
                if(!Float.isFinite(x)||x<AdjustmentSpec.display(id,AdjustmentSpec.min(id))
                    ||x>AdjustmentSpec.display(id,AdjustmentSpec.max(id)))throw new NumberFormatException();
                listener.begin();listener.change(id,AdjustmentSpec.fromDisplay(id,x));listener.end();dialog.dismiss();
            }catch(NumberFormatException e){field.setError("Use um valor dentro do intervalo indicado");}
        });
        dialog.setContentView(sheet);
        Window w=dialog.getWindow();
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.BOTTOM;p.width=-1;p.dimAmount=.58f;
            w.setAttributes(p);}
        dialog.show();if(w!=null)w.setLayout(-1,-2);
    }
}

package com.discnet.noise.advancedstudio;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Reusable native Noise palette: HSV square, hue track, alpha and presets. */
public final class StudioColorPicker extends LinearLayout {
    public interface Selected {void onColor(int argb);}
    private final float[] hsv=new float[3];
    private int alpha;
    private final View sample;
    private final SeekBar hue,opacity;
    private final Field square;
    public static void show(Context ctx,String title,int initial,Selected onSelect){
        StudioColorPicker picker=new StudioColorPicker(ctx,initial);
        new AlertDialog.Builder(ctx).setTitle(title).setView(picker)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(d,w)->onSelect.onColor(picker.color())).show();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private StudioColorPicker(Context ctx,int initial){
        super(ctx);setOrientation(VERTICAL);setPadding(dp(18),dp(8),dp(18),dp(8));
        Color.colorToHSV(initial,hsv);alpha=Color.alpha(initial);
        square=new Field(ctx);
        addView(square,new LayoutParams(-1,dp(208)));
        TextView hueText=label(ctx,"MATIZ");
        addView(hueText);
        hue=new SeekBar(ctx);hue.setMax(360);hue.setProgress(Math.round(hsv[0]));
        addView(hue,new LayoutParams(-1,dp(42)));
        TextView aText=label(ctx,"TRANSPARÊNCIA");
        addView(aText);
        opacity=new SeekBar(ctx);opacity.setMax(255);opacity.setProgress(alpha);
        addView(opacity,new LayoutParams(-1,dp(42)));
        sample=new View(ctx);addView(sample,new LayoutParams(-1,dp(31)));
        LinearLayout presets=new LinearLayout(ctx);
        int[] colors={0xffffffff,0xff101010,0xfff94a70,0xffffc64d,0xff52d8bd,0xff5189ff,0xffad83ed};
        for(int c:colors){
            TextView chip=label(ctx,"■");chip.setTextSize(23);chip.setTextColor(c);
            chip.setGravity(Gravity.CENTER);
            presets.addView(chip,new LayoutParams(0,dp(39),1));
            chip.setOnClickListener(v->{Color.colorToHSV(c,hsv);alpha=Color.alpha(c);
                hue.setProgress(Math.round(hsv[0]));opacity.setProgress(alpha);
                refresh();});
        }
        addView(presets);
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int n,boolean user){
                if(user){hsv[0]=n;refresh();}
            }
        });
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int n,boolean user){
                if(user){alpha=n;refresh();}
            }
        });
        refresh();
    }
    private TextView label(Context ctx,String s){
        TextView t=new TextView(ctx);t.setText(s);t.setTextColor(0xffc6b9d5);
        t.setTextSize(11);return t;
    }
    private int color(){return Color.HSVToColor(alpha,hsv);}
    private void refresh(){sample.setBackgroundColor(color());square.invalidate();}
    private final class Field extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Field(Context ctx){super(ctx);}
        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();
            if(w<1||h<1)return;
            p.setShader(new LinearGradient(0,0,w,0,Color.WHITE,
                Color.HSVToColor(new float[]{hsv[0],1,1}),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);
            p.setShader(new LinearGradient(0,0,0,h,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            float x=hsv[1]*w,y=(1-hsv[2])*h;
            p.setColor(Color.BLACK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));
            c.drawCircle(x,y,dp(9),p);
            p.setColor(Color.WHITE);c.drawCircle(x,y,dp(7),p);
            p.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN||
                e.getActionMasked()==MotionEvent.ACTION_MOVE){
                hsv[1]=Math.max(0,Math.min(1,e.getX()/Math.max(1,getWidth())));
                hsv[2]=1-Math.max(0,Math.min(1,e.getY()/Math.max(1,getHeight())));
                refresh();return true;
            }return true;
        }
    }
}

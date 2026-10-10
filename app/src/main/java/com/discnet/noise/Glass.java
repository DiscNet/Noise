package com.discnet.noise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;

/** Noise! workspace styling: opaque neutral surfaces, restrained typography, no glass. */
final class Glass {
    static final int INK=0xfff4f4f6, MUTED=0xffa8aab3;
    static final int LIME=0xffdddddf, CYAN=0xffd9dbdf, VIOLET=0xffdddddf;
    static int dp(Context context,float n){
        return Math.round(n*context.getResources().getDisplayMetrics().density);
    }
    static GradientDrawable panel(Context context,int first,int second,float radius,int border){
        GradientDrawable d=new GradientDrawable();
        d.setColor(first);
        d.setCornerRadius(dp(context,radius));
        // No colored bevels, inner reflections, gradients or transparency tricks.
        if((border>>>24)>0x50)d.setStroke(dp(context,1),0xff3a3b42);
        return d;
    }
    static void button(View view,boolean primary){
        Context c=view.getContext();
        GradientDrawable background=panel(c,primary?0xffededf0:0xff27282e,0,10,0);
        view.setBackground(new RippleDrawable(
            ColorStateList.valueOf(primary?0x33000000:0x28ffffff),background,null));
        view.setMinimumHeight(dp(c,40));
        view.setPadding(dp(c,7),dp(c,5),dp(c,7),dp(c,5));
    }
    static final class Backdrop extends FrameLayout {
        Backdrop(Context context){
            super(context);
            setBackgroundColor(0xff101115);
        }
    }
    /** Compatibility name for older views; returns an opaque flat panel. */
    static Drawable frosted(Context context,float radius){
        return panel(context,0xff202126,0xff202126,radius,0);
    }
    /** Compatibility name for older views; returns an opaque flat panel. */
    static Drawable liquid(Context context,float radius,boolean primary){
        return panel(context,primary?0xffededf0:0xff25262b,0,radius,0);
    }
    static final class Slider extends SeekBar {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Slider(Context context,int accent){
            super(context);
            setMax(200);setProgress(100);
            setPadding(dp(context,12),0,dp(context,12),0);
            GradientDrawable thumb=new GradientDrawable();
            thumb.setColor(Color.TRANSPARENT);
            thumb.setSize(dp(context,18),dp(context,18));
            setThumb(thumb);setThumbOffset(dp(context,9));
            setSplitTrack(false);
            setMinimumHeight(dp(context,36));
        }
        @Override protected synchronized void onDraw(Canvas canvas){
            float left=getPaddingLeft(),right=getWidth()-getPaddingRight();
            float y=getHeight()*.5f;
            float x=left+(right-left)*getProgress()/Math.max(1,getMax());
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(getContext(),3));
            paint.setColor(0xff45464e);
            canvas.drawLine(left,y,right,y,paint);
            paint.setColor(0xffe5e5e9);
            canvas.drawLine(left,y,x,y,paint);
            paint.setColor(0xfff5f5f8);
            canvas.drawCircle(x,y,dp(getContext(),isPressed()?7:6),paint);
        }
    }
}

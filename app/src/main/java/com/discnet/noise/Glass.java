package com.discnet.noise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;

/** Restrained graphite glass styling inspired by the Noise! concept. */
final class Glass {
    static final int INK = 0xfff5f2ff, MUTED = 0xffb5b1c4;
    static final int LIME = 0xffd9c8ff, CYAN = 0xffd7bbff, VIOLET = 0xffa9a2ff;
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable panel(Context c, int first, int second, float radius, int border) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{first, second});
        d.setCornerRadius(dp(c, radius)); d.setStroke(dp(c, 1), border); return d;
    }
    static void button(View view, boolean primary) {
        Context c = view.getContext();
        GradientDrawable bg = primary ? panel(c, 0xffd0baff, 0xffbc9cff, 18, 0x77fff5ff)
            : panel(c, 0x9b252331, 0xb91c1b25, 18, 0x35ffffff);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff), bg, null));
        view.setMinimumHeight(dp(c, 44));
        view.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
    }
    static final class Backdrop extends FrameLayout {
        Backdrop(Context c) { super(c); setBackgroundColor(0xff0b0a10); }
    }
    static final class Slider extends SeekBar {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        Slider(Context c, int accent) {
            super(c); this.accent = accent; setMax(200); setProgress(100);
            setPadding(dp(c, 10), 0, dp(c, 10), 0);
            GradientDrawable thumb = new GradientDrawable();
            thumb.setColor(Color.TRANSPARENT); thumb.setSize(dp(c, 22), dp(c, 22));
            setThumb(thumb); setThumbOffset(dp(c, 11)); setSplitTrack(false); setMinimumHeight(dp(c, 36));
        }
        @Override protected synchronized void onDraw(Canvas canvas) {
            float l=getPaddingLeft(), r=getWidth()-getPaddingRight(), y=getHeight()*.5f;
            float x=l+(r-l)*getProgress()/getMax();
            paint.setShader(null); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(getContext(),4)); paint.setColor(0x445d596d); canvas.drawLine(l,y,r,y,paint);
            paint.setShader(new LinearGradient(l,y,r,y,0xffbbaaff,accent,Shader.TileMode.CLAMP));
            canvas.drawLine(l,y,x,y,paint); paint.setShader(null);
            paint.setColor(0xffe9e2ff); canvas.drawCircle(x,y,dp(getContext(),isPressed()?10:8),paint);
            paint.setColor(0x45ffffff); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(),1)); canvas.drawCircle(x,y,dp(getContext(),isPressed()?10:8),paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}

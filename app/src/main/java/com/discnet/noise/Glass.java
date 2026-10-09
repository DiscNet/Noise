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
    static final int INK = 0xfff7f2ff, MUTED = 0xffbdb5cb;
    static final int LIME = 0xffd9c8ff, CYAN = 0xffef83e7, VIOLET = 0xffb3a8ff;
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
    /** Backdrop: soft diffused orbs, never a distracting rainbow gradient. */
    static final class Backdrop extends FrameLayout {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        Backdrop(Context c) {
            super(c);
            setBackgroundColor(0xff09080f);
            setWillNotDraw(false);
        }
        private void orb(Canvas c,float x,float y,float radius,int color) {
            glow.setShader(new RadialGradient(x,y,radius,color,Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
            c.drawCircle(x,y,radius,glow);
            glow.setShader(null);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            orb(c,w*.86f,h*.19f,w*.76f,0x552d1c58);
            orb(c,w*.06f,h*.72f,w*.64f,0x403b125e);
            orb(c,w*.87f,h*.88f,w*.55f,0x393c2349);
        }
    }

    /** Translucent glass surface with layered specular stroke and soft tint.
     * Actual backdrop blur requires capturing SurfaceView separately, which is
     * intentionally avoided to keep the OpenGL preview frame rate stable. */
    static GradientDrawable frosted(Context c,float radius) {
        GradientDrawable result=panel(c,0xb638304a,0xc31a1928,radius,0x76d1b5fa);
        return result;
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
            paint.setShader(new LinearGradient(l,y,r,y,0xffc55af0,accent,Shader.TileMode.CLAMP));
            canvas.drawLine(l,y,x,y,paint); paint.setShader(null);
            paint.setColor(0xffe9e2ff); canvas.drawCircle(x,y,dp(getContext(),isPressed()?10:8),paint);
            paint.setColor(0x45ffffff); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(),1)); canvas.drawCircle(x,y,dp(getContext(),isPressed()?10:8),paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}

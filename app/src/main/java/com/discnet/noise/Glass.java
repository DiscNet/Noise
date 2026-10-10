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
        Drawable bg=liquid(c,18,primary);
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
    static Drawable frosted(Context c,float radius) {
        return liquid(c,radius,false);
    }
    /** iOS Liquid Glass-inspired custom material for Android Views:
      * semi-transparent chromatic gradient, refractive-looking highlights and
      * layered border illumination. No iOS APIs or screen scraping is used.
      * Performance is constant and it works back to Android 8.
      */
    static Drawable liquid(Context c,float radius,boolean primary) {
        return new LiquidDrawable(dp(c,radius),primary);
    }
    private static final class LiquidDrawable extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float r;
        private final boolean primary;
        LiquidDrawable(float r,boolean primary){this.r=r;this.primary=primary;}
        @Override public void draw(Canvas canvas){
            android.graphics.Rect bounds=getBounds();
            float l=bounds.left,t=bounds.top,w=bounds.width(),h=bounds.height();
            if(w<=0||h<=0)return;
            float rad=Math.min(r,Math.min(w,h)*.5f);
            android.graphics.RectF shape=new android.graphics.RectF(l,t,l+w,t+h);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(l,t,l+w,t+h,
                primary?new int[]{0xeeffe1ff,0xf6d2b1ff,0xffdca5fd}:
                    new int[]{0xb0564b75,0x9b292238,0xb7272039,0xa54c335b},
                null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(shape,rad,rad,paint);
            paint.setShader(null);
            canvas.save();
            android.graphics.Path clip=new android.graphics.Path();
            clip.addRoundRect(shape,rad,rad,android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            paint.setShader(new RadialGradient(l+w*.18f,t-h*.26f,
                Math.max(w,h)*1.05f,
                new int[]{0x84ffffff,0x27ff8fe5,0x00ffffff},
                new float[]{0f,.42f,1f},Shader.TileMode.CLAMP));
            canvas.drawRect(shape,paint);paint.setShader(null);
            paint.setShader(new LinearGradient(l,t,l,t+h,
                0x55ffffff,0x00ffffff,Shader.TileMode.CLAMP));
            canvas.drawRect(l,t,l+w,t+Math.max(1,h*.46f),paint);
            paint.setShader(null);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f,Math.min(w,h)*.018f));
            paint.setColor(primary?0xc6ffffff:0x8cfae9ff);
            canvas.drawRoundRect(new android.graphics.RectF(l+.8f,t+.8f,
                l+w-.8f,t+h-.8f),rad,rad,paint);
            paint.setStrokeWidth(1.15f);
            paint.setColor(0x78ffb7fc);
            canvas.drawRoundRect(new android.graphics.RectF(l+2.4f,t+2.4f,
                l+w-2.4f,t+h-2.4f),Math.max(0,rad-2),Math.max(0,rad-2),paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0x58ffffff);
            canvas.drawRoundRect(new android.graphics.RectF(l+w*.18f,t+1.7f,
                l+w*.83f,t+3.3f),2,2,paint);
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(android.graphics.ColorFilter cf){paint.setColorFilter(cf);invalidateSelf();}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
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

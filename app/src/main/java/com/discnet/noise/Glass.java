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
    static final int INK = 0xfff5f5f8, MUTED = 0xffa6a8b3;
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
    /** Quiet studio background: image content stays visually dominant. */
    static final class Backdrop extends FrameLayout {
        private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);
        Backdrop(Context context){
            super(context);
            setBackgroundColor(0xff090a0f);
            setWillNotDraw(false);
        }
        private void haze(Canvas canvas,float x,float y,float radius,int color){
            glow.setStyle(Paint.Style.FILL);
            glow.setShader(new RadialGradient(x,y,radius,color,0x00000000,
                Shader.TileMode.CLAMP));
            canvas.drawCircle(x,y,radius,glow);
            glow.setShader(null);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            haze(canvas,w*.88f,h*.08f,w*.85f,0x293a3852);
            haze(canvas,w*.06f,h*.88f,w*.75f,0x1b414457);
        }
    }

    /** Translucent glass surface with layered specular stroke and soft tint.
     * Actual backdrop blur requires capturing SurfaceView separately, which is
     * intentionally avoided to keep the OpenGL preview frame rate stable. */
    static Drawable frosted(Context c,float radius) {
        return liquid(c,radius,false);
    }
    /** Deliberately restrained translucent material for tools and navigation. */
    static Drawable liquid(Context c,float radius,boolean primary) {
        return new LiquidDrawable(dp(c,radius),primary);
    }
    private static final class LiquidDrawable extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;
        private final boolean primary;
        LiquidDrawable(float radius,boolean primary){
            this.radius=radius;this.primary=primary;
        }
        @Override public void draw(Canvas canvas){
            android.graphics.Rect b=getBounds();
            float l=b.left,t=b.top,w=b.width(),h=b.height();
            if(w<=0||h<=0)return;
            float r=Math.min(radius,Math.min(w,h)*.5f);
            RectF rect=new RectF(l,t,l+w,t+h);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(l,t,l+w,t+h,
                primary?new int[]{0xfff6f3ff,0xffcfd5e6}:
                    new int[]{0xdc292a35,0xe51c1d27},
                null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect,r,r,p);
            p.setShader(null);
            p.setColor(primary?0x40ffffff:0x16ffffff);
            canvas.drawRoundRect(new RectF(l+1,t+1,l+w-1,t+h*.30f),r,r,p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f,w*.002f));
            p.setColor(primary?0x80ffffff:0x4cbdc0d2);
            canvas.drawRoundRect(new RectF(l+1,t+1,l+w-1,t+h-1),r,r,p);
            p.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(android.graphics.ColorFilter cf){
            p.setColorFilter(cf);invalidateSelf();
        }
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
            paint.setStrokeWidth(dp(getContext(),4)); paint.setColor(0x594a4e5a); canvas.drawLine(l,y,r,y,paint);
            paint.setShader(new LinearGradient(l,y,r,y,0xff9e9eb8,accent,Shader.TileMode.CLAMP));
            canvas.drawLine(l,y,x,y,paint); paint.setShader(null);
            paint.setColor(0xfff5f5fa); canvas.drawCircle(x,y,dp(getContext(),isPressed()?10:8),paint);
            paint.setColor(0x45ffffff); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(),1)); canvas.drawCircle(x,y,dp(getContext(),isPressed()?10:8),paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}

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
    /** Satin-gradient background inspired by the motion and chromatic ribbons
      * of the user's final artwork. Vector paths, no baked reference image. */
    static final class Backdrop extends FrameLayout {
        private final Paint light=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path sweep=new Path();
        Backdrop(Context context){
            super(context);setBackgroundColor(0xff07050c);setWillNotDraw(false);
        }
        private void orb(Canvas canvas,float cx,float cy,float radius,int color){
            light.setStyle(Paint.Style.FILL);
            light.setShader(new RadialGradient(cx,cy,radius,color,0x00000000,
                Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,radius,light);light.setShader(null);
        }
        private void ribbon(Canvas canvas,float x0,float y0,float x1,float y1,
                            float x2,float y2,float x3,float y3,float thick,
                            float w,float h){
            sweep.reset();sweep.moveTo(x0*w,y0*h);
            sweep.cubicTo(x1*w,y1*h,x2*w,y2*h,x3*w,y3*h);
            light.setStyle(Paint.Style.STROKE);
            light.setStrokeCap(Paint.Cap.ROUND);light.setStrokeJoin(Paint.Join.ROUND);
            // Diffuse purple fringe wraps a warm pink-orange inner reflection.
            light.setStrokeWidth(thick*2.6f*w);
            light.setShader(new LinearGradient(0,0,w,h,
                new int[]{0x00262484,0x72352291,0x4cdd48b4,0x001c0c32},
                new float[]{0f,.33f,.79f,1f},Shader.TileMode.CLAMP));
            canvas.drawPath(sweep,light);
            light.setStrokeWidth(thick*.92f*w);
            light.setShader(new LinearGradient(0,h*.1f,w,h*.9f,
                new int[]{0x00ffa34d,0xa3ffad83,0x98c44daf,0x06ffc2a4},
                new float[]{0f,.28f,.73f,1f},Shader.TileMode.CLAMP));
            canvas.drawPath(sweep,light);
            light.setStrokeWidth(Math.max(1f,thick*.15f*w));
            light.setShader(new LinearGradient(0,h*.16f,w,h*.88f,
                0xb6ffcb96,0x96e65add,Shader.TileMode.CLAMP));
            canvas.drawPath(sweep,light);
            light.setShader(null);light.setStyle(Paint.Style.FILL);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            orb(canvas,w*.12f,-h*.13f,w*.8f,0x624b286e);
            orb(canvas,w*.94f,h*.39f,w*.66f,0x51301f6a);
            orb(canvas,w*.12f,h*.90f,w*.79f,0x54371a75);
            ribbon(canvas,-.50f,.17f,.25f,.04f,.70f,.55f,1.35f,.12f,.025f,w,h);
            ribbon(canvas,-.24f,.83f,.58f,.49f,.36f,.99f,1.23f,.73f,.032f,w,h);
            ribbon(canvas,-.27f,.99f,.43f,.59f,.67f,1.31f,1.25f,1.07f,.020f,w,h);
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
            // Fine diagonal metal-mesh highlights on the frosted material.
            paint.setStrokeWidth(1f);
            paint.setColor(0x13ffc5ee);
            float step=Math.max(12f,Math.min(w,h)*.22f);
            for(float xx=-h;xx<w+h;xx+=step) {
                canvas.drawLine(l+xx,t,l+xx+h,t+h,paint);
            }
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

package com.discnet.noise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Monochrome, organic ink backdrop. The soft speckled edges are rendered
 * procedurally into a cached bitmap whenever the viewport changes, while the
 * main Canvas combines it with an off-white photographic paper gradient.
 *
 * No textured-glass sampling, backdrop blur, or UI filtering is involved.
 * The editor's photograph is never modified by this decorative background.
 */
class OrganicBackdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect inkSource=new Rect(),inkDestination=new Rect();
    private Bitmap inkLayer;
    private LinearGradient baseGradient;
    private ValueAnimator animator;
    private float phase;
    OrganicBackdrop(Context c){super(c);setClickable(false);setFocusable(false);}
    float phaseValue(){return phase;}

    private static float smooth(float start,float end,float v){
        float t=Math.max(0f,Math.min(1f,(v-start)/(end-start)));
        return t*t*(3f-2f*t);
    }
    private static int hash(int x,int y){
        int z=x*374761393+y*668265263;
        z=(z^(z>>>13))*1274126177;
        return z^(z>>>16);
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    private static float ribbon(float u,float center,float width,
                                float edge,float irregular){
        float d=Math.abs(u-center)+irregular;
        return 1f-smooth(width-edge,width+edge,d);
    }

    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        if(width<1||height<1)return;
        baseGradient=new LinearGradient(0f,0f,width,height,
            new int[]{0xfff7f5f2,0xffedeae7,0xffe2e0df,0xfff0eded},
            new float[]{0f,.38f,.71f,1f},Shader.TileMode.CLAMP);
        buildInkLayer(width,height);
        invalidate();
    }

    /**
     * Large asymmetric black ribbons, high-contrast white negative space,
     * and a narrow physically granulated matte edge like ink on paper.
     */
    private void buildInkLayer(int width,int height){
        int w=Math.max(2,Math.min(520,width));
        int h=Math.max(2,Math.min(1100,height));
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++){
            float v=(y+.5f)/h;
            float leftCenter=-.18f+.39f*(float)Math.sin(v*4.7f+.44f)+.12f*v;
            float rightCenter=1.14f-.40f*(float)Math.sin(v*4.10f+.29f)-.10f*v;
            float waveCenter=.49f+.24f*(float)Math.sin(v*5.25f+1.65f);
            float leftGate=smooth(.15f,.28f,v)*(1f-smooth(.85f,1f,v));
            float rightGate=smooth(.12f,.24f,v)*(1f-smooth(.86f,1f,v));
            float middleGate=smooth(.34f,.43f,v)*(1f-smooth(.79f,.94f,v));
            for(int x=0;x<w;x++){
                float u=(x+.5f)/w;
                // Two scales of stable edge irregularity: low-frequency
                // silhouette modulation and fine stochastic stippling.
                int seed=hash(x,y);
                float grain=((seed&65535)/65535f)-.5f;
                float contour=(float)Math.sin(u*52f+v*31f)
                    *(float)Math.sin(u*43f-v*27f)*.008f;
                float jitter=grain*.019f+contour;
                float a=ribbon(u,leftCenter,.153f,.041f,jitter)*leftGate;
                float b=ribbon(u,rightCenter,.140f,.044f,-jitter)*rightGate;
                float c=ribbon(u,waveCenter,.090f,.058f,jitter*.84f)*middleGate;
                float ink=1f-(1f-a)*(1f-b)*(1f-c);
                float dust=(float)((hash(x+41,y+103)&65535)/65535.0);
                // Tiny pores are concentrated along feathered outlines.
                float boundary=4f*ink*(1f-ink);
                ink=Math.max(0f,Math.min(1f,ink+(dust-.5f)*boundary*.37f));
                int alpha=clamp(Math.round(247f*ink));
                int shadow=clamp(Math.round(19f+11f*grain));
                pixels[y*w+x]=Color.argb(alpha,shadow,shadow,shadow+2);
            }
        }
        Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        next.setPixels(pixels,0,w,0,0,w,h);
        if(inkLayer!=null&&!inkLayer.isRecycled())inkLayer.recycle();
        inkLayer=next;
        inkSource.set(0,0,w,h);
        inkDestination.set(0,0,width,height);
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(baseGradient==null)return;
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        paint.setShader(baseGradient);
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setShader(null);
        if(inkLayer!=null&&!inkLayer.isRecycled()){
            // Minimal horizontal drift keeps the atmosphere alive without
            // moving the silhouette enough to distract from editing actions.
            float drift=(float)Math.sin(phase*2f*Math.PI)*getWidth()*.003f;
            int saved=canvas.save();
            canvas.translate(drift,0f);
            canvas.drawBitmap(inkLayer,inkSource,inkDestination,paint);
            canvas.restoreToCount(saved);
        }
    }
    @Override public void onVisibilityAggregated(boolean visible){
        super.onVisibilityAggregated(visible);
        if(visible)resume();else pause();
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();if(isShown())resume();
    }
    @Override protected void onDetachedFromWindow(){
        pause();if(animator!=null){animator.cancel();animator=null;}
        if(inkLayer!=null){inkLayer.recycle();inkLayer=null;}
        super.onDetachedFromWindow();
    }
    private void resume(){
        if(!ValueAnimator.areAnimatorsEnabled()||!isShown())return;
        if(animator==null){
            animator=ValueAnimator.ofFloat(0f,1f);
            animator.setDuration(24000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a->{
                phase=(Float)a.getAnimatedValue();
                postInvalidateOnAnimation();
            });
        }
        if(!animator.isStarted())animator.start();
        else if(animator.isPaused())animator.resume();
    }
    private void pause(){if(animator!=null&&animator.isStarted())animator.pause();}
}

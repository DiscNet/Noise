package com.discnet.noise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * The Home's graphic foundation. One authored asymmetric ink composition
 * supports the lilac photographic light and the sampled glass surfaces.
 * Nothing here touches the editor bitmap or GPU export pipeline.
 */
class OrganicBackdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix=new Matrix();
    private Bitmap inkLayer;
    private final Rect inkBounds=new Rect();
    private final Rect inkDestination=new Rect();
    private long lastNotifiedAt;
    private final ArrayList<WeakReference<TexturedGlass>> materials=new ArrayList<>();
    private LinearGradient baseGradient;
    private RadialGradient[] atmosphere;
    private ValueAnimator animator;
    private float phase;
    private Bitmap snapshot;
    private long snapshotAt;
    private int snapshotVersion;
    OrganicBackdrop(Context context){super(context);setClickable(false);setFocusable(false);}
    float phaseValue(){return phase;}
    int materialVersion(){return snapshotVersion;}
    void subscribe(TexturedGlass material){materials.add(new WeakReference<>(material));}
    private void notifyMaterials(){
        for(int i=materials.size()-1;i>=0;i--){
            TexturedGlass g=materials.get(i).get();
            if(g==null)materials.remove(i);
            else g.invalidateSelf();
        }
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);
        if(w<1||h<1)return;
        baseGradient=new LinearGradient(0,0,w,h,
            new int[]{0xff0b0e15,0xff17202e,0xff130f1a,0xff080a10},
            new float[]{0f,.30f,.66f,1f},Shader.TileMode.CLAMP);
        atmosphere=new RadialGradient[]{
            new RadialGradient(0,0,1,new int[]{0x7e496aa0,0x315b4887,0x005b4887},
                new float[]{0f,.44f,1f},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,new int[]{0x83884d88,0x33744b85,0x00744b85},
                new float[]{0f,.40f,1f},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,new int[]{0x628b9ea6,0x2261798c,0x0061798c},
                new float[]{0f,.45f,1f},Shader.TileMode.CLAMP)
        };
        // Compute softened ink fields once per size, at display-independent
        // resolution. This replaces the former flat, hard-edged paths.
        buildInkLayer(w,h);
        if(snapshot!=null){snapshot.recycle();snapshot=null;}
        snapshotAt=0;invalidate();notifyMaterials();
    }
    private static float smooth(float a,float b,float x){
        float t=Math.max(0f,Math.min(1f,(x-a)/(b-a)));
        return t*t*(3f-2f*t);
    }
    private static int clamp(int n){return Math.max(0,Math.min(255,n));}
    /**
     * Three authored ribbon fields with soft, irregular edges. The ink
     * deliberately never becomes a solid flat polygon; neighboring atmospheric
     * light remains visible through the ribbons.
     */
    private void buildInkLayer(int width,int height){
        int w=Math.max(1,Math.min(420,(width+1)/2));
        int h=Math.max(1,Math.min(780,(height+1)/2));
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++){
            float v=(y+.5f)/h;
            float c1=.025f+.20f*(float)Math.sin(v*4.8f+.42f);
            float c2=1.09f-.22f*(float)Math.sin(v*4.1f+1.25f);
            float c3=.48f+.25f*(float)Math.sin(v*5.6f+.7f);
            for(int x=0;x<w;x++){
                float u=(x+.5f)/w;
                float fine=(float)(Math.sin((x*1.57+y*2.69)*.39)
                    *Math.sin((x*2.13-y*.71)*.51));
                float irregular=.013f*fine;
                float left=1f-smooth(.15f,.30f,Math.abs(u-c1)+irregular);
                float right=1f-smooth(.15f,.29f,Math.abs(u-c2)-irregular);
                float drift=1f-smooth(.07f,.22f,Math.abs(u-c3)+irregular);
                float gate=smooth(.12f,.31f,v)*(1f-smooth(.75f,.97f,v));
                float shadow=Math.min(.65f,left*.26f+right*.42f+drift*gate*.17f);
                float luminous=(1f-smooth(.05f,.22f,Math.abs(u-(.31f+
                    .22f*(float)Math.sin(v*3.1f)))))*gate*.13f;
                float mixed=Math.max(0f,shadow+luminous);
                int alpha=clamp(Math.round(mixed*255f));
                int red=clamp(Math.round(14f+luminous*300f));
                int green=clamp(Math.round(14f+luminous*230f));
                int blue=clamp(Math.round(22f+luminous*375f));
                pixels[y*w+x]=Color.argb(alpha,red,green,blue);
            }
        }
        Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        next.setPixels(pixels,0,w,0,0,w,h);
        if(inkLayer!=null)inkLayer.recycle();
        inkLayer=next;
        inkBounds.set(0,0,w,h);
        inkDestination.set(0,0,width,height);
    }
    private void scene(Canvas canvas,float w,float h){
        if(baseGradient==null)return;
        paint.setColor(Color.WHITE);
        paint.setShader(baseGradient);
        canvas.drawRect(0,0,w,h,paint);
        paint.setShader(null);
        // The atmospheric gradient stays present behind the new ink forms.
        for(int i=0;i<atmosphere.length;i++){
            float t=phase*6.2831853f;
            float x=w*(i==0?.18f:i==1?.88f:.44f)
                +w*.020f*(float)Math.sin(t+i*2.2f);
            float y=h*(i==0?.24f:i==1?.66f:.87f)
                +h*.021f*(float)Math.cos(t*.66f+i);
            float rw=w*(i==1?.72f:.56f),rh=h*(i==0?.30f:.39f);
            matrix.reset();matrix.setScale(rw,rh);matrix.postTranslate(x,y);
            atmosphere[i].setLocalMatrix(matrix);
            paint.setColor(Color.WHITE);
            paint.setShader(atmosphere[i]);
            canvas.drawOval(x-rw,y-rh,x+rw,y+rh,paint);
        }
        paint.setShader(null);
        if(inkLayer!=null&&!inkLayer.isRecycled()){
            paint.setColor(Color.WHITE);
            paint.setAlpha(226);
            canvas.drawBitmap(inkLayer,inkBounds,inkDestination,paint);
            paint.setAlpha(255);
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(getWidth()>0&&getHeight()>0)scene(canvas,getWidth(),getHeight());
    }
    /**
     * A downsampled, actually blurred copy of the artwork behind glass.
     * Updated at <=1.2 Hz; the animated full-resolution backdrop stays on GPU.
     */
    Bitmap blurredSnapshot(){
        if(getWidth()<1||getHeight()<1)return null;
        long now=SystemClock.uptimeMillis();
        if(snapshot!=null&&now-snapshotAt<2800)return snapshot;
        // Four-times reduction keeps detail behind glass without reproducing
        // the old 8x-upscaled cloudy blocks.
        int w=Math.max(1,(getWidth()+3)/4),h=Math.max(1,(getHeight()+3)/4);
        Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas offscreen=new Canvas(next);
        offscreen.scale(w/(float)getWidth(),h/(float)getHeight());
        scene(offscreen,getWidth(),getHeight());
        // 2-pass 3x3 tent blur on low-resolution samples, with clamped edges.
        int[] pixels=new int[w*h],tmp=new int[w*h];
        next.getPixels(pixels,0,w,0,0,w,h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int rr=0,gg=0,bb=0;
            for(int k=-2;k<=2;k++){
                int px=pixels[y*w+Math.max(0,Math.min(w-1,x+k))];
                int weight=3-Math.abs(k);
                rr+=Color.red(px)*weight;gg+=Color.green(px)*weight;bb+=Color.blue(px)*weight;
            }
            tmp[y*w+x]=Color.rgb(rr/9,gg/9,bb/9);
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int rr=0,gg=0,bb=0;
            for(int k=-2;k<=2;k++){
                int px=tmp[Math.max(0,Math.min(h-1,y+k))*w+x];
                int weight=3-Math.abs(k);
                rr+=Color.red(px)*weight;gg+=Color.green(px)*weight;bb+=Color.blue(px)*weight;
            }
            pixels[y*w+x]=Color.rgb(rr/9,gg/9,bb/9);
        }
        next.setPixels(pixels,0,w,0,0,w,h);
        if(snapshot!=null)snapshot.recycle();
        snapshot=next;snapshotAt=now;snapshotVersion++;
        return snapshot;
    }
    @Override public void onVisibilityAggregated(boolean visible){
        super.onVisibilityAggregated(visible);
        if(visible)resume();else pause();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(isShown())resume();}
    @Override protected void onDetachedFromWindow(){
        pause();
        if(animator!=null){animator.cancel();animator=null;}
        if(snapshot!=null){snapshot.recycle();snapshot=null;}
        if(inkLayer!=null){inkLayer.recycle();inkLayer=null;}
        super.onDetachedFromWindow();
    }
    private void resume(){
        if(!ValueAnimator.areAnimatorsEnabled()||!isShown())return;
        if(animator==null){
            animator=ValueAnimator.ofFloat(0f,1f);
            animator.setDuration(21000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a->{
                phase=(Float)a.getAnimatedValue();
                postInvalidateOnAnimation();
                long now=SystemClock.uptimeMillis();
                if(now-lastNotifiedAt>2900){
                    lastNotifiedAt=now;
                    notifyMaterials();
                }
            });
        }
        if(!animator.isStarted())animator.start();
        else if(animator.isPaused())animator.resume();
    }
    private void pause(){if(animator!=null&&animator.isStarted())animator.pause();}
}
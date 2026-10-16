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
    private final Path inkLeft=new Path(),inkRight=new Path(),paperLight=new Path();
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
        // Deliberate, asymmetric negative-space silhouettes, not random blobs.
        inkLeft.reset();
        inkLeft.moveTo(-w*.18f,h*.07f);
        inkLeft.cubicTo(w*.37f,-h*.07f,w*.23f,h*.22f,w*.12f,h*.34f);
        inkLeft.cubicTo(w*.02f,h*.52f,w*.22f,h*.58f,w*.05f,h*.76f);
        inkLeft.cubicTo(-w*.09f,h*.87f,-w*.25f,h*.57f,-w*.18f,h*.07f);
        inkLeft.close();
        inkRight.reset();
        inkRight.moveTo(w*1.12f,h*.04f);
        inkRight.cubicTo(w*.76f,h*.03f,w*.86f,h*.21f,w*.66f,h*.30f);
        inkRight.cubicTo(w*.50f,h*.43f,w*.83f,h*.48f,w*.90f,h*.63f);
        inkRight.cubicTo(w*1.06f,h*.84f,w*1.13f,h*.92f,w*1.12f,h*.04f);
        inkRight.close();
        paperLight.reset();
        paperLight.moveTo(w*.12f,h*.12f);
        paperLight.cubicTo(w*.21f,h*.02f,w*.45f,h*.10f,w*.48f,h*.18f);
        paperLight.cubicTo(w*.55f,h*.34f,w*.19f,h*.38f,w*.14f,h*.29f);
        paperLight.cubicTo(w*.09f,h*.23f,w*.06f,h*.18f,w*.12f,h*.12f);
        paperLight.close();
        if(snapshot!=null){snapshot.recycle();snapshot=null;}
        snapshotAt=0;invalidate();notifyMaterials();
    }
    private void scene(Canvas canvas,float w,float h){
        if(baseGradient==null)return;
        paint.setColor(Color.WHITE);
        paint.setShader(baseGradient);
        canvas.drawRect(0,0,w,h,paint);
        paint.setShader(null);
        // Off-white is used sparingly as a scattering source, not an opaque wallpaper.
        paint.setColor(0x243a4051);canvas.drawPath(inkLeft,paint);
        paint.setColor(0xb70a0b12);canvas.drawPath(inkRight,paint);
        paint.setColor(0x1239b2ca);canvas.drawPath(paperLight,paint);
        for(int i=0;i<atmosphere.length;i++){
            float t=phase*6.2831853f;
            float x=w*(i==0?.18f:i==1?.88f:.44f)+w*.028f*(float)Math.sin(t+i*2.2f);
            float y=h*(i==0?.24f:i==1?.66f:.87f)+h*.025f*(float)Math.cos(t*.66f+i);
            float rw=w*(i==1?.72f:.56f),rh=h*(i==0?.30f:.39f);
            matrix.reset();matrix.setScale(rw,rh);matrix.postTranslate(x,y);
            atmosphere[i].setLocalMatrix(matrix);
            paint.setColor(Color.WHITE);
            paint.setShader(atmosphere[i]);
            canvas.drawOval(x-rw,y-rh,x+rw,y+rh,paint);
        }
        paint.setShader(null);
        // A few soft ink intersections reinforce the shared material direction.
        paint.setColor(0x2d0a0912);
        canvas.drawPath(inkRight,paint);
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
        if(snapshot!=null&&now-snapshotAt<840)return snapshot;
        int w=Math.max(1,(getWidth()+7)/8),h=Math.max(1,(getHeight()+7)/8);
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
                if(SystemClock.uptimeMillis()-snapshotAt>900)notifyMaterials();
            });
        }
        if(!animator.isStarted())animator.start();
        else if(animator.isPaused())animator.resume();
    }
    private void pause(){if(animator!=null&&animator.isStarted())animator.pause();}
}
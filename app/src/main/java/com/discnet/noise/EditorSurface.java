package com.discnet.noise;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.view.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Latest snapshot wins, no queued bitmap work on the slider path. */
public final class EditorSurface extends GLSurfaceView implements GLSurfaceView.Renderer {
    public interface Listener{void ready(int maxTexture);void failed(String message);}
    interface Gestures{void compare(boolean pressed);void fullscreen();}
    private volatile EditState state=EditState.NEUTRAL;
    private volatile Bitmap source;
    private Bitmap uploaded;
    private GlPipeline pipeline;
    private final Listener listener;
    private Gestures gestures;
    private int width=1,height=1;
    private volatile int sourceWidth,sourceHeight;
    volatile long frameCount,renderNanos;
    private volatile float zoom=1,panX,panY;
    private final ScaleGestureDetector scale;
    private final GestureDetector taps;
    private boolean holding,scaling;
    public EditorSurface(Context c,Listener listener){
        super(c);this.listener=listener;setEGLContextClientVersion(2);setEGLConfigChooser(8,8,8,8,0,0);
        setPreserveEGLContextOnPause(true);setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);
        setContentDescription("Prévia. Pinça para ampliar; arraste para mover; segure para ver o original; dois toques para ajustar o zoom.");
        scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){scaling=true;releaseCompare();return true;}
            @Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(1,Math.min(8,zoom*d.getScaleFactor()));if(zoom==1)panX=panY=0;requestRender();return true;}
        });
        taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public void onLongPress(MotionEvent e){if(!scaling&&source!=null){holding=true;if(gestures!=null)gestures.compare(true);}}
            @Override public boolean onScroll(MotionEvent e,MotionEvent n,float dx,float dy){releaseCompare();if(!scaling&&zoom>1){panX-=dx*2/width;panY+=dy*2/height;float max=zoom-1;panX=Math.max(-max,Math.min(max,panX));panY=Math.max(-max,Math.min(max,panY));requestRender();}return true;}
            @Override public boolean onDoubleTap(MotionEvent e){zoom=zoom>1?1:2;panX=panY=0;requestRender();return true;}
        });
    }
    void gestures(Gestures g){gestures=g;}
    void resetViewport(){zoom=1;panX=panY=0;requestRender();}
    private void releaseCompare(){if(holding){holding=false;if(gestures!=null)gestures.compare(false);}}
    @Override public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);taps.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){releaseCompare();scaling=false;performClick();}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    public void setImage(Bitmap b){setImage(b,b.getWidth(),b.getHeight());}
    void setImage(Bitmap b,int w,int h){sourceWidth=w;sourceHeight=h;source=b;resetViewport();}
    public void setEditState(EditState s){state=s;requestRender();}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){uploaded=null;
        try{pipeline=new GlPipeline(getContext());int max=GlPipeline.maximumTextureSize();post(()->listener.ready(max));}
        catch(Exception e){android.util.Log.e("NoiseGL","Initialize",e);pipeline=null;post(()->listener.failed("A GPU não conseguiu iniciar o editor."));}}
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;}
    @Override public void onDrawFrame(GL10 gl){if(pipeline==null)return;long start=System.nanoTime();
        try{Bitmap next=source;if(next!=null&&next!=uploaded){pipeline.upload(next);uploaded=next;}
            pipeline.sourceDimensions(sourceWidth,sourceHeight);pipeline.draw(width,height,state,false,zoom,panX,panY);frameCount++;renderNanos=System.nanoTime()-start;}
        catch(Exception|OutOfMemoryError e){android.util.Log.e("NoiseGL","Draw",e);pipeline.release();pipeline=null;post(()->listener.failed("Falha na prévia. Tente reabrir uma imagem menor."));}}
    void dispose(){source=null;queueEvent(()->{if(pipeline!=null){pipeline.release();pipeline=null;}uploaded=null;});}
}

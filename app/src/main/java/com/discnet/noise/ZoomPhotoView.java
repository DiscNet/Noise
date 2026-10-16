package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;

/** Photo gestures: pan/pinch when zoomed; switch images only at fit scale. */
final class ZoomPhotoView extends ImageView {
    interface Gestures { void controls(); void page(int delta); }
    private final Matrix transform=new Matrix();
    private final ScaleGestureDetector pinch;
    private final GestureDetector taps;
    private final Gestures callback;
    private float fitScale=1f,zoom=1f,downX,downY,lastX,lastY;
    private boolean drag,scaled;
    ZoomPhotoView(Context c,Gestures actions){
        super(c);callback=actions;
        setScaleType(ScaleType.MATRIX);
        setBackgroundColor(0xff080808);
        pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){scaled=true;return true;}
            @Override public boolean onScale(ScaleGestureDetector d){
                zoom=Math.max(1f,Math.min(5f,zoom*d.getScaleFactor()));
                updateMatrix();
                return true;
            }
        });
        taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){
                callback.controls();return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e){
                zoom=zoom>1.04f?1f:2.6f;
                updateMatrix();return true;
            }
        });
    }
    @Override public void setImageBitmap(Bitmap bitmap){
        super.setImageBitmap(bitmap);
        zoom=1f;drag=false;
        updateMatrix();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);updateMatrix();
    }
    private void updateMatrix(){
        if(getDrawable()==null||getWidth()==0||getHeight()==0)return;
        float w=getDrawable().getIntrinsicWidth(),h=getDrawable().getIntrinsicHeight();
        if(w<=0||h<=0)return;
        fitScale=Math.min(getWidth()/w,getHeight()/h);
        float total=fitScale*zoom;
        if(zoom<=1.001f){lastX=lastY=0f;}
        float maxX=Math.max(0f,(w*total-getWidth())*.5f);
        float maxY=Math.max(0f,(h*total-getHeight())*.5f);
        lastX=Math.max(-maxX,Math.min(maxX,lastX));
        lastY=Math.max(-maxY,Math.min(maxY,lastY));
        transform.reset();transform.postScale(total,total);
        transform.postTranslate((getWidth()-w*total)*.5f+lastX,
            (getHeight()-h*total)*.5f+lastY);
        setImageMatrix(transform);
    }
    boolean zoomed(){return zoom>1.02f;}
    @Override public boolean onTouchEvent(MotionEvent e){
        pinch.onTouchEvent(e);taps.onTouchEvent(e);
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            downX=lastTouchX=e.getX();downY=lastTouchY=e.getY();
            drag=false;scaled=false;return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(pinch.isInProgress()||e.getPointerCount()>1){scaled=true;return true;}
            float dx=e.getX()-lastTouchX,dy=e.getY()-lastTouchY;
            lastTouchX=e.getX();lastTouchY=e.getY();
            if(zoomed()){
                lastX+=dx;lastY+=dy;updateMatrix();drag=true;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(!scaled&&!zoomed()){
                float dx=e.getX()-downX,dy=e.getY()-downY;
                if(Math.abs(dx)>Math.max(dp(58),getWidth()*.14f)
                   &&Math.abs(dx)>Math.abs(dy)*1.35f){
                    callback.page(dx<0?1:-1);
                    return true;
                }
            }
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL)return true;
        return true;
    }
    private float lastTouchX,lastTouchY;
    private int dp(float value){return EditorTheme.dp(getContext(),value);}
}

package com.discnet.noise;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

/**
 * Opens the orbital tools with a deliberate leftward swipe over the unzoomed
 * image preview. Touch input remains with the preview until horizontal intent
 * is established, so pinching, panning, crop and sliders are unaffected.
 */
final class SwipeNavigationHost extends FrameLayout {
    interface Handler {
        boolean allow(float x,float y);
        void openTools();
    }
    private final Handler callback;
    private final int slop;
    private float startX,startY;
    private boolean eligible,tracking;
    SwipeNavigationHost(Context context,Handler callback){
        super(context);
        this.callback=callback;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                startX=event.getX();startY=event.getY();
                eligible=event.getPointerCount()==1&&callback.allow(startX,startY);
                tracking=false;return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                eligible=false;return false;
            case MotionEvent.ACTION_MOVE:
                if(!eligible||event.getPointerCount()!=1)return false;
                float dx=event.getX()-startX,dy=event.getY()-startY;
                if(dx< -Math.max(slop*2,EditorTheme.dp(getContext(),26))
                    &&Math.abs(dx)>Math.abs(dy)*1.45f){
                    tracking=true;return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:eligible=false;return false;
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!tracking)return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            if(event.getX()-startX < -EditorTheme.dp(getContext(),68)
                &&Math.abs(event.getY()-startY)<Math.abs(event.getX()-startX)*.85f){
                callback.openTools();
            }
            tracking=false;eligible=false;return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            tracking=false;eligible=false;return true;
        }
        return true;
    }
}

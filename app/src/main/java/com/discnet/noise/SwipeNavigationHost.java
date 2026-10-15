package com.discnet.noise;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

/**
 * Swipe-first navigation surface. Tracks the gesture at the editor root BEFORE
 * passing it to the OpenGL view, then cancels that child gesture only when
 * horizontal intent is established. The wheel approaches gesture targets with
 * damped movement rather than mirroring the finger without delay.
 * Horizontal actions never steal two-finger zoom or ordinary vertical drags.
 */
final class SwipeNavigationHost extends FrameLayout {
    interface Handler {
        boolean allow(float x,float y);
        void start(boolean effects);
        void finish(boolean open);
    }

    private final Handler callback;
    private final float touchSlop;
    private final float activationDistance;
    private float downX,downY;
    private boolean eligible,tracking,openingEffects;
    private int edgeWidth;
    private VelocityTracker velocity;

    SwipeNavigationHost(Context context,Handler callback) {
        super(context);
        this.callback=callback;
        setClickable(true); // Keep DOWN sequences even over empty editor margins.
        this.touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        this.activationDistance=Math.max(touchSlop*1.25f,EditorTheme.dp(context,13));
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        final int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            reset();
            downX=event.getX();downY=event.getY();
            // Only touches initiated within a real side-edge activation band
            // can open either wheel. Gestures from the middle must never open.
            edgeWidth=Math.round(Math.min(EditorTheme.dp(getContext(),72),
                Math.max(EditorTheme.dp(getContext(),48),getWidth()*.16f)));
            boolean fromLeft=downX<=getPaddingLeft()+edgeWidth;
            boolean fromRight=downX>=getWidth()-getPaddingRight()-edgeWidth;
            eligible=(fromLeft||fromRight)&&callback.allow(downX,downY);
            openingEffects=fromLeft;
            if(eligible){velocity=VelocityTracker.obtain();velocity.addMovement(event);}
            return super.dispatchTouchEvent(event);
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN) {
            if(tracking){callback.finish(false);tracking=false;}
            eligible=false;
            return super.dispatchTouchEvent(event);
        }
        if(velocity!=null)velocity.addMovement(event);

        if(action==MotionEvent.ACTION_MOVE && eligible && event.getPointerCount()==1) {
            float dx=event.getX()-downX,dy=event.getY()-downY;
            boolean movingInward=openingEffects?dx>activationDistance:dx< -activationDistance;
            if(!tracking && movingInward && Math.abs(dx)>Math.abs(dy)*1.20f){
                tracking=true;
                // The original child must not receive ACTION_UP: cancel its touch.
                MotionEvent cancel=MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                callback.start(openingEffects);
            }
            if(tracking) {
                // The gesture only decides which wheel to open. The opening
                // animation runs independently and never tracks the pointer.
                return true;
            }
        }
        if(tracking) {
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                float dx=event.getX()-downX;
                final float distance=Math.abs(dx);
                float signedSpeed=0;
                if(velocity!=null){
                    velocity.computeCurrentVelocity(1000);
                    signedSpeed=velocity.getXVelocity()*(openingEffects?1f:-1f);
                }
                boolean correctDirection=openingEffects?dx>0:dx<0;
                boolean open=action==MotionEvent.ACTION_UP && correctDirection
                    && distance>Math.min(EditorTheme.dp(getContext(),75),getWidth()*.22f)
                    && Math.abs(event.getY()-downY)<distance*1.3f;
                if(action==MotionEvent.ACTION_UP && correctDirection
                    && distance>EditorTheme.dp(getContext(),34)
                    && signedSpeed>EditorTheme.dp(getContext(),410))open=true;
                callback.finish(open);
                reset();
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
            reset();
        }
        return super.dispatchTouchEvent(event);
    }

    private void reset(){
        eligible=false;tracking=false;openingEffects=false;
        if(velocity!=null){velocity.recycle();velocity=null;}
    }
}

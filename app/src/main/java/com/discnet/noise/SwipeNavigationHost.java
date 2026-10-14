package com.discnet.noise;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

/**
 * Swipe-first navigation surface. Tracks the gesture at the editor root BEFORE
 * passing it to the OpenGL view, then cancels that child gesture only when
 * horizontal intent is established. The new wheel follows the finger directly.
 * Horizontal actions never steal two-finger zoom or ordinary vertical drags.
 */
final class SwipeNavigationHost extends FrameLayout {
    interface Handler {
        boolean allow(float x,float y);
        void start();
        void progress(float completion);
        void finish(boolean open);
    }

    private final Handler callback;
    private final float touchSlop;
    private final float activationDistance;
    private float downX,downY;
    private boolean eligible,tracking;
    private VelocityTracker velocity;

    SwipeNavigationHost(Context context,Handler callback) {
        super(context);
        this.callback=callback;
        this.touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        this.activationDistance=Math.max(touchSlop*1.25f,EditorTheme.dp(context,11));
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        final int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            reset();
            downX=event.getX();downY=event.getY();
            eligible=callback.allow(downX,downY);
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
            if(!tracking && dx < -activationDistance && Math.abs(dx)>Math.abs(dy)*1.18f){
                tracking=true;
                // The original child must not receive ACTION_UP: cancel its touch.
                MotionEvent cancel=MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                callback.start();
            }
            if(tracking) {
                callback.progress(gestureFraction(-dx));
                return true;
            }
        }
        if(tracking) {
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                final float distance=downX-event.getX();
                float speed=0;
                if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=-velocity.getXVelocity();}
                boolean open=action==MotionEvent.ACTION_UP
                    && distance>Math.min(EditorTheme.dp(getContext(),75),getWidth()*.22f)
                    && Math.abs(event.getY()-downY)<Math.abs(distance)*1.3f;
                if(action==MotionEvent.ACTION_UP && distance>EditorTheme.dp(getContext(),34)
                    && speed>EditorTheme.dp(getContext(),410))open=true;
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

    private float gestureFraction(float delta){
        // Finger-following progress: the first 60% of the screen reveals 100%
        // of the wheel. A short gesture can commit without reaching the edge.
        return Math.max(0f,Math.min(1f,(delta-activationDistance)
            /Math.max(EditorTheme.dp(getContext(),155),getWidth()*.60f)));
    }
    private void reset(){
        eligible=false;tracking=false;
        if(velocity!=null){velocity.recycle();velocity=null;}
    }
}

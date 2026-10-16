package com.discnet.noise;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.OverScroller;
import java.util.Locale;

/** Center-index precision ruler with pixel-accurate drag, inertial scroll and reset. */
final class CameraRuler extends View {
    interface Listener {void changed(float value);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestures;
    private final OverScroller scroller;
    private Listener listener;
    private float minimum=1f,maximum=4f,step=.1f,neutral=1f,value=1f;
    private float startX,startValue;
    private boolean dragging;
    private long lastHaptic;
    private VelocityTracker velocity;
    private final float spacing;
    CameraRuler(Context context){
        super(context);
        spacing=dp(16);
        scroller=new OverScroller(context);
        // Low friction + hardware canvas keeps the ruler responsive on small phones.
        scroller.setFriction(.011f);
        setFocusable(true);
        setContentDescription("Régua graduada de precisão. Arraste para alterar, toque duas vezes para zerar");
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){return true;}
            @Override public boolean onDoubleTap(MotionEvent event){reset();return true;}
        });
    }
    void listen(Listener listener){this.listener=listener;}
    void configure(float min,float max,float quantum,float initial,float defaultValue){
        if(max<=min||quantum<=0)throw new IllegalArgumentException("Ruler bounds");
        scroller.forceFinished(true);
        minimum=min;maximum=max;step=quantum;neutral=defaultValue;
        setValue(initial,false);
    }
    float value(){return value;}
    void reset(){setValue(neutral,true);}
    void setValue(float v,boolean emit){
        float clamped=Math.max(minimum,Math.min(maximum,v));
        float quantized=minimum+Math.round((clamped-minimum)/step)*step;
        quantized=Math.max(minimum,Math.min(maximum,quantized));
        if(Math.abs(quantized-value)>.0001f){
            value=quantized;
            if(emit&&listener!=null)listener.changed(value);
            invalidate();
        }else invalidate();
    }
    private float dp(float a){return getResources().getDisplayMetrics().density*a;}
    /**
     * Make a single swipe cover useful adjustment range instead of needing
     * several full-screen drags. The last few steps remain accessible precisely.
     */
    private float tickSpacing(){
        float steps=(maximum-minimum)/step;
        float available=getWidth()>0?getWidth():dp(360);
        return Math.max(dp(2.2f),Math.min(spacing,available*1.75f/Math.max(1f,steps)));
    }
    private int labeledInterval(float tickPx){
        int target=Math.max(5,(int)Math.ceil(dp(65)/Math.max(1f,tickPx)));
        return (target+4)/5*5;
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float pixelsPerStep=tickSpacing();
        if(!scroller.isFinished()&&scroller.computeScrollOffset()){
            setValue(minimum+scroller.getCurrX()*step/pixelsPerStep,true);
            postInvalidateOnAnimation();
        }
        float width=getWidth(),height=getHeight(),center=width*.5f;
        paint.setTextSize(dp(10));paint.setTextAlign(Paint.Align.CENTER);
        float from=(value-minimum)/step;
        int left=Math.max(0,(int)Math.floor(from-center/pixelsPerStep)-2);
        int right=Math.min(Math.round((maximum-minimum)/step),
            (int)Math.ceil(from+center/pixelsPerStep)+2);
        int labelStep=labeledInterval(pixelsPerStep);
        for(int tick=left;tick<=right;tick++){
            float x=center+(tick-from)*pixelsPerStep;
            boolean major=tick%labelStep==0;
            paint.setColor(major?0xbbeee5f4:0x607c7884);
            paint.setStrokeWidth(major?dp(1.5f):dp(.9f));
            float top=major?height*.40f:height*.52f;
            c.drawLine(x,top,x,height*.76f,paint);
            if(major){
                float num=minimum+tick*step;
                paint.setColor(0xb2eee9f4);
                c.drawText(format(num),x,height*.28f,paint);
            }
        }
        paint.setColor(0xfff5f2f8);
        paint.setStrokeWidth(dp(2.6f));
        c.drawRoundRect(center-dp(1.3f),height*.31f,center+dp(1.3f),height*.81f,dp(2),dp(2),paint);
    }
    private String format(float number){
        if(Math.abs(number)>=100)return String.format(Locale.US,"%.0f",number);
        if(Math.abs(number-Math.round(number))<.001f)return String.format(Locale.US,"%.0f",number);
        return String.format(Locale.US,"%.1f",number);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        gestures.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                if(velocity!=null)velocity.recycle();
                velocity=VelocityTracker.obtain();velocity.addMovement(e);
                startX=e.getX();startValue=value;dragging=false;
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(velocity!=null)velocity.addMovement(e);
                if(Math.abs(e.getX()-startX)>dp(4))dragging=true;
                if(dragging){
                    float before=value;
                    setValue(startValue+(startX-e.getX())/tickSpacing()*step,true);
                    if(Math.abs(before-value)>.0001f
                        &&SystemClock.uptimeMillis()-lastHaptic>110){
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                        lastHaptic=SystemClock.uptimeMillis();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(velocity!=null){velocity.addMovement(e);velocity.computeCurrentVelocity(1000);}
                if(dragging&&velocity!=null&&Math.abs(velocity.getXVelocity())>dp(350)){
                    float pixelsPerStep=tickSpacing();
                    int start=Math.round((value-minimum)/step*pixelsPerStep);
                    int upper=Math.round((maximum-minimum)/step*pixelsPerStep);
                    scroller.fling(start,0,Math.round(-velocity.getXVelocity()*.80f),0,
                        0,upper,0,0);
                    postInvalidateOnAnimation();
                }
                if(velocity!=null){velocity.recycle();velocity=null;}
                performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                if(velocity!=null){velocity.recycle();velocity=null;}
                return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

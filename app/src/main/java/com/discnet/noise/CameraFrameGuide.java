package com.discnet.noise;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Touch-consuming camera-only focus and digital-framing overlay. */
final class CameraFrameGuide extends View {
    interface Gestures {
        void focus(float x,float y);
        void zoom(float factor);
    }
    private final Gestures gestures;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame=new RectF();
    private final ScaleGestureDetector pinch;
    private float ratio=1f,zoom=1f,touchX,touchY,focusX=-1,focusY=-1;
    private long touchAt,focusAt;
    private boolean multiple,moved;

    CameraFrameGuide(Context context,Gestures gestures){
        super(context);
        this.gestures=gestures;
        setClickable(true);
        setFocusable(true);
        setContentDescription("Enquadramento da câmera. Toque para focar, use dois dedos para zoom");
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                if(detector.getScaleFactor()>0f)gestures.zoom(detector.getScaleFactor());
                return true;
            }
        });
    }
    void ratio(float wideToShort){ratio=Math.max(1f,Math.min(2f,wideToShort));invalidate();}
    void zoom(float value){zoom=Math.max(1f,Math.min(4f,value));invalidate();}
    float zoom(){return zoom;}
    float ratio(){return ratio;}
    RectF frame(){
        geometry(getWidth(),getHeight(),ratio,frame);
        return new RectF(frame);
    }
    static void geometry(int w,int h,float ratio,RectF out){
        if(w<=0||h<=0){out.setEmpty();return;}
        float aspect=h>=w?1f/ratio:ratio;
        float maxWidth=w*.86f,maxHeight=h*.76f;
        float width=Math.min(maxWidth,maxHeight*aspect);
        float height=width/aspect;
        float cx=w*.5f,cy=h*.5f;
        out.set(cx-width*.5f,cy-height*.5f,cx+width*.5f,cy+height*.5f);
    }
    /**
     * Crop the oriented JPEG in exactly the region shown by the frame,
     * accounting for the TextureView's center-cover transform and digital zoom.
     */
    static Rect photoCrop(int imageW,int imageH,int viewW,int viewH,RectF guide,float zoom){
        if(imageW<1||imageH<1||viewW<1||viewH<1||guide.isEmpty())
            return new Rect(0,0,Math.max(1,imageW),Math.max(1,imageH));
        float cover=Math.max(viewW/(float)imageW,viewH/(float)imageH);
        float scale=cover*Math.max(1f,zoom);
        int outW=Math.min(imageW,Math.max(1,Math.round(guide.width()/scale)));
        int outH=Math.min(imageH,Math.max(1,Math.round(guide.height()/scale)));
        float cx=imageW*.5f+(guide.centerX()-viewW*.5f)/scale;
        float cy=imageH*.5f+(guide.centerY()-viewH*.5f)/scale;
        int left=Math.max(0,Math.min(imageW-outW,Math.round(cx-outW*.5f)));
        int top=Math.max(0,Math.min(imageH-outH,Math.round(cy-outH*.5f)));
        return new Rect(left,top,left+outW,top+outH);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        geometry(getWidth(),getHeight(),ratio,frame);
        if(frame.isEmpty())return;
        paint.setColor(0x99000000);paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0,0,getWidth(),frame.top,paint);
        canvas.drawRect(0,frame.top,frame.left,frame.bottom,paint);
        canvas.drawRect(frame.right,frame.top,getWidth(),frame.bottom,paint);
        canvas.drawRect(0,frame.bottom,getWidth(),getHeight(),paint);
        paint.setColor(0xe8ffffff);paint.setStrokeWidth(dp(1.5f));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(frame,dp(2),dp(2),paint);
        paint.setColor(0x7dffffff);paint.setStrokeWidth(dp(0.7f));
        for(int i=1;i<=2;i++){
            float x=frame.left+frame.width()*i/3f;
            float y=frame.top+frame.height()*i/3f;
            canvas.drawLine(x,frame.top,x,frame.bottom,paint);
            canvas.drawLine(frame.left,y,frame.right,y,paint);
        }
        if(focusX>=0&&android.os.SystemClock.uptimeMillis()-focusAt<1100){
            paint.setStrokeWidth(dp(1.8f));paint.setColor(0xffffddbb);
            canvas.drawRoundRect(focusX-dp(23),focusY-dp(23),
                focusX+dp(23),focusY+dp(23),dp(2),dp(2),paint);
            canvas.drawCircle(focusX,focusY,dp(2),paint);
            postInvalidateDelayed(1100);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private float dp(float amount){return amount*getResources().getDisplayMetrics().density;}
    @Override public boolean onTouchEvent(MotionEvent e){
        pinch.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                touchX=e.getX();touchY=e.getY();touchAt=e.getEventTime();
                moved=false;multiple=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiple=true;return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.hypot(e.getX()-touchX,e.getY()-touchY)>dp(14))moved=true;
                return true;
            case MotionEvent.ACTION_UP:
                if(!multiple&&!moved&&e.getEventTime()-touchAt<550){
                    focusX=e.getX();focusY=e.getY();focusAt=android.os.SystemClock.uptimeMillis();
                    gestures.focus(focusX,focusY);invalidate();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

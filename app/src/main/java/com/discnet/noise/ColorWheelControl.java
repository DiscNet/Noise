package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Compact independent color-grade wheel. X/Y tint vector is stored in EditorDocument. */
final class ColorWheelControl extends View {
    interface Listener {void begin();void change(float x,float y);void end();}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix=new Matrix();
    private SweepGradient spectrum;
    private final Listener listener;
    private float x,y,cx,cy,r;
    private boolean dragging;
    ColorWheelControl(Context c,Listener listener){
        super(c);this.listener=listener;
        setMinimumHeight(EditorTheme.dp(c,152));
        setContentDescription("Roda cromática; arraste para escolher a tonalidade");
        setFocusable(true);
    }
    void bind(float x,float y){this.x=x;this.y=y;invalidate();}
    float selectedX(){return x;}
    float selectedY(){return y;}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);
        cx=w*.5f;cy=h*.5f;
        r=Math.max(12f,Math.min(w,h)*.43f);
        spectrum=new SweepGradient(cx,cy,
            new int[]{0xffff657f,0xffffd17a,0xffb2f59a,0xff74e9cb,
                0xff8eafff,0xffe9a4ff,0xffff657f},null);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(spectrum==null)return;
        paint.setStyle(Paint.Style.FILL);paint.setShader(spectrum);
        canvas.drawCircle(cx,cy,r,paint);paint.setShader(null);
        paint.setColor(0xee17161d);canvas.drawCircle(cx,cy,r*.20f,paint);
        paint.setColor(0x6f17161d);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(EditorTheme.dp(getContext(),1.4f));
        canvas.drawCircle(cx,cy,r*.61f,paint);
        paint.setStyle(Paint.Style.FILL);
        float px=cx+x*r*.84f,py=cy+y*r*.84f;
        halo.setColor(0x65e4c7ff);canvas.drawCircle(px,py,EditorTheme.dp(getContext(),13),halo);
        paint.setColor(0xfff7efff);canvas.drawCircle(px,py,EditorTheme.dp(getContext(),8),paint);
        paint.setColor(0xff241b30);canvas.drawCircle(px,py,EditorTheme.dp(getContext(),4),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(Math.hypot(e.getX()-cx,e.getY()-cy)>r+EditorTheme.dp(getContext(),16))return false;
                dragging=true;getParent().requestDisallowInterceptTouchEvent(true);
                listener.begin();pick(e);return true;
            case MotionEvent.ACTION_MOVE:
                if(dragging){pick(e);return true;}break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(dragging){if(e.getActionMasked()==MotionEvent.ACTION_UP)pick(e);
                    dragging=false;listener.end();performClick();return true;}break;
        }
        return super.onTouchEvent(e);
    }
    private void pick(MotionEvent e){
        float nx=(e.getX()-cx)/(r*.84f),ny=(e.getY()-cy)/(r*.84f);
        float mag=(float)Math.hypot(nx,ny);
        if(mag>1f){nx/=mag;ny/=mag;}
        x=nx;y=ny;invalidate();listener.change(x,y);
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

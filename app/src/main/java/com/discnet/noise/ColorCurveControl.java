package com.discnet.noise;
import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Six editable curve stops, GPU-sampled with smooth interpolation. */
final class ColorCurveControl extends View {
    interface Listener {void begin();void change(int knot,float value);void end();}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final float[] points=new float[6];
    private final Listener listener;
    private final boolean hueAxis;
    private int active=-1;
    private float left,top,width,height;
    ColorCurveControl(Context c,boolean hueAxis,Listener callback){
        super(c);this.hueAxis=hueAxis;listener=callback;
        setMinimumHeight(EditorTheme.dp(c,154));
        setContentDescription("Curva de cor editável de seis pontos");
    }
    void bind(float[] data,int start){
        System.arraycopy(data,start,points,0,6);invalidate();
    }
    float point(int i){return points[i];}
    private float dp(float a){return EditorTheme.dp(getContext(),a);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        left=dp(23);top=dp(18);
        width=Math.max(1,w-dp(46));height=Math.max(1,h-dp(38));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        paint.setShader(null);paint.setColor(0xff1b1823);
        c.drawRoundRect(left,top,left+width,top+height,dp(11),dp(11),paint);
        for(int i=0;i<=5;i++){
            paint.setColor(i==0||i==5?0x444b4256:0x2d63576b);
            paint.setStrokeWidth(dp(1));
            float x=left+i*width/5f;
            c.drawLine(x,top,x,top+height,paint);
        }
        paint.setColor(0x78bba2cc);paint.setStrokeWidth(dp(1));
        c.drawLine(left,top+height*.5f,left+width,top+height*.5f,paint);
        if(hueAxis){
            for(int i=0;i<6;i++){
                float[] hsv={(i/6f)*360f,.65f,.83f};
                paint.setColor(Color.HSVToColor(hsv));paint.setAlpha(110);
                c.drawRect(left+i*width/6,top+height-dp(3),
                    left+(i+1)*width/6,top+height,paint);
            }
        }
        path.reset();
        for(int i=0;i<=100;i++){
            float u=i/100f*5f;
            int index=Math.min(4,(int)u);
            float blend=u-index;blend=blend*blend*(3-2*blend);
            float y=points[index]*(1-blend)+points[index+1]*blend;
            float px=left+i/100f*width,py=top+height*(.5f-y*.44f);
            if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
        }
        line.setShader(null);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(2.2f));
        line.setColor(EditorTheme.LIME);line.setStrokeCap(Paint.Cap.ROUND);
        c.drawPath(path,line);line.setStyle(Paint.Style.FILL);
        for(int i=0;i<6;i++){
            float px=left+i*width/5f;
            float py=top+height*(.5f-points[i]*.44f);
            paint.setColor(0xff281e33);c.drawCircle(px,py,dp(i==active?9:7),paint);
            paint.setColor(0xffeed6ff);c.drawCircle(px,py,dp(i==active?5:4),paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(e.getY()<top-dp(12)||e.getY()>top+height+dp(12))return false;
                active=Math.max(0,Math.min(5,Math.round((e.getX()-left)/width*5)));
                getParent().requestDisallowInterceptTouchEvent(true);
                listener.begin();move(e);return true;
            case MotionEvent.ACTION_MOVE:
                if(active>=0){move(e);return true;}break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(active>=0){
                    if(e.getActionMasked()==MotionEvent.ACTION_UP)move(e);
                    active=-1;invalidate();listener.end();performClick();return true;
                }break;
        }
        return super.onTouchEvent(e);
    }
    private void move(MotionEvent e){
        float value=Math.max(-1f,Math.min(1f,
            ((top+height*.5f-e.getY())/(height*.44f))));
        points[active]=value;listener.change(active,value);invalidate();
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

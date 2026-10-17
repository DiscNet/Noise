package com.discnet.noise.advancedstudio;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Interactive five-anchor gloss contour, plus photo-editor style preset contours. */
public final class StudioContourEditor extends View {
    public interface Callback {void begin();void preview();void finish();}
    private final StudioLayerStyles.Style style;
    private final Callback onChange;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selected=-1;
    public StudioContourEditor(Context c,StudioLayerStyles.Style style,Callback callback){
        super(c);this.style=style;this.onChange=callback;
        setContentDescription("Curva do contorno: arraste os pontos para ajustar brilho e relevo");
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDraw(Canvas canvas){
        int m=dp(18);float w=getWidth()-m*2f,h=getHeight()-m*2f;
        if(w<=0||h<=0)return;
        paint.setColor(0xff1e1a25);paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(m,m,m+w,m+h,dp(5),dp(5),paint);
        paint.setStrokeWidth(dp(.75f));paint.setColor(0xff454050);
        for(int i=1;i<4;i++){
            float x=m+w*i/4f,y=m+h*i/4f;
            canvas.drawLine(x,m,x,m+h,paint);canvas.drawLine(m,y,m+w,y,paint);
        }
        paint.setColor(0xffbe96ef);paint.setStrokeWidth(dp(2.5f));
        paint.setStyle(Paint.Style.STROKE);Path path=new Path();
        for(int i=0;i<=100;i++){
            float x=i/100f,yy=StudioLayerStyles.curve(style,x);
            if(i==0)path.moveTo(m+x*w,m+(1-yy)*h);
            else path.lineTo(m+x*w,m+(1-yy)*h);
        }
        canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        if("CUSTOM".equals(style.contour)){
            for(int i=0;i<5;i++){
                paint.setColor(i==selected?0xffffffff:0xffbf97ec);
                canvas.drawCircle(m+w*i/4f,m+h*(1-style.customCurve[i]),dp(5),paint);
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int m=dp(18);float w=getWidth()-m*2f,h=getHeight()-m*2f;
        if(w<=0||h<=0)return true;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                selected=Math.min(4,Math.max(0,Math.round((event.getX()-m)/w*4f)));
                onChange.begin();
            case MotionEvent.ACTION_MOVE:
                style.contour="CUSTOM";
                style.customCurve[selected]=Math.max(0,Math.min(1,1-(event.getY()-m)/h));
                onChange.preview();invalidate();return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                onChange.finish();selected=-1;invalidate();return true;
            default:return true;
        }
    }
}

package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.ImageView;

/** Device-independent drawn vector pictograms, not Unicode/emoji font glyphs. */
final class IconArt extends Drawable {
    static final int SETTINGS=30, GALLERY=31, RESIZE=32, SAVE=33, BACK=34,
        ORIGINAL=35, RESET=36, FOLDER=37, PLUS=38, MINUS=39, PHOTO=40, SHUFFLE=41;
    private final int symbol;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private int color=0xffeee5ff;
    IconArt(int symbol,int color){this.symbol=symbol;this.color=color;p.setStrokeWidth(1.8f);}
    static ImageView button(Context context,int symbol,String description){
        ImageView view=new ImageView(context);
        view.setImageDrawable(new IconArt(symbol,0xfff1e8fa));
        view.setContentDescription(description);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Glass.button(view,false);
        view.setPadding(Glass.dp(context,11),Glass.dp(context,11),
            Glass.dp(context,11),Glass.dp(context,11));
        view.setClickable(true);view.setFocusable(true);
        return view;
    }
    static View inline(Context context,int symbol){
        View view=new View(context);
        view.setBackground(new IconArt(symbol,0xffd6c4ea));
        view.setContentDescription("Ícone de "+symbol);
        return view;
    }
    private void ln(Canvas c,float a,float b,float d,float e){c.drawLine(a,b,d,e,p);}
    private void circle(Canvas c,float a,float b,float r){c.drawCircle(a,b,r,p);}
    private void rect(Canvas c,float a,float b,float d,float e){c.drawRoundRect(a,b,d,e,2f,2f,p);}
    private void arc(Canvas c,float a,float b,float d,float e,float start,float sweep){
        c.drawArc(a,b,d,e,start,sweep,false,p);
    }
    private void poly(Canvas c,float...xy){
        path.reset();path.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);
        c.drawPath(path,p);
    }
    @Override public void draw(Canvas canvas){
        android.graphics.Rect bounds=getBounds();
        if(bounds.width()==0||bounds.height()==0)return;
        canvas.save();
        float size=Math.min(bounds.width(),bounds.height());
        canvas.translate(bounds.centerX()-size/2f,bounds.centerY()-size/2f);
        canvas.scale(size/24f,size/24f);
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(1.8f);
        switch(symbol){
            case SETTINGS:
                circle(canvas,12,12,3.2f);circle(canvas,12,12,8.5f);
                for(int n=0;n<8;n++){double a=n*Math.PI/4d;
                    ln(canvas,(float)(12+8.4*Math.cos(a)),(float)(12+8.4*Math.sin(a)),
                        (float)(12+10.5*Math.cos(a)),(float)(12+10.5*Math.sin(a)));}
                break;
            case GALLERY:case PHOTO:
                rect(canvas,3,4,21,20);circle(canvas,8.2f,9,1.7f);
                poly(canvas,4,18,10,12,14,15,17,11,20,16);break;
            case RESIZE:
                poly(canvas,4,10,4,4,10,4);poly(canvas,14,20,20,20,20,14);
                ln(canvas,4,4,10,10);ln(canvas,20,20,14,14);break;
            case SAVE:
                poly(canvas,12,3,12,16);poly(canvas,7,11,12,16,17,11);ln(canvas,4,20,20,20);break;
            case BACK:poly(canvas,15,4,7,12,15,20);break;
            case ORIGINAL:
                circle(canvas,12,12,9);p.setStyle(Paint.Style.FILL);
                canvas.drawArc(3,3,21,21,-90,180,true,p);break;
            case RESET:
                arc(canvas,4,4,20,20,48,295);poly(canvas,4,5,4,11,10,10);break;
            case FOLDER:
                poly(canvas,3,8,3,19,21,19,21,9,12,9,10,6,3,6,3,8);break;
            case PLUS:ln(canvas,12,4,12,20);ln(canvas,4,12,20,12);break;
            case MINUS:ln(canvas,4,12,20,12);break;
            case SHUFFLE: poly(canvas,4,7,7,7,17,17,20,17);poly(canvas,16,14,20,17,16,20);
                poly(canvas,4,17,7,17,17,7,20,7);poly(canvas,16,4,20,7,16,10);break;
            case 0: // sun, saturation
                circle(canvas,12,12,4);
                for(int n=0;n<8;n++){double a=n*Math.PI/4d;
                    ln(canvas,(float)(12+6.2*Math.cos(a)),(float)(12+6.2*Math.sin(a)),
                        (float)(12+9.4*Math.cos(a)),(float)(12+9.4*Math.sin(a)));}break;
            case 1:case 13:circle(canvas,10,11,6);circle(canvas,14,13,6);break;
            case 2:poly(canvas,12,2,5,14,12,14,10,22,19,10,12,10,12,2);break;
            case 3:case 12:circle(canvas,12,12,9);ln(canvas,12,3,12,21);break;
            case 4:case 10:
                poly(canvas,12,2,14,10,22,12,14,14,12,22,10,14,2,12,10,10,12,2);break;
            case 5:arc(canvas,3,3,21,21,20,140);poly(canvas,7,17,12,20,17,17);break;
            case 6:arc(canvas,3,3,21,21,200,140);poly(canvas,7,7,12,4,17,7);break;
            case 7:case 14:
                for(int j=0;j<4;j++)for(int i=0;i<4;i++){p.setStyle(Paint.Style.FILL);
                    circle(canvas,5+i*4.6f,5+j*4.6f,0.85f); }break;
            case 8:circle(canvas,12,12,8);poly(canvas,12,4,17,12,12,20,7,12,12,4);break;
            case 9:case 25:
                for(int i=0;i<3;i++){path.reset();path.moveTo(3,6+i*6);
                    path.cubicTo(8,2+i*6,15,10+i*6,21,6+i*6);canvas.drawPath(path,p);}break;
            case 11:
                circle(canvas,9,9,5);circle(canvas,15,9,5);circle(canvas,12,15,5);break;
            case 15:case 19:
                circle(canvas,12,12,9);arc(canvas,7,7,17,17,20,195);break;
            case 16:circle(canvas,12,12,8);ln(canvas,4,6,20,18);ln(canvas,4,18,20,6);break;
            case 17:for(int i=0;i<3;i++){path.reset();path.moveTo(3,7+i*5);
                path.cubicTo(8,2+i*5,14,12+i*5,21,7+i*5);canvas.drawPath(path,p);}break;
            case 18:poly(canvas,12,2,12,22);poly(canvas,2,12,22,12);
                poly(canvas,5,5,19,19);poly(canvas,19,5,5,19);break;
            case 20:rect(canvas,4,4,20,20);ln(canvas,8,17,16,7);break;
            case 21:poly(canvas,3,12,21,12);poly(canvas,7,8,3,12,7,16);poly(canvas,17,8,21,12,17,16);break;
            case 22:poly(canvas,12,3,12,21);poly(canvas,8,7,12,3,16,7);poly(canvas,8,17,12,21,16,17);break;
            case 23:rect(canvas,5,5,19,19);poly(canvas,8,11,11,8,16,13);break;
            case 24:for(int i=0;i<3;i++)for(int j=0;j<3;j++)circle(canvas,6+i*6,6+j*6,1);break;
            default:circle(canvas,12,12,8);
        }
        canvas.restore();
    }
    @Override public void setAlpha(int alpha){color=(color&0xffffff)|((alpha&255)<<24);invalidateSelf();}
    @Override public void setColorFilter(android.graphics.ColorFilter cf){p.setColorFilter(cf);invalidateSelf();}
    @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
}

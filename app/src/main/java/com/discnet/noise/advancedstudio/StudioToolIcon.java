package com.discnet.noise.advancedstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Lightweight vector icons: independent of font glyphs and density. */
public final class StudioToolIcon extends View {
    private final String kind;
    private boolean selected;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public StudioToolIcon(Context context,String kind){
        super(context);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setSelectedTool(boolean value){selected=value;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int save=canvas.save();
        float unit=Math.min(getWidth(),getHeight())/24f;
        canvas.translate((getWidth()-24*unit)/2,(getHeight()-24*unit)/2);
        canvas.scale(unit,unit);
        p.setColor(selected?0xffd3afff:0xffc1b9c9);
        p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        Path path=new Path();
        switch(kind){
            case "move":
                path.moveTo(5,3);path.lineTo(5,19);path.lineTo(9,15);
                path.lineTo(12,21);path.lineTo(14,20);path.lineTo(11,14);
                path.lineTo(16,14);path.close();canvas.drawPath(path,p);break;
            case "transform":
                canvas.drawRect(4,4,20,20,p);
                for(int x:new int[]{4,20})for(int y:new int[]{4,20})
                    canvas.drawCircle(x,y,1.6f,p);
                break;
            case "hand":
                path.moveTo(8,19);path.lineTo(5,13);path.quadTo(4,11,6,11);
                path.lineTo(9,14);path.lineTo(9,5);path.quadTo(9,3,11,4);
                path.lineTo(11,11);path.lineTo(13,7);path.lineTo(15,11);
                path.lineTo(17,9);path.lineTo(19,12);path.lineTo(19,16);
                path.quadTo(18,21,12,21);path.close();canvas.drawPath(path,p);break;
            case "brush":
                path.moveTo(5,18);path.cubicTo(10,18,12,14,14,10);
                path.lineTo(20,4);path.lineTo(21,6);path.lineTo(15,13);
                path.cubicTo(13,16,11,20,5,20);canvas.drawPath(path,p);
                canvas.drawLine(4,21,11,21,p);break;
            case "eraser":
                path.moveTo(3,15);path.lineTo(13,4);path.quadTo(15,2,17,5);
                path.lineTo(21,9);path.quadTo(23,11,21,13);
                path.lineTo(13,21);path.lineTo(9,21);path.close();
                canvas.drawPath(path,p);canvas.drawLine(12,12,18,18,p);break;
            case "selection":
                p.setPathEffect(new android.graphics.DashPathEffect(new float[]{2,2},0));
                canvas.drawRect(4,5,20,19,p);p.setPathEffect(null);break;
            case "shape":
                canvas.drawRect(3,5,13,16,p);canvas.drawOval(11,10,21,20,p);break;
            case "fill":
                path.moveTo(5,4);path.lineTo(17,16);path.lineTo(11,21);
                path.lineTo(2,12);path.close();canvas.drawPath(path,p);
                canvas.drawLine(4,12,18,12,p);break;
            case "type":
                canvas.drawLine(4,6,20,6,p);canvas.drawLine(12,6,12,20,p);
                canvas.drawLine(8,20,16,20,p);break;
            case "crop":
                canvas.drawLine(7,3,7,18,p);canvas.drawLine(3,17,19,17,p);
                canvas.drawLine(12,7,21,7,p);canvas.drawLine(17,12,17,21,p);break;
            case "mask":
                canvas.drawRect(4,4,20,20,p);
                p.setStyle(Paint.Style.FILL);canvas.drawArc(7,7,17,17,90,180,true,p);
                break;
            default:canvas.drawCircle(12,12,7,p);break;
        }
        canvas.restoreToCount(save);
    }
}

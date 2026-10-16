package com.discnet.noise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;

/**
 * One gesture-synchronized viscous membrane between neighboring orbital icons.
 * Its single field follows the wheel's fractional offset and reverses without
 * allocating animators, restarting timers or dropping frames between items.
 */
final class LiquidSelectorLayer {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softEdge=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path field=new Path();
    private final float density;
    private boolean connected;
    private float displayedProgress;
    LiquidSelectorLayer(Context context){
        density=context.getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        softEdge.setStyle(Paint.Style.STROKE);
        softEdge.setStrokeWidth(3.1f*density);
        softEdge.setStrokeJoin(Paint.Join.ROUND);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1.05f*density);
        outline.setStrokeCap(Paint.Cap.ROUND);
        outline.setStrokeJoin(Paint.Join.ROUND);
        sheen.setStyle(Paint.Style.STROKE);
        sheen.setStrokeWidth(.65f*density);
        sheen.setStrokeCap(Paint.Cap.ROUND);
    }
    boolean connected(){return connected;}
    float progress(){return displayedProgress;}
    private static float smooth(float a,float b,float x){
        float p=Math.max(0f,Math.min(1f,(x-a)/(b-a)));
        return p*p*(3f-2f*p);
    }
    /**
     * Each icon contributes a smoothly varying influence. The lower and upper
     * membranes share matching Bézier tangents at the neck, avoiding corners
     * in the moment of fusion.
     */
    void draw(Canvas canvas,float ax,float ay,float bx,float by,
              float progress,float radius){
        displayedProgress=Math.max(0f,Math.min(1f,progress));
        connected=ValueAnimator.areAnimatorsEnabled()
            &&displayedProgress>.002f&&displayedProgress<.998f;
        if(!connected)return;
        float dx=bx-ax,dy=by-ay;
        float distance=(float)Math.hypot(dx,dy);
        if(distance<1f){connected=false;return;}
        float p=displayedProgress;
        float aWeight=1f-smooth(0f,1f,p);
        float bWeight=smooth(0f,1f,p);
        float belly=(float)Math.sin(Math.PI*p);
        float ra=radius*(.52f+.47f*aWeight);
        float rb=radius*(.52f+.47f*bWeight);
        float halfNeck=Math.min(radius*.60f,
            (Math.min(ra,rb)*(.25f+.22f*belly)));
        float neckX=distance*(.50f+.08f*(aWeight-bWeight));
        float tangent=Math.min(distance*.28f,radius*.64f);

        field.reset();
        field.moveTo(-ra,0f);
        // Source lobe: first derivative is smooth at all four extrema.
        field.cubicTo(-ra,-ra*.56f,-ra*.56f,-ra,0f,-ra);
        field.cubicTo(ra*.53f,-ra,neckX-tangent,-halfNeck,neckX,-halfNeck);
        // Neck: horizontal tangent to the receiving lobe.
        field.cubicTo(neckX+tangent,-halfNeck,distance-rb*.55f,-rb,
            distance,-rb);
        field.cubicTo(distance+rb*.57f,-rb,distance+rb,-rb*.54f,
            distance+rb,0f);
        field.cubicTo(distance+rb,rb*.54f,distance+rb*.57f,rb,
            distance,rb);
        field.cubicTo(distance-rb*.55f,rb,neckX+tangent,halfNeck,
            neckX,halfNeck);
        field.cubicTo(neckX-tangent,halfNeck,ra*.53f,ra,0f,ra);
        field.cubicTo(-ra*.56f,ra,-ra,ra*.56f,-ra,0f);
        field.close();

        // The transfer must not pop in/out. Both opacity and light scattering
        // taper with continuous derivatives at the beginning and end.
        float appear=smooth(0f,.19f,p)*(1f-smooth(.81f,1f,p));
        int alpha=Math.round(204f*appear);
        int save=canvas.save();
        canvas.translate(ax,ay);
        canvas.rotate((float)Math.toDegrees(Math.atan2(dy,dx)));
        fill.setColor(Color.argb(Math.round(alpha*.63f),124,105,147));
        canvas.drawPath(field,fill);
        softEdge.setColor(Color.argb(Math.round(alpha*.17f),226,210,237));
        canvas.drawPath(field,softEdge);
        outline.setColor(Color.argb(alpha,245,241,247));
        canvas.drawPath(field,outline);
        sheen.setColor(Color.argb(Math.round(alpha*.30f),234,217,239));
        canvas.drawLine(ra*.30f,-ra*.82f,neckX,-halfNeck*.96f,sheen);
        canvas.restoreToCount(save);
    }
}

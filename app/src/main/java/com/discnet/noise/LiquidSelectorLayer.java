package com.discnet.noise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;

/**
 * Gesture-driven metaball bridge for adjacent orbital icons.
 *
 * The contour is a continuous Bézier approximation of two merged influence
 * fields. It is evaluated directly from the wheel's fractional angular offset:
 * reversing direction reverses the transfer in the same frame, with no
 * queued transition animators, sliding capsules, or particle decorations.
 */
final class LiquidSelectorLayer {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path field=new Path();
    private final float density;
    private boolean connected;
    private float displayedProgress;
    LiquidSelectorLayer(Context c){
        density=c.getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(density*1.15f);
        outline.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
        sheen.setStyle(Paint.Style.STROKE);
        sheen.setStrokeWidth(density*.60f);
    }
    boolean connected(){return connected;}
    float progress(){return displayedProgress;}
    /**
     * Coordinates refer to actual icon centers projected on the orbit.
     * Rendering happens behind child icons, preserving their readability.
     */
    void draw(Canvas canvas,float ax,float ay,float bx,float by,
              float progress,float iconRadius){
        displayedProgress=Math.max(0f,Math.min(1f,progress));
        connected=ValueAnimator.areAnimatorsEnabled()
            &&displayedProgress>.012f&&displayedProgress<.988f;
        if(!connected)return;
        float dx=bx-ax,dy=by-ay;
        float distance=(float)Math.hypot(dx,dy);
        if(distance<1f)return;
        // Material moves rather than two independent rings fading in/out.
        float transfer=displayedProgress;
        float rA=iconRadius*(1f-.48f*transfer);
        float rB=iconRadius*(.52f+.48f*transfer);
        float influence=(float)Math.sin(Math.PI*transfer);
        float neck=Math.max(iconRadius*.12f,Math.min(rA,rB)*(.28f+.12f*influence));
        float mid=distance*.5f+(rA-rB)*.10f;

        field.reset();
        field.moveTo(0f,-rA);
        // Two upper flowing shoulders join in one narrow negative-space neck.
        field.cubicTo(rA*.83f,-rA,mid-rA*.77f,-neck,mid,-neck);
        field.cubicTo(mid+rB*.75f,-neck,distance-rB*.90f,-rB,distance,-rB);
        // Positive end lobe.
        field.cubicTo(distance+rB*.78f,-rB,distance+rB*1.06f,-rB*.45f,
            distance+rB,0f);
        field.cubicTo(distance+rB*1.06f,rB*.45f,distance+rB*.78f,rB,
            distance,rB);
        // The lower surface mirrors the bridge with a gentle asymmetric drift.
        field.cubicTo(distance-rB*.83f,rB,mid+rB*.57f,neck*1.06f,mid,neck*1.06f);
        field.cubicTo(mid-rA*.68f,neck*1.06f,rA*.80f,rA,0f,rA);
        field.cubicTo(-rA*.82f,rA,-rA*1.06f,rA*.46f,-rA,0f);
        field.cubicTo(-rA*1.06f,-rA*.46f,-rA*.82f,-rA,0f,-rA);
        field.close();

        int save=canvas.save();
        canvas.translate(ax,ay);
        canvas.rotate((float)Math.toDegrees(Math.atan2(dy,dx)));
        int alpha=Math.round(205f*Math.min(1f,influence*1.65f));
        fill.setColor(Color.argb(Math.round(alpha*.64f),135,113,161));
        canvas.drawPath(field,fill);
        // White edge is a SINGLE continuous stroke. During transfer only,
        // both influence zones are connected by the same boundary.
        outline.setColor(Color.argb(alpha,241,232,246));
        canvas.drawPath(field,outline);
        // Surface reflection shares the same cool studio highlights.
        sheen.setColor(Color.argb(Math.round(alpha*.31f),228,209,242));
        canvas.drawLine(rA*.38f,-rA*.78f,mid,-neck*.93f,sheen);
        canvas.restoreToCount(save);
    }
}
package com.discnet.noise.advancedstudio;

import android.graphics.Canvas;
import android.graphics.RectF;

/** One canonical transform for on-canvas handles and exported layer compositing.
 * x/y denote the unrotated content bounds origin; rotation pivots around its center.
 */
public final class StudioTransformMath {
    private StudioTransformMath(){}
    public static float centerX(StudioDocument.Layer l){
        return l.x+l.naturalWidth()*l.scaleX*.5f;
    }
    public static float centerY(StudioDocument.Layer l){
        return l.y+l.naturalHeight()*l.scaleY*.5f;
    }
    public static void apply(Canvas c,StudioDocument.Layer l){
        float w=l.naturalWidth(),h=l.naturalHeight();
        c.translate(centerX(l),centerY(l));
        c.rotate(l.rotation);
        c.skew((float)Math.tan(Math.toRadians(l.skewX)),
            (float)Math.tan(Math.toRadians(l.skewY)));
        c.scale(l.scaleX,l.scaleY);
        c.translate(-w*.5f,-h*.5f);
    }
    public static float[] point(StudioDocument.Layer l,float px,float py){
        float w=l.naturalWidth(),h=l.naturalHeight();
        float x=(px-w*.5f)*l.scaleX,y=(py-h*.5f)*l.scaleY;
        float kx=(float)Math.tan(Math.toRadians(l.skewX));
        float ky=(float)Math.tan(Math.toRadians(l.skewY));
        float u=x+kx*y,v=y+ky*x;
        float radians=(float)Math.toRadians(l.rotation);
        float cos=(float)Math.cos(radians),sin=(float)Math.sin(radians);
        return new float[]{centerX(l)+u*cos-v*sin,centerY(l)+u*sin+v*cos};
    }
    /** Maps a point from document coordinates into source bitmap coordinates.
     * Inverts rotate -> skew -> scale, matching Canvas compositing exactly.
     */
    public static float[] inversePoint(StudioDocument.Layer l,float docX,float docY){
        float x=docX-centerX(l),y=docY-centerY(l);
        float radians=(float)Math.toRadians(l.rotation);
        float co=(float)Math.cos(radians),si=(float)Math.sin(radians);
        float u=x*co+y*si,v=-x*si+y*co;
        float kx=(float)Math.tan(Math.toRadians(l.skewX));
        float ky=(float)Math.tan(Math.toRadians(l.skewY));
        float det=1-kx*ky;
        if(Math.abs(det)<.0001f)det=Math.copySign(.0001f,det);
        float sx=(u-kx*v)/det,sy=(v-ky*u)/det;
        return new float[]{sx/l.scaleX+l.naturalWidth()*.5f,
            sy/l.scaleY+l.naturalHeight()*.5f};
    }
    public static RectF bounds(StudioDocument.Layer l){
        float w=l.naturalWidth(),h=l.naturalHeight();
        float[][] corners={point(l,0,0),point(l,w,0),
            point(l,w,h),point(l,0,h)};
        RectF b=new RectF(corners[0][0],corners[0][1],corners[0][0],corners[0][1]);
        for(float[] p:corners)b.union(p[0],p[1]);
        return b;
    }
}

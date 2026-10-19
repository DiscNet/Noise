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
    /** Resize the dragged side/corner while holding the opposite anchor
     * stationary. Positions are recomputed from the gesture's initial state
     * each frame to avoid cumulative drift or inverted handles. */
    public static void resizeFromHandle(StudioDocument.Layer layer,
         int handle,float dx,float dy,float originalX,float originalY,
         float originalSX,float originalSY,float rotation,boolean keepAspect){
        float rad=(float)Math.toRadians(rotation),co=(float)Math.cos(rad),
            si=(float)Math.sin(rad);
        float u=co*dx+si*dy,v=-si*dx+co*dy;
        int hSide=(handle==1||handle==4||handle==6)?-1:
            (handle==3||handle==5||handle==8)?1:0;
        int vSide=handle<=3?-1:handle>=6?1:0;
        if(hSide==0&&vSide==0)return;
        float w=layer.naturalWidth(),h=layer.naturalHeight();
        float newX=originalSX,newY=originalSY;
        if(hSide!=0)newX=Math.copySign(
            Math.max(.01f,Math.min(16f,Math.abs(originalSX)+
                hSide*u/Math.max(1,w))),originalSX);
        if(vSide!=0)newY=Math.copySign(
            Math.max(.01f,Math.min(16f,Math.abs(originalSY)+
                vSide*v/Math.max(1,h))),originalSY);
        if(keepAspect){
            float factor=hSide!=0?Math.abs(newX/originalSX):
                Math.abs(newY/originalSY);
            newX=Math.copySign(Math.max(.01f,Math.min(16,
                Math.abs(originalSX)*factor)),originalSX);
            newY=Math.copySign(Math.max(.01f,Math.min(16,
                Math.abs(originalSY)*factor)),originalSY);
        }
        float deltaW=(newX-originalSX)*w,deltaH=(newY-originalSY)*h;
        float kx=(float)Math.tan(Math.toRadians(layer.skewX));
        float ky=(float)Math.tan(Math.toRadians(layer.skewY));
        float localX=(hSide!=0?hSide*deltaW/2f:0);
        float localY=(vSide!=0?vSide*deltaH/2f:0);
        float shiftedX=localX+kx*localY,shiftedY=localY+ky*localX;
        float centerX=originalX+w*originalSX*.5f+
            co*shiftedX-si*shiftedY;
        float centerY=originalY+h*originalSY*.5f+
            si*shiftedX+co*shiftedY;
        layer.scaleX=newX;layer.scaleY=newY;
        layer.x=centerX-w*newX*.5f;layer.y=centerY-h*newY*.5f;
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

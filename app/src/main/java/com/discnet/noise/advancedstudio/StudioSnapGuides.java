package com.discnet.noise.advancedstudio;

import android.graphics.RectF;

/** Pure snapping math shared by finger move and interactive transform. */
public final class StudioSnapGuides {
    private StudioSnapGuides(){}
    public static final class Result {
        public final float dx,dy;
        public final Float xGuide,yGuide;
        Result(float dx,float dy,Float x,Float y){
            this.dx=dx;this.dy=dy;xGuide=x;yGuide=y;
        }
    }
    /** Candidate bounds are in document coordinates and have already moved.
     * This never changes layer state; the caller can apply exactly one delta.
     */
    public static Result snap(StudioDocument d,StudioDocument.Layer moving,
                              RectF candidate,float tolerance){
        if(!d.snapEnabled)return new Result(0,0,null,null);
        float[] mx={candidate.left,candidate.centerX(),candidate.right};
        float[] my={candidate.top,candidate.centerY(),candidate.bottom};
        float nearestX=tolerance+1,nearestY=tolerance+1;
        float dx=0,dy=0;Float gx=null,gy=null;
        java.util.ArrayList<Float> xx=new java.util.ArrayList<>(d.verticalGuides);
        java.util.ArrayList<Float> yy=new java.util.ArrayList<>(d.horizontalGuides);
        xx.add(0f);xx.add(d.width*.5f);xx.add((float)d.width);
        yy.add(0f);yy.add(d.height*.5f);yy.add((float)d.height);
        for(StudioDocument.Layer other:d.layers){
            if(other==moving||!other.visible)continue;
            RectF r=StudioTransformMath.bounds(other);
            xx.add(r.left);xx.add(r.centerX());xx.add(r.right);
            yy.add(r.top);yy.add(r.centerY());yy.add(r.bottom);
        }
        for(float target:xx)for(float v:mx){
            float delta=target-v;
            if(Math.abs(delta)<nearestX){
                nearestX=Math.abs(delta);dx=delta;gx=target;
            }
        }
        for(float target:yy)for(float v:my){
            float delta=target-v;
            if(Math.abs(delta)<nearestY){
                nearestY=Math.abs(delta);dy=delta;gy=target;
            }
        }
        if(nearestX>tolerance){dx=0;gx=null;}
        if(nearestY>tolerance){dy=0;gy=null;}
        return new Result(dx,dy,gx,gy);
    }
}

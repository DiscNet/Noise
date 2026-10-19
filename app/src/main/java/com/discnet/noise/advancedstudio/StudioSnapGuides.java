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
    private static final java.util.WeakHashMap<StudioDocument.Layer,Cache>
        BOUNDS_CACHE=new java.util.WeakHashMap<>();
    private static final class Cache {
        final String signature;
        final RectF local;
        Cache(String signature,RectF local){this.signature=signature;this.local=local;}
    }
    private static String signature(StudioDocument.Layer l){
        return l.type+":"+l.text+":"+l.fontFamily+":"+l.fontSize+":"+
            l.fontBold+":"+l.fontItalic+":"+l.letterSpacing+":"+
            l.lineSpacing+":"+l.textAlign+":"+l.objectWidth+":"+
            l.objectHeight+":"+l.color+":"+l.maskEnabled+":"+
            l.maskDensity+":"+(l.bitmap==null?"none":
                System.identityHashCode(l.bitmap)+"/"+l.bitmap.getGenerationId())+
            ":"+(l.mask==null?"none":
                System.identityHashCode(l.mask)+"/"+l.mask.getGenerationId());
    }
    /** The visible alpha silhouette, rather than the full layer's image
     * rectangle or the font's padded typographic metrics.
     * Relative (untransformed) bounds are cached across pointer moves.
     */
    public static RectF localVisibleBounds(StudioDocument.Layer layer){
        if(layer==null||layer.opacity<=0)return null;
        String key=signature(layer);
        Cache cached=BOUNDS_CACHE.get(layer);
        if(cached!=null&&key.equals(cached.signature))
            return cached.local==null?null:new RectF(cached.local);
        android.graphics.Bitmap bitmap=null;
        RectF result=null;
        try{
            bitmap=StudioLayerEffects.sourceBitmap(layer);
            if(bitmap==null)return null;
            int w=bitmap.getWidth(),h=bitmap.getHeight();
            if(w<1||h<1)return null;
            android.graphics.Bitmap mask=layer.maskEnabled?layer.mask:null;
            boolean masked=mask!=null&&mask.getWidth()==w&&mask.getHeight()==h;
            int[] row=new int[w],maskRow=masked?new int[w]:null;
            int left=w,top=h,right=-1,bottom=-1;
            // For extremely large bitmaps, bounds computation is sampled at
            // up to 2-pixel spacing. A 1x scan is used in the normal case.
            int stride=(long)w*h>5_000_000L?2:1;
            for(int y=0;y<h;y+=stride){
                bitmap.getPixels(row,0,w,0,y,w,1);
                if(masked)mask.getPixels(maskRow,0,w,0,y,w,1);
                for(int x=0;x<w;x+=stride){
                    int a=android.graphics.Color.alpha(row[x]);
                    if(masked)a=Math.round(a*(1-layer.maskDensity/100f+
                        layer.maskDensity/100f*
                        android.graphics.Color.red(maskRow[x])/255f));
                    if(a<=8)continue;
                    if(x<left)left=x;if(x>right)right=x;
                    if(y<top)top=y;if(y>bottom)bottom=y;
                }
            }
            if(right>=left&&bottom>=top)
                result=new RectF(left,top,Math.min(w,right+stride),
                                 Math.min(h,bottom+stride));
        }catch(RuntimeException ignored){
            // A transient invalid bitmap must not interrupt the drag gesture.
            result=null;
        }finally{
            if(bitmap!=null&&bitmap!=layer.bitmap&&!bitmap.isRecycled())
                bitmap.recycle();
        }
        BOUNDS_CACHE.put(layer,new Cache(key,result));
        return result==null?null:new RectF(result);
    }
    public static RectF visibleBounds(StudioDocument.Layer layer){
        RectF local=localVisibleBounds(layer);
        if(local==null)return null;
        float[][] corners={
            StudioTransformMath.point(layer,local.left,local.top),
            StudioTransformMath.point(layer,local.right,local.top),
            StudioTransformMath.point(layer,local.right,local.bottom),
            StudioTransformMath.point(layer,local.left,local.bottom)
        };
        RectF bounds=new RectF(corners[0][0],corners[0][1],
            corners[0][0],corners[0][1]);
        for(float[] p:corners)bounds.union(p[0],p[1]);
        return bounds;
    }
    /** Candidate bounds are in document coordinates and have already moved.
     * This never changes layer state; the caller can apply exactly one delta.
     */
    public static Result snap(StudioDocument d,StudioDocument.Layer moving,
                              RectF candidate,float tolerance){
        if(!d.snapEnabled)return new Result(0,0,null,null);
        RectF movingVisible=visibleBounds(moving);
        if(movingVisible==null)return new Result(0,0,null,null);
        RectF original=StudioTransformMath.bounds(moving);
        // Public callers may submit a hypothetical candidate without
        // modifying the layer; account for that offset.
        movingVisible.offset(candidate.left-original.left,
                             candidate.top-original.top);
        float[] mx={movingVisible.left,movingVisible.centerX(),movingVisible.right};
        float[] my={movingVisible.top,movingVisible.centerY(),movingVisible.bottom};
        float nearestX=tolerance+1,nearestY=tolerance+1;
        float dx=0,dy=0;Float gx=null,gy=null;
        java.util.ArrayList<Float> xx=new java.util.ArrayList<>(d.verticalGuides);
        java.util.ArrayList<Float> yy=new java.util.ArrayList<>(d.horizontalGuides);
        xx.add(0f);xx.add(d.width*.5f);xx.add((float)d.width);
        yy.add(0f);yy.add(d.height*.5f);yy.add((float)d.height);
        for(StudioDocument.Layer other:d.layers){
            if(other==moving||!other.visible)continue;
            RectF r=visibleBounds(other);
            if(r==null)continue;
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

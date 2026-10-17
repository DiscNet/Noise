package com.discnet.noise.advancedstudio;

import android.graphics.*;

/**
 * High-quality bevel from a supersampled coverage mask and a true Euclidean
 * signed-distance field. Geometry is never built from a binary staircase at
 * the final output resolution. All work is bounded for low-memory phones.
 */
public final class StudioBevelRenderer {
    private static final float INF=1.0e12f;
    private static final int MAX_WORK_PIXELS=1_100_000;
    private StudioBevelRenderer(){}

    static Bitmap render(StudioDocument.Layer layer,Bitmap source,
                         StudioLayerStyles.Style style,int padding){
        int w=source.getWidth(),h=source.getHeight();
        int targetWidth=w+padding*2,targetHeight=h+padding*2;
        long pixels=(long)targetWidth*targetHeight;
        float factor=1f;
        if(pixels>MAX_WORK_PIXELS){
            // Avoid large full-resolution EDT buffers on limited-RAM Android.
            // The result is resized to the full layer dimensions at the end.
            factor=(float)Math.sqrt(MAX_WORK_PIXELS/(double)pixels)*.90f;
        }else{
            for(int candidate=4;candidate>=2;candidate--){
                if(pixels*candidate*candidate<=MAX_WORK_PIXELS){
                    factor=candidate;break;
                }
            }
        }
        int rw=Math.max(1,Math.round(w*factor));
        int rh=Math.max(1,Math.round(h*factor));
        int workPadding=Math.max(0,Math.round(padding*factor));
        Bitmap hi=null,composited=null;
        try{
            hi=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888);
            Canvas hiCanvas=new Canvas(hi);
            hiCanvas.scale(factor,factor);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|
                Paint.SUBPIXEL_TEXT_FLAG);
            if(layer.type==StudioDocument.Type.TEXT&&
                !StudioLayerEffects.requiresProcessing(layer)){
                p.setColor(layer.color);
                StudioTypography.draw(hiCanvas,layer,p);
            }else if((layer.type==StudioDocument.Type.RECTANGLE||
                    layer.type==StudioDocument.Type.ELLIPSE)&&
                    !StudioLayerEffects.requiresProcessing(layer)){
                p.setColor(layer.color);
                if(layer.type==StudioDocument.Type.RECTANGLE)
                    hiCanvas.drawRect(0,0,layer.objectWidth,layer.objectHeight,p);
                else hiCanvas.drawOval(0,0,layer.objectWidth,layer.objectHeight,p);
            }else{
                hiCanvas.drawBitmap(source,0,0,p);
            }
            StudioLayerStyles.Style work=style.copy();
            work.size*=factor;work.soften*=factor;
            composited=shade(hi,work,workPadding,1);
            if(factor==1f)return composited;
            if(factor>=2f&&Math.abs(factor-Math.round(factor))<.001f&&
                composited.getWidth()==targetWidth*Math.round(factor)&&
                composited.getHeight()==targetHeight*Math.round(factor))
                return areaDownsample(composited,Math.round(factor),targetWidth,targetHeight);
            // Fallback is used only for large documents / odd dimensions.
            return Bitmap.createScaledBitmap(composited,targetWidth,targetHeight,true);
        }finally{
            if(composited!=null&&factor!=1f&&!composited.isRecycled())
                composited.recycle();
            if(hi!=null&&!hi.isRecycled())hi.recycle();
        }
    }

    /** Exact squared Euclidean distance transform (Felzenszwalb/Huttenlocher)
     * evaluated in O(width*height), using reusable line buffers. */
    private static void edtLine(float[] input,int inputOffset,int step,int count,
                                float[] output,int outputOffset,int outputStep,
                                int[] sites,float[] boundaries){
        int tail=-1;
        for(int q=0;q<count;q++){
            float cost=input[inputOffset+q*step];
            if(cost>=INF*.5f)continue;
            float boundary=-INF;
            while(tail>=0){
                int prev=sites[tail];
                float previous=input[inputOffset+prev*step];
                boundary=((cost+q*q)-(previous+prev*prev))/(2f*(q-prev));
                if(boundary>boundaries[tail])break;
                tail--;
            }
            if(tail<0){tail=0;sites[0]=q;boundaries[0]=-INF;}
            else{tail++;sites[tail]=q;boundaries[tail]=boundary;}
            boundaries[tail+1]=INF;
        }
        if(tail<0){
            for(int q=0;q<count;q++)output[outputOffset+q*outputStep]=INF;
            return;
        }
        int k=0;
        for(int q=0;q<count;q++){
            while(k<tail&&boundaries[k+1]<q)k++;
            int site=sites[k];
            float delta=q-site;
            output[outputOffset+q*outputStep]=
                delta*delta+input[inputOffset+site*step];
        }
    }
    private static float[] distances(int[] alpha,int w,int h,boolean toOpaque){
        int n=w*h,max=Math.max(w,h);
        float[] field=new float[n],partial=new float[n],squared=new float[n];
        int[] sites=new int[max];float[] boundaries=new float[max+1];
        for(int i=0;i<n;i++)
            field[i]=(toOpaque?alpha[i]>=128:alpha[i]<128)?0:INF;
        for(int y=0;y<h;y++)
            edtLine(field,y*w,1,w,partial,y*w,1,sites,boundaries);
        field=null;
        for(int x=0;x<w;x++)
            edtLine(partial,x,w,h,squared,x,w,sites,boundaries);
        return squared;
    }
    private static float clamp(float n){return Math.max(0,Math.min(1,n));}
    private static float ease(float t){t=clamp(t);return t*t*(3-2*t);}
    private static float read(float[] data,int w,int h,int x,int y){
        return data[Math.max(0,Math.min(h-1,y))*w+
                    Math.max(0,Math.min(w-1,x))];
    }
    private static int blendRgb(int shadow,int highlight,float t){
        t=clamp(t);
        int r=Math.round(Color.red(shadow)*(1-t)+Color.red(highlight)*t);
        int g=Math.round(Color.green(shadow)*(1-t)+Color.green(highlight)*t);
        int b=Math.round(Color.blue(shadow)*(1-t)+Color.blue(highlight)*t);
        return (r<<16)|(g<<8)|b;
    }
    private static Bitmap shade(Bitmap source,StudioLayerStyles.Style s,
                                int padding,int scale){
        int W=source.getWidth()+2*padding,H=source.getHeight()+2*padding;
        int n=W*H;
        if(n>MAX_WORK_PIXELS&&scale>1)
            throw new IllegalArgumentException("Bisel excede orçamento de memória");
        int[] pixels=new int[source.getWidth()*source.getHeight()];
        source.getPixels(pixels,0,source.getWidth(),0,0,
            source.getWidth(),source.getHeight());
        int[] alpha=new int[n];
        for(int y=0;y<source.getHeight();y++)
            for(int x=0;x<source.getWidth();x++)
                alpha[(y+padding)*W+x+padding]=Color.alpha(
                    pixels[y*source.getWidth()+x]);
        float[] toInside=distances(alpha,W,H,true);
        float[] toOutside=distances(alpha,W,H,false);
        float[] signed=new float[n];
        for(int i=0;i<n;i++){
            float coverage=alpha[i]/255f;
            // Correct the binary EDT boundary with real glyph alpha.
            float signedDistance=alpha[i]>=128?
                (float)Math.sqrt(toOutside[i])-.5f:
                .5f-(float)Math.sqrt(toInside[i]);
            if(!Float.isFinite(signedDistance))signedDistance=0;
            signed[i]=signedDistance+(coverage-.5f)*.65f;
        }
        // Continuous signed-distance smoothing removes the quantized diagonal
        // gradients that produced the black-striped / white-ghost Luka text.
        float[] smoothed=new float[n];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float sum=0;
            for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){
                int weight=(ox==0?2:1)*(oy==0?2:1);
                sum+=read(signed,W,H,x+ox,y+oy)*weight;
            }
            smoothed[y*W+x]=sum/16f;
        }
        int[] out=new int[n];
        float angle=(float)Math.toRadians(s.angle);
        float lx=(float)Math.cos(angle),ly=-(float)Math.sin(angle);
        float size=Math.max(1f,s.size*scale),soften=s.soften*scale;
        float width=size+Math.min(size*.65f,soften);
        float gain=Math.min(2.5f,Math.max(.15f,s.depth/100f));
        boolean outer="OUTER_BEVEL".equals(s.bevelStyle);
        boolean embossed="EMBOSS".equals(s.bevelStyle)||
                         "PILLOW".equals(s.bevelStyle);
        boolean chiselHard="CHISEL_HARD".equals(s.technique);
        boolean chiselSoft="CHISEL_SOFT".equals(s.technique);
        float sun=(float)Math.sin(Math.toRadians(s.altitude));
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int i=y*W+x;
            float coverage=alpha[i]/255f;
            float d=signed[i],ad=Math.abs(d);
            boolean outside=d<0;
            if((outer&&!outside)||(!outer&&!embossed&&outside)||
                (embossed&&false))continue;
            if(ad>width+scale||coverage<=.001f&&!outside)continue;
            float surface=outside?1-coverage:coverage;
            float q=clamp(ad/width);
            float ramp;
            // The three techniques differ in the surface itself, rather
            // than merely toggling a hard alpha threshold.
            if(chiselHard){
                float aa=Math.max(.025f,1.15f/width);
                ramp=1-ease((q-(.81f-aa))/(2*aa));
            }else if(chiselSoft){
                ramp=(float)Math.pow(1-ease(q),.48f);
            }else{
                ramp=(float)Math.cos(q*Math.PI*.5f);
                ramp*=ramp;
            }
            // A soft, normalized 3x3 derivative yields stable shading even
            // around circular counters and angular glyph junctions.
            float gx=(read(smoothed,W,H,x+1,y-1)+
                2*read(smoothed,W,H,x+1,y)+read(smoothed,W,H,x+1,y+1)-
                read(smoothed,W,H,x-1,y-1)-2*read(smoothed,W,H,x-1,y)-
                read(smoothed,W,H,x-1,y+1))*.125f;
            float gy=(read(smoothed,W,H,x-1,y+1)+
                2*read(smoothed,W,H,x,y+1)+read(smoothed,W,H,x+1,y+1)-
                read(smoothed,W,H,x-1,y-1)-2*read(smoothed,W,H,x,y-1)-
                read(smoothed,W,H,x+1,y-1))*.125f;
            float normal=(gx*lx+gy*ly)/
                (float)Math.max(.18,Math.hypot(gx,gy));
            if("DOWN".equals(s.direction))normal=-normal;
            if(outside)normal=-normal;
            if("PILLOW".equals(s.bevelStyle))normal=-normal;
            float oriented=Math.max(-1,Math.min(1,normal));
            float intensity=chiselHard?1f:chiselSoft?.87f:.68f;
            float specular=clamp(Math.abs(oriented)*intensity+
                (chiselHard?.08f:0));
            float profile=StudioLayerStyles.curve(s,1-q);
            float strength=ramp*gain*specular*sun*profile;
            // Only an alpha-weighted highlight/shadow is composited on top.
            // Neutral or transparent exterior remains untouched: no ghost
            // silhouettes or solid white "extrusions".
            float polarity=clamp(.5f+.5f*oriented);
            float lightOpacity=s.shadowOpacity+
                (s.highlightOpacity-s.shadowOpacity)*polarity;
            int pigment=blendRgb(s.shade,s.highlight,polarity);
            int a=Math.round(255*clamp(surface*strength*lightOpacity));
            if(a>0)out[i]=(a<<24)|pigment;
        }
        Bitmap result=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        result.setPixels(out,0,W,0,0,W,H);
        return result;
    }

    /** Box-filter in premultiplied alpha; bilinear one-tap shrink aliases text. */
    private static Bitmap areaDownsample(Bitmap source,int scale,int w,int h){
        int W=source.getWidth();
        int[] raw=new int[W*source.getHeight()];
        source.getPixels(raw,0,W,0,0,W,source.getHeight());
        int[] output=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            long sumA=0,sumR=0,sumG=0,sumB=0;
            for(int iy=0;iy<scale;iy++)for(int ix=0;ix<scale;ix++){
                int col=raw[(y*scale+iy)*W+x*scale+ix],a=Color.alpha(col);
                sumA+=a;sumR+=(long)Color.red(col)*a;
                sumG+=(long)Color.green(col)*a;
                sumB+=(long)Color.blue(col)*a;
            }
            if(sumA==0)continue;
            int a=Math.min(255,(int)((sumA+scale*scale/2)/(scale*scale)));
            int r=(int)(sumR/sumA),g=(int)(sumG/sumA),b=(int)(sumB/sumA);
            output[y*w+x]=Color.argb(a,r,g,b);
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        result.setPixels(output,0,w,0,0,w,h);return result;
    }
}

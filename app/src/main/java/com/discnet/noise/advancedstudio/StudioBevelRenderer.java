package com.discnet.noise.advancedstudio;

import android.graphics.*;

/**
 * Bevel / Emboss renderer using a continuous signed Euclidean-distance field.
 *
 * Unlike a white-to-black painted gradient, it creates two independent,
 * transparent light and shade masks for Screen / Multiply compositing.
 * It never paints neutral gray onto the original artwork.
 */
public final class StudioBevelRenderer {
    private static final float INF=1.0e12f;
    private static final int MAX_WORK_PIXELS=960_000;
    private StudioBevelRenderer(){}

    public static final class Layers {
        public final Bitmap light,shade;
        Layers(Bitmap light,Bitmap shade){this.light=light;this.shade=shade;}
        int bytes(){return light.getByteCount()+shade.getByteCount();}
    }

    static Layers render(StudioDocument.Layer layer,Bitmap source,
                         StudioLayerStyles.Style style,int padding){
        int outputW=source.getWidth()+2*padding,outputH=source.getHeight()+2*padding;
        float scale=1f;
        long outputPixels=(long)outputW*outputH;
        if(outputPixels>MAX_WORK_PIXELS){
            scale=(float)Math.sqrt((double)MAX_WORK_PIXELS/outputPixels)*.94f;
        }else{
            for(int n=4;n>=2;n--){
                if(outputPixels*(long)n*n<=MAX_WORK_PIXELS){scale=n;break;}
            }
        }
        int w=Math.max(1,Math.round(source.getWidth()*scale));
        int h=Math.max(1,Math.round(source.getHeight()*scale));
        int border=Math.max(0,Math.round(padding*scale));
        Bitmap hi=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Layers work=null;
        try{
            Canvas cv=new Canvas(hi);
            cv.scale(scale,scale);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|
                Paint.SUBPIXEL_TEXT_FLAG);
            if(layer.type==StudioDocument.Type.TEXT &&
                !StudioLayerEffects.requiresProcessing(layer)){
                p.setColor(layer.color);
                StudioTypography.draw(cv,layer,p);
            }else if((layer.type==StudioDocument.Type.RECTANGLE||
                layer.type==StudioDocument.Type.ELLIPSE)&&
                !StudioLayerEffects.requiresProcessing(layer)){
                p.setColor(layer.color);
                if(layer.type==StudioDocument.Type.RECTANGLE)
                    cv.drawRect(0,0,layer.objectWidth,layer.objectHeight,p);
                else cv.drawOval(0,0,layer.objectWidth,layer.objectHeight,p);
            }else{
                cv.drawBitmap(source,0,0,p);
            }
            work=shade(hi,style,border,scale);
            if(scale==1)return work;
            Bitmap light=resize(work.light,outputW,outputH,scale);
            Bitmap shade=resize(work.shade,outputW,outputH,scale);
            return new Layers(light,shade);
        }finally{
            if(work!=null&&scale!=1){
                work.light.recycle();work.shade.recycle();
            }
            hi.recycle();
        }
    }

    private static Bitmap resize(Bitmap source,int w,int h,float scale){
        int n=Math.round(scale);
        if(n>=2&&Math.abs(scale-n)<.001f&&
            source.getWidth()==w*n&&source.getHeight()==h*n)
            return areaDownsample(source,n,w,h);
        return Bitmap.createScaledBitmap(source,w,h,true);
    }

    /** Exact O(N) squared Euclidean distance transform on each axis. */
    private static void edtLine(float[] input,int offset,int step,int length,
                                float[] out,int outOffset,int outStep,
                                int[] sites,float[] boundaries){
        int top=-1;
        for(int q=0;q<length;q++){
            float value=input[offset+q*step];
            if(value>=INF*.5f)continue;
            float boundary=-INF;
            while(top>=0){
                int old=sites[top];
                float f=input[offset+old*step];
                boundary=(value+q*q-f-old*old)/(2f*(q-old));
                if(boundary>boundaries[top])break;
                top--;
            }
            if(top<0){top=0;sites[0]=q;boundaries[0]=-INF;}
            else{sites[++top]=q;boundaries[top]=boundary;}
            boundaries[top+1]=INF;
        }
        if(top<0){
            for(int q=0;q<length;q++)out[outOffset+q*outStep]=INF;
            return;
        }
        int idx=0;
        for(int q=0;q<length;q++){
            while(idx<top&&boundaries[idx+1]<q)idx++;
            int site=sites[idx];float delta=q-site;
            out[outOffset+q*outStep]=delta*delta+input[offset+site*step];
        }
    }

    private static float[] squaredDistance(int[] alpha,int width,int height,
                                            boolean foreground){
        int count=width*height,extent=Math.max(width,height);
        float[] src=new float[count],temp=new float[count],out=new float[count];
        int[] sites=new int[extent];
        float[] bounds=new float[extent+1];
        for(int i=0;i<count;i++)
            src[i]=(foreground?alpha[i]>=128:alpha[i]<128)?0:INF;
        for(int y=0;y<height;y++)
            edtLine(src,y*width,1,width,temp,y*width,1,sites,bounds);
        for(int x=0;x<width;x++)
            edtLine(temp,x,width,height,out,x,width,sites,bounds);
        return out;
    }

    private static float clamp(float f){return Math.max(0,Math.min(1,f));}
    private static float smooth(float f){f=clamp(f);return f*f*(3f-2f*f);}
    private static float profile(String technique,float ratio,float width){
        float q=clamp(ratio),pixel=1f/Math.max(1f,width);
        if("CHISEL_HARD".equals(technique)){
            // A flat plane with narrow, *continuous* rounded shoulders.
            float leading=smooth((q+.04f)/(Math.max(.035f,pixel*1.4f)));
            float trailing=1-smooth((q-.79f)/Math.max(.08f,pixel*1.5f));
            return leading*trailing;
        }
        if("CHISEL_SOFT".equals(technique)){
            // Rounded edges of a largely planar chisel face.
            return (float)Math.sqrt(1-smooth(q));
        }
        // Gently curved smooth profile, disappearing into the flat face.
        float c=(float)Math.cos(Math.PI*.5*q);
        return c*c;
    }
    private static float pixel(float[] field,int w,int h,int x,int y){
        return field[Math.max(0,Math.min(h-1,y))*w+
            Math.max(0,Math.min(w-1,x))];
    }

    private static Layers shade(Bitmap source,StudioLayerStyles.Style s,
                                int padding,float scale){
        int srcW=source.getWidth(),srcH=source.getHeight();
        int W=srcW+2*padding,H=srcH+2*padding,n=W*H;
        if(n>MAX_WORK_PIXELS+4096)
            throw new IllegalArgumentException("Relevo acima do limite de processamento");
        int[] rgba=new int[srcW*srcH],alpha=new int[n];
        source.getPixels(rgba,0,srcW,0,0,srcW,srcH);
        for(int y=0;y<srcH;y++)for(int x=0;x<srcW;x++)
            alpha[(y+padding)*W+x+padding]=Color.alpha(rgba[y*srcW+x]);
        float[] distanceToSolid=squaredDistance(alpha,W,H,true);
        float[] distanceToClear=squaredDistance(alpha,W,H,false);
        float[] sdf=new float[n];
        for(int i=0;i<n;i++){
            float coverage=alpha[i]/255f;
            float signed=coverage>=.5f?
                (float)Math.sqrt(distanceToClear[i])-.5f:
                .5f-(float)Math.sqrt(distanceToSolid[i]);
            sdf[i]=Float.isFinite(signed)?
                signed+(coverage-.5f)*.65f:0;
        }
        // 3x3 weighted low-pass on the SDF, NOT on the text's alpha.
        float[] smoothed=new float[n];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float sum=0;
            for(int sy=-1;sy<=1;sy++)for(int sx=-1;sx<=1;sx++){
                int weight=(sy==0?2:1)*(sx==0?2:1);
                sum+=pixel(sdf,W,H,x+sx,y+sy)*weight;
            }
            smoothed[y*W+x]=sum*.0625f;
        }

        int[] lights=new int[n],shadows=new int[n];
        float a=(float)Math.toRadians(s.angle);
        float lightX=(float)Math.cos(a),lightY=-(float)Math.sin(a);
        float altitude=(float)Math.toRadians(s.altitude);
        float lightVertical=(float)Math.sin(altitude);
        float lightHorizontal=(float)Math.cos(altitude);
        float width=Math.max(.8f,s.size*scale);
        float soften=s.soften*scale;
        float depth=Math.min(5,Math.max(0,s.depth/100f));
        boolean outer="OUTER_BEVEL".equals(s.bevelStyle);
        boolean emboss="EMBOSS".equals(s.bevelStyle);
        boolean pillow="PILLOW".equals(s.bevelStyle);
        int hiColor=s.highlight&0x00ffffff,shColor=s.shade&0x00ffffff;
        float hiAlpha=Color.alpha(s.highlight)/255f,shAlpha=Color.alpha(s.shade)/255f;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int i=y*W+x;
            float coverage=alpha[i]/255f;
            boolean outside=sdf[i]<0;
            if(outer&&!outside)continue;
            if(!outer&&!emboss&&!pillow&&outside)continue;
            float shapeCoverage=outside?1-coverage:coverage;
            if(shapeCoverage<.001f)continue;
            float d=Math.abs(sdf[i]);
            if(d>=width+Math.max(1f,soften))continue;
            float fraction=clamp(d/width);
            float slope=profile(s.technique,fraction,width);
            float feather=soften>0?
                1-smooth((d-width+soften)/Math.max(1f,2*soften)):
                1-smooth((d-width+.7f)/1.4f);
            slope*=clamp(feather);
            if(slope<.0001f)continue;
            float gx=(pixel(smoothed,W,H,x+1,y-1)+
                2*pixel(smoothed,W,H,x+1,y)+pixel(smoothed,W,H,x+1,y+1)-
                pixel(smoothed,W,H,x-1,y-1)-
                2*pixel(smoothed,W,H,x-1,y)-
                pixel(smoothed,W,H,x-1,y+1))*.125f;
            float gy=(pixel(smoothed,W,H,x-1,y+1)+
                2*pixel(smoothed,W,H,x,y+1)+pixel(smoothed,W,H,x+1,y+1)-
                pixel(smoothed,W,H,x-1,y-1)-
                2*pixel(smoothed,W,H,x,y-1)-
                pixel(smoothed,W,H,x+1,y-1))*.125f;
            float length=(float)Math.hypot(gx,gy);
            float radial=(gx*lightX+gy*lightY)/Math.max(.15f,length);
            if(outside)radial=-radial;
            if("DOWN".equals(s.direction))radial=-radial;
            if(pillow)radial=-radial;

            // Shading from the tilt of the surface relative to its flat
            // interior. No gray layer at grazing angles: neutral is alpha 0.
            float tilt=depth*slope;
            float normalZ=1f/(float)Math.sqrt(1f+tilt*tilt);
            float illumination=lightVertical*normalZ+
                radial*lightHorizontal*tilt*normalZ;
            float delta=illumination-lightVertical;
            float amount=clamp(Math.abs(delta)*2.6f);
            if(amount<.0004f)continue;
            // Gloss contours adjust contrast, without painting neutral areas.
            float gloss=StudioLayerStyles.curve(s,amount);
            amount*=.35f+.65f*clamp(gloss);
            amount=smooth(amount);
            float edge=shapeCoverage*amount;
            if(delta>0){
                int opacity=Math.round(255*edge*hiAlpha);
                if(opacity>0)lights[i]=(opacity<<24)|hiColor;
            }else{
                int opacity=Math.round(255*edge*shAlpha);
                if(opacity>0)shadows[i]=(opacity<<24)|shColor;
            }
        }
        Bitmap light=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        Bitmap shade=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        light.setPixels(lights,0,W,0,0,W,H);
        shade.setPixels(shadows,0,W,0,0,W,H);
        return new Layers(light,shade);
    }

    /** Pixel footprint averaging in premultiplied color / alpha. */
    private static Bitmap areaDownsample(Bitmap bitmap,int factor,int w,int h){
        int W=bitmap.getWidth();
        int[] src=new int[W*bitmap.getHeight()],out=new int[w*h];
        bitmap.getPixels(src,0,W,0,0,W,bitmap.getHeight());
        int count=factor*factor;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            long a=0,r=0,g=0,b=0;
            for(int iy=0;iy<factor;iy++)for(int ix=0;ix<factor;ix++){
                int c=src[(y*factor+iy)*W+x*factor+ix],ca=Color.alpha(c);
                a+=ca;r+=(long)ca*Color.red(c);
                g+=(long)ca*Color.green(c);
                b+=(long)ca*Color.blue(c);
            }
            if(a==0)continue;
            out[y*w+x]=Color.argb((int)((a+count/2)/count),
                (int)(r/a),(int)(g/a),(int)(b/a));
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        result.setPixels(out,0,w,0,0,w,h);return result;
    }
}

package com.discnet.noise.advancedstudio;

import android.graphics.*;

/**
 * Independent, source-compatible Bevel & Emboss renderer.
 *
 * A SINGLE supersampled raster is used for the editable glyph and its relief.
 * The old implementation drew low-resolution text, generated a second outline
 * from differently rasterized text and placed mixed black/white translucent
 * pixels over it. Those two silhouettes produced ghost edges and hard stripes.
 *
 * This implementation derives a signed Euclidean distance field from the same
 * alpha coverage that is finally displayed. Lighting is applied in two
 * independent stages (Screen highlight, Multiply shadow) to the source RGB,
 * preserving the alpha of an inner bevel EXACTLY.
 */
public final class StudioBevelRenderer {
    private static final float INF=1.0e12f;
    private static final int MAX_WORK_PIXELS=900_000;
    private StudioBevelRenderer(){}

    static Bitmap compose(StudioDocument.Layer layer,Bitmap source,
                          StudioLayerStyles.Style style,int padding){
        final int width=source.getWidth()+2*padding;
        final int height=source.getHeight()+2*padding;
        // No illumination must be a strict identity transform, including RGB
        // and the premultiplied edge pixels. Do not rerasterize glyphs just to
        // render a fully transparent lighting pass.
        if(style.opacity<=0||
           (style.highlightOpacity<=0&&style.shadowOpacity<=0)){
            Bitmap untouched=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            new Canvas(untouched).drawBitmap(source,padding,padding,
                new Paint(Paint.FILTER_BITMAP_FLAG));
            return untouched;
        }
        long outputPixels=(long)width*height;
        float factor=1f;
        if(outputPixels>MAX_WORK_PIXELS){
            factor=(float)Math.sqrt(MAX_WORK_PIXELS/(double)outputPixels)*.96f;
        }else{
            for(int f=3;f>=2;f--)if(outputPixels*f*f<=MAX_WORK_PIXELS){
                factor=f;break;
            }
        }
        int w=Math.max(1,Math.round(source.getWidth()*factor));
        int h=Math.max(1,Math.round(source.getHeight()*factor));
        int pad=Math.max(0,Math.round(padding*factor));
        Bitmap base=null,styled=null;
        try{
            base=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(base);
            c.scale(factor,factor);
            Paint text=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|
                Paint.SUBPIXEL_TEXT_FLAG);
            boolean parametric=layer.type==StudioDocument.Type.TEXT||
                layer.type==StudioDocument.Type.RECTANGLE||
                layer.type==StudioDocument.Type.ELLIPSE;
            if(parametric&&!StudioLayerEffects.requiresProcessing(layer)){
                text.setColor(layer.color);
                if(layer.type==StudioDocument.Type.TEXT){
                    StudioTypography.draw(c,layer,text);
                }else if(layer.type==StudioDocument.Type.RECTANGLE){
                    c.drawRect(0,0,layer.objectWidth,layer.objectHeight,text);
                }else{
                    c.drawOval(0,0,layer.objectWidth,layer.objectHeight,text);
                }
            }else{
                c.drawBitmap(source,0,0,text);
            }
            int[] src=new int[w*h];
            base.getPixels(src,0,w,0,0,w,h);
            styled=shade(src,w,h,pad,style,factor);
            Bitmap output;
            if(styled.getWidth()==width&&styled.getHeight()==height){
                output=styled;
            }else{
                output=resizePremultiplied(styled,width,height);
            }
            // For inner bevel, match the exact pixel coverage of the regular
            // source glyph. Different Android glyph hinting at 1x / 3x
            // must never become phantom outlines outside the source alpha.
            if("INNER_BEVEL".equals(style.bevelStyle)){
                maskToOriginalSource(output,source,padding);
            }
            return output;
        }finally{
            if(base!=null&&!base.isRecycled())base.recycle();
            if(styled!=null&&styled.getWidth()!=width&&
                !styled.isRecycled())styled.recycle();
        }
    }

    // Exact O(n) Euclidean distance transform. INF sites are omitted to
    // prevent overflow and NaNs when a mask is fully opaque or fully empty.
    private static void edt(float[] input,int start,int stride,int length,
                            float[] output,int outStart,int outStride,
                            int[] sites,float[] transitions){
        int tail=-1;
        for(int q=0;q<length;q++){
            float cost=input[start+q*stride];
            if(cost>=INF*.5f)continue;
            float border=-INF;
            while(tail>=0){
                int previous=sites[tail];
                float prevCost=input[start+previous*stride];
                border=((cost+q*q)-(prevCost+previous*previous))/
                    (2f*(q-previous));
                if(border>transitions[tail])break;
                tail--;
            }
            if(tail<0){tail=0;sites[0]=q;transitions[0]=-INF;}
            else{sites[++tail]=q;transitions[tail]=border;}
            transitions[tail+1]=INF;
        }
        if(tail<0){
            for(int q=0;q<length;q++)output[outStart+q*outStride]=INF;
            return;
        }
        int k=0;
        for(int q=0;q<length;q++){
            while(k<tail&&transitions[k+1]<q)k++;
            int delta=q-sites[k];
            output[outStart+q*outStride]=
                delta*delta+input[start+sites[k]*stride];
        }
    }
    private static float[] distances(int[] a,int w,int h,boolean toOpaque){
        int n=w*h,max=Math.max(w,h);
        float[] first=new float[n],horizontal=new float[n],result=new float[n];
        int[] sites=new int[max];float[] transitions=new float[max+1];
        for(int i=0;i<n;i++)
            first[i]=(toOpaque?a[i]>=128:a[i]<128)?0:INF;
        for(int y=0;y<h;y++)
            edt(first,y*w,1,w,horizontal,y*w,1,sites,transitions);
        first=null;
        for(int x=0;x<w;x++)
            edt(horizontal,x,w,h,result,x,w,sites,transitions);
        return result;
    }
    private static float clamp(float value){
        return Math.max(0,Math.min(1,value));
    }
    private static float smooth(float x){
        x=clamp(x);return x*x*(3-2*x);
    }
    private static float at(float[] f,int w,int h,int x,int y){
        return f[Math.max(0,Math.min(h-1,y))*w+
            Math.max(0,Math.min(w-1,x))];
    }
    private static int blend(int base,int tint,float amount,String mode){
        amount=clamp(amount);
        if(amount<=0)return base;
        int r=channel(Color.red(base),Color.red(tint),amount,mode);
        int g=channel(Color.green(base),Color.green(tint),amount,mode);
        int b=channel(Color.blue(base),Color.blue(tint),amount,mode);
        return Color.rgb(r,g,b);
    }
    private static int channel(int v,int tint,float amount,String mode){
        float a=v/255f,b=tint/255f,mixed;
        switch(mode){
            case "MULTIPLY":mixed=a*b;break;
            case "SCREEN":mixed=1-(1-a)*(1-b);break;
            case "OVERLAY":mixed=a<.5f?2*a*b:1-2*(1-a)*(1-b);break;
            case "ADD":mixed=Math.min(1,a+b);break;
            case "NORMAL":default:mixed=b;break;
        }
        return Math.max(0,Math.min(255,Math.round(255*(a+(mixed-a)*amount))));
    }
    private static Bitmap shade(int[] source,int w,int h,int pad,
                                StudioLayerStyles.Style s,float factor){
        int W=w+2*pad,H=h+2*pad,n=W*H;
        int[] base=new int[n],alpha=new int[n];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=source[y*w+x],index=(y+pad)*W+x+pad;
            base[index]=p;alpha[index]=Color.alpha(p);
        }
        int[] output=new int[n];
        // No opaque alpha: there is no outline to shade.
        boolean any=false;
        for(int a:alpha)if(a>0){any=true;break;}
        if(!any){
            Bitmap empty=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
            return empty;
        }
        float[] towardOpaque=distances(alpha,W,H,true);
        float[] towardTransparent=distances(alpha,W,H,false);
        float[] signed=new float[n];
        for(int i=0;i<n;i++){
            float coverage=alpha[i]/255f;
            float d=coverage>=.5f?
                (float)Math.sqrt(towardTransparent[i])-.5f:
                .5f-(float)Math.sqrt(towardOpaque[i]);
            if(!Float.isFinite(d))d=0;
            signed[i]=d+(coverage-.5f)*.5f;
        }
        towardOpaque=null;towardTransparent=null;
        // A smoothly varying distance derivative is essential around the
        // inner counter of 'a', the bowl of 'u' and diagonals of 'k'.
        float[] normalField=new float[n];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float sum=0;
            for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++)
                sum+=at(signed,W,H,x+xx,y+yy)*
                    (xx==0?2:1)*(yy==0?2:1);
            normalField[y*W+x]=sum/16f;
        }
        float size=Math.max(.8f,s.size*factor),soften=s.soften*factor;
        float width=Math.max(.8f,size+Math.min(size*.5f,soften));
        float angle=(float)Math.toRadians(s.angle);
        float lightX=(float)Math.cos(angle),lightY=-(float)Math.sin(angle);
        float altitude=(float)Math.toRadians(s.altitude);
        float sun=(float)Math.cos(altitude),z=(float)Math.sin(altitude);
        boolean outer="OUTER_BEVEL".equals(s.bevelStyle);
        boolean both="EMBOSS".equals(s.bevelStyle)||
            "PILLOW".equals(s.bevelStyle);
        boolean hard="CHISEL_HARD".equals(s.technique);
        boolean chisel="CHISEL_SOFT".equals(s.technique);
        float depth=(float)Math.sqrt(Math.min(10,s.depth/100f));
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int i=y*W+x,original=base[i],a=alpha[i];
            boolean outside=signed[i]<0;
            if((outer&&!outside)||(!outer&&!both&&outside)){
                output[i]=original;continue;
            }
            if(a==0&&!outside){output[i]=original;continue;}
            float edge=Math.abs(signed[i]);
            if(edge>width+factor){output[i]=original;continue;}
            float q=clamp(edge/width);
            // Height slope, not binary opacity, distinguishes techniques:
            // smooth = round surface; chisel hard = planar wall and crisp ridge;
            // chisel soft = softly rounded planar wall.
            float slope;
            if(hard){
                float aa=Math.max(.07f,1f/width);
                slope=smooth((.96f-q)/aa)*
                    (1-.38f*smooth((q-.58f)/Math.max(.13f,aa)));
            }else if(chisel){
                slope=(float)Math.pow(1-q,.54f);
            }else{
                slope=(float)Math.sin(Math.PI*q);
                slope=Math.max(0,slope);
            }
            if(slope<.0001f){output[i]=original;continue;}
            float gx=(at(normalField,W,H,x+1,y-1)+
                    2*at(normalField,W,H,x+1,y)+
                    at(normalField,W,H,x+1,y+1)-
                    at(normalField,W,H,x-1,y-1)-
                    2*at(normalField,W,H,x-1,y)-
                    at(normalField,W,H,x-1,y+1))*.125f;
            float gy=(at(normalField,W,H,x-1,y+1)+
                    2*at(normalField,W,H,x,y+1)+
                    at(normalField,W,H,x+1,y+1)-
                    at(normalField,W,H,x-1,y-1)-
                    2*at(normalField,W,H,x,y-1)-
                    at(normalField,W,H,x+1,y-1))*.125f;
            float magnitude=(float)Math.hypot(gx,gy);
            float normal=(gx*lightX+gy*lightY)/Math.max(.18f,magnitude);
            if(outside)normal=-normal;
            if("DOWN".equals(s.direction))normal=-normal;
            if("PILLOW".equals(s.bevelStyle))normal=-normal;
            // Neutral light directions remain unchanged. No gray wash.
            float reflect=normal*depth*slope;
            float lit=Math.max(0,reflect);
            float dark=Math.max(0,-reflect);
            // Gloss Contour shapes lighting, not source alpha or geometry.
            lit=lit>0?StudioLayerStyles.curve(s,clamp(lit)):0;
            dark=dark>0?StudioLayerStyles.curve(s,clamp(dark)):0;
            float boost=.45f+.55f*sun;
            lit=clamp(lit*boost);
            dark=clamp(dark*boost);
            // Independent Screen and Multiply follow conventional Layer Style
            // highlight/shadow processing rather than blended gray brushes.
            float opacity=clamp(s.opacity);
            float hStrength=lit*clamp(s.highlightOpacity)*opacity*
                (Color.alpha(s.highlight)/255f);
            float sStrength=dark*clamp(s.shadowOpacity)*opacity*
                (Color.alpha(s.shade)/255f);
            if(outside){
                if(!outer&&!both){output[i]=original;continue;}
                float feather=clamp((width-edge)/Math.max(1f,factor));
                float coverage=(1-a/255f)*feather;
                int tint=lit>=dark?s.highlight:s.shade;
                float intensity=coverage*Math.max(hStrength,sStrength);
                int color=(Math.round(255*clamp(intensity))<<24)|(tint&0xffffff);
                output[i]=color;
            }else{
                int color=original;
                color=(color&0xff000000)|(
                    blend(color,s.highlight,hStrength,s.highlightBlend)&0xffffff);
                color=(color&0xff000000)|(
                    blend(color,s.shade,sStrength,s.shadowBlend)&0xffffff);
                // Keep the exact antialiased alpha of the original source.
                output[i]=(a<<24)|(color&0xffffff);
            }
        }
        Bitmap bitmap=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        bitmap.setPixels(output,0,W,0,0,W,H);
        return bitmap;
    }

    private static void maskToOriginalSource(Bitmap output,Bitmap source,int padding){
        int w=output.getWidth(),h=output.getHeight(),sw=source.getWidth(),
            sh=source.getHeight();
        int[] src=new int[sw*sh],out=new int[w*h];
        source.getPixels(src,0,sw,0,0,sw,sh);
        output.getPixels(out,0,w,0,0,w,h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            int sx=x-padding,sy=y-padding;
            if(sx<0||sy<0||sx>=sw||sy>=sh){
                out[i]=Color.TRANSPARENT;continue;
            }
            int a=Color.alpha(src[sy*sw+sx]);
            out[i]=a==0?Color.TRANSPARENT:(out[i]&0xffffff)|(a<<24);
        }
        output.setPixels(out,0,w,0,0,w,h);
    }

    /** Premultiplied-alpha area resampling prevents white fringes on glyphs. */
    private static Bitmap resizePremultiplied(Bitmap src,int width,int height){
        int w=src.getWidth(),h=src.getHeight();
        // Android draws opaque and alpha source through premultiplied filtering;
        // this resize happens after both source and lighting were composed.
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src,new Rect(0,0,w,h),
            new Rect(0,0,width,height),p);
        return bitmap;
    }
}

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
        return compose(layer,source,style,padding,null);
    }

    /** Colour/pattern/gradient overlays are intrinsic layer fill: they go
     * UNDER the bevel, never over its directional highlights or shadows.
     * They are painted into the SAME supersampled glyph mask as the bevel.
     */
    static Bitmap compose(StudioDocument.Layer layer,Bitmap source,
                          StudioLayerStyles.Style style,int padding,
                          java.util.List<StudioLayerStyles.Style> overlays){
        final int width=source.getWidth()+2*padding;
        final int height=source.getHeight()+2*padding;
        // No illumination must be a strict identity transform, including RGB
        // and the premultiplied edge pixels. Do not rerasterize glyphs just to
        // render a fully transparent lighting pass.
        if((overlays==null||overlays.isEmpty())&&
            (style.opacity<=0||
             (style.highlightOpacity<=0&&style.shadowOpacity<=0))){
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
            if(overlays!=null&&!overlays.isEmpty())
                applyFillOverlays(src,w,h,factor,overlays);
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

    /** Non-destructive color application, preserving the glyph coverage.
     * The per-style blend and opacity are evaluated on unassociated RGB; the
     * bevel then shades that RGB while retaining the ORIGINAL alpha mask.
     */
    private static void applyFillOverlays(int[] pixels,int w,int h,float factor,
                    java.util.List<StudioLayerStyles.Style> overlays){
        StudioLayerStyles.Kind[] order={
            StudioLayerStyles.Kind.PATTERN_OVERLAY,
            StudioLayerStyles.Kind.GRADIENT_OVERLAY,
            StudioLayerStyles.Kind.COLOR_OVERLAY
        };
        for(StudioLayerStyles.Kind kind:order){
            for(StudioLayerStyles.Style effect:overlays){
                if(!effect.enabled||effect.kind!=kind||effect.opacity<=0)continue;
                float theta=(float)Math.toRadians(effect.angle);
                float cos=(float)Math.cos(theta),sin=-(float)Math.sin(theta);
                float max=Math.max(w,h);
                float unit=1f/Math.max(.01f,factor);
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                    int i=y*w+x,original=pixels[i];
                    int a=Color.alpha(original);
                    if(a==0)continue;
                    int color=effect.color;
                    float px=(x+.5f-w*.5f)*unit;
                    float py=(y+.5f-h*.5f)*unit;
                    if(kind==StudioLayerStyles.Kind.GRADIENT_OVERLAY){
                        float scaled=Math.max(.001f,max*unit*
                            effect.gradientScale/100f);
                        float t=clamp((px*cos+py*sin)/scaled+.5f);
                        if(effect.reverse)t=1-t;
                        color=mixRgb(effect.color,effect.color2,t);
                    }else if(kind==StudioLayerStyles.Kind.PATTERN_OVERLAY){
                        float sx=(px*cos+py*sin)/
                            Math.max(2f,12f*effect.gradientScale/100f);
                        float sy=(-px*sin+py*cos)/
                            Math.max(2f,12f*effect.gradientScale/100f);
                        int ix=(int)Math.floor(sx),iy=(int)Math.floor(sy);
                        if("DOTS".equals(effect.pattern)){
                            float cx=sx-ix-.5f,cy=sy-iy-.5f;
                            color=cx*cx+cy*cy<.14f?effect.color:effect.color2;
                        }else if("STRIPES".equals(effect.pattern)){
                            color=(ix&1)==0?effect.color:effect.color2;
                        }else{
                            color=((ix+iy)&1)==0?effect.color:effect.color2;
                        }
                    }
                    float amount=clamp(effect.opacity)*
                        Color.alpha(color)/255f;
                    int recolored=StudioBlendMath.composite(
                        StudioBlendMath.of(effect.blend),
                        0xff000000|(original&0xffffff),
                        0xff000000|(color&0xffffff),amount,0,0);
                    pixels[i]=(a<<24)|(recolored&0xffffff);
                }
            }
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
    private static int mixRgb(int a,int b,float weight){
        float t=clamp(weight),u=1-t;
        return Color.rgb(
            Math.round(Color.red(a)*u+Color.red(b)*t),
            Math.round(Color.green(a)*u+Color.green(b)*t),
            Math.round(Color.blue(a)*u+Color.blue(b)*t));
    }
    private static int blend(int base,int tint,float amount,String mode){
        // Highlights and shadows use their own independent blend modes,
        // including Color Dodge/Burn, Soft/Hard Light and Hue family.
        return StudioBlendMath.composite(StudioBlendMath.of(mode),
            0xff000000|(base&0xffffff),
            0xff000000|(tint&0xffffff),amount,0,0);
    }
    /**
     * Layer-style geometry following the PhotoCraft reference design:
     * SMOOTH uses a two-box/tent-filtered alpha height map;
     * CHISEL_HARD and CHISEL_SOFT use signed Euclidean distance ramps.
     * The gradient of HEIGHT, not the gradient of the alpha outline,
     * determines the reflected light. Each pixel has a neutral baseline.
     *
     * Inspired by PhotoCraft's crates/compose/src/effects.rs, licensed MIT
     * or Apache-2.0, rewritten independently for Android/ARGB memory limits.
     */
    private static float[] boxPass(float[] data,int w,int h,int radius,boolean horizontal){
        if(radius<=0)return data.clone();
        float[] out=new float[data.length];
        final float normalization=1f/(2*radius+1);
        if(horizontal){
            for(int y=0;y<h;y++){
                int offset=y*w;double sum=0;
                for(int k=-radius;k<=radius;k++)
                    if(k>=0&&k<w)sum+=data[offset+k];
                for(int x=0;x<w;x++){
                    out[offset+x]=(float)(sum*normalization);
                    int entering=x+radius+1,leaving=x-radius;
                    if(entering<w)sum+=data[offset+entering];
                    if(leaving>=0)sum-=data[offset+leaving];
                }
            }
        }else{
            for(int x=0;x<w;x++){
                double sum=0;
                for(int k=-radius;k<=radius;k++)
                    if(k>=0&&k<h)sum+=data[k*w+x];
                for(int y=0;y<h;y++){
                    out[y*w+x]=(float)(sum*normalization);
                    int entering=y+radius+1,leaving=y-radius;
                    if(entering<h)sum+=data[entering*w+x];
                    if(leaving>=0)sum-=data[leaving*w+x];
                }
            }
        }
        return out;
    }
    private static float[] tent(float[] coverage,int w,int h,float width){
        // A pair of box filters on each axis is a tent kernel. For an
        // integer Photoshop-style size of five the tent radius is four.
        int radius=Math.min(128,Math.max(0,Math.round((width-1f)*.5f)));
        if(radius==0)return coverage.clone();
        float[] t=boxPass(coverage,w,h,radius,true);
        t=boxPass(t,w,h,radius,true);
        t=boxPass(t,w,h,radius,false);
        return boxPass(t,w,h,radius,false);
    }
    private static Bitmap shade(int[] source,int w,int h,int pad,
                                StudioLayerStyles.Style s,float factor){
        int W=w+2*pad,H=h+2*pad,n=W*H;
        int[] base=new int[n],alpha=new int[n];
        float[] coverage=new float[n];
        boolean any=false;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int pixel=source[y*w+x],index=(y+pad)*W+x+pad;
            base[index]=pixel;
            int a=Color.alpha(pixel);
            alpha[index]=a;
            coverage[index]=a/255f;
            if(a>0)any=true;
        }
        if(!any)return Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);

        float size=Math.max(1f,s.size*factor);
        boolean hard="CHISEL_HARD".equals(s.technique);
        boolean soft="CHISEL_SOFT".equals(s.technique);
        boolean smooth=!hard&&!soft;
        boolean outer="OUTER_BEVEL".equals(s.bevelStyle);
        boolean both="EMBOSS".equals(s.bevelStyle)||
                     "PILLOW".equals(s.bevelStyle);
        boolean pillow="PILLOW".equals(s.bevelStyle);
        float width=both?(float)Math.ceil(size*.5f):size;
        float depth=s.depth/100f*(smooth?width:size*.5f);
        if("DOWN".equals(s.direction))depth=-depth;

        float[] height;
        if(smooth){
            height=tent(coverage,W,H,width);
        }else{
            float[] opaque=distances(alpha,W,H,true);
            float[] empty=distances(alpha,W,H,false);
            height=new float[n];
            float ramp=size+1f;
            for(int i=0;i<n;i++){
                float cov=coverage[i];
                // Fractional alpha adjusts the EDT edge to subpixel
                // glyph coverage, so diagonals remain smooth.
                float signed=cov>=.5f?
                    (float)Math.sqrt(empty[i])-.5f:
                    .5f-(float)Math.sqrt(opaque[i]);
                signed+=(cov-.5f)*.5f;
                float inside=Math.max(0,signed);
                float outside=Math.max(0,-signed);
                if(both){
                    float half=size*.5f+.5f;
                    height[i]=clamp(.5f+signed/(2f*half));
                }else if(outer){
                    height[i]=outside>0?
                        clamp(1f-(outside+(soft?.5f:-.5f))/ramp):1f;
                }else{
                    // Hard stays a planar facet; Soft differs in the
                    // anti-aliased first edge pixel, as in PhotoCraft.
                    height[i]=signed>=0?
                        clamp((inside+(soft?1.5f:.5f))/ramp):0f;
                }
            }
            if(soft&&both)height=tent(height,W,H,Math.max(1f,size*.25f));
        }
        if(s.soften>.5f)
            height=tent(height,W,H,Math.max(1f,s.soften*factor));
        // Small-font bevel normals benefit from one gentle spatial averaging
        // pass. The user-requested sharp modes still preserve their facets.
        if(smooth&&width>1.5f)
            height=boxPass(boxPass(height,W,H,1,true),W,H,1,false);

        float angle=(float)Math.toRadians(s.angle);
        float alt=(float)Math.toRadians(s.altitude);
        float se=(float)Math.sin(alt),ce=(float)Math.cos(alt);
        float lx=(float)Math.cos(angle)*ce,ly=-(float)Math.sin(angle)*ce;
        int[] out=new int[n];
        float opacity=clamp(s.opacity);
        final int hiColor=s.highlight,shadowColor=s.shade;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int i=y*W+x,pixel=base[i],a=alpha[i];
            float cov=coverage[i];
            boolean shadeInside=!outer&&cov>.001f;
            boolean shadeOutside=(outer||both)&&cov<.999f;
            if(!shadeInside&&!shadeOutside){out[i]=pixel;continue;}
            float gx=(at(height,W,H,x+1,y)-at(height,W,H,x-1,y))*
                .5f*depth;
            float gy=(at(height,W,H,x,y+1)-at(height,W,H,x,y-1))*
                .5f*depth;
            float denom=(float)Math.sqrt(1+gx*gx+gy*gy);
            float innerDelta=(-gx*lx-gy*ly+se)/denom-se;

            // Bevel maps are split into interior and exterior masks. For
            // pillow emboss ONLY the exterior surface faces the opposite
            // direction, as in PhotoCraft's BevelPaint::Both.
            float outerDelta=pillow?
                (gx*lx+gy*ly+se)/denom-se:innerDelta;
            float hi=0,sh=0,ho=0,so=0;
            if(shadeInside){
                hi=innerDelta>0?clamp(innerDelta/Math.max(.001f,1-se)):0;
                sh=innerDelta<0?clamp(-innerDelta/Math.max(.001f,se)):0;
                if(hi>0)hi=StudioLayerStyles.curve(s,hi);
                if(sh>0)sh=StudioLayerStyles.curve(s,sh);
            }
            if(shadeOutside){
                ho=outerDelta>0?clamp(outerDelta/Math.max(.001f,1-se)):0;
                so=outerDelta<0?clamp(-outerDelta/Math.max(.001f,se)):0;
                if(ho>0)ho=StudioLayerStyles.curve(s,ho);
                if(so>0)so=StudioLayerStyles.curve(s,so);
            }
            float lightScale=opacity*clamp(s.highlightOpacity)*
                (Color.alpha(hiColor)/255f);
            float shadowScale=opacity*clamp(s.shadowOpacity)*
                (Color.alpha(shadowColor)/255f);
            int insideRgb=pixel&0xffffff;
            if(shadeInside){
                int lit=blend(pixel,hiColor,hi*lightScale,s.highlightBlend);
                int shaded=blend(lit,shadowColor,sh*shadowScale,s.shadowBlend);
                insideRgb=shaded&0xffffff;
            }
            float exteriorLight=ho*lightScale,exteriorShade=so*shadowScale;
            float exteriorAmount=Math.max(exteriorLight,exteriorShade);

            // Height map is nonzero only near the boundary. Use its smooth
            // support instead of an unconditional rectangular halo. The
            // exterior is computed independently even at partially covered
            // edge pixels (otherwise a 50% edge loses its outer half).
            float exteriorMatte=shadeOutside?
                smooth(clamp(height[i]*2.4f)):0f;
            float exteriorAlpha=clamp((1-cov)*exteriorAmount*exteriorMatte);
            int exteriorA=Math.round(255*exteriorAlpha);
            if(exteriorA==0){
                out[i]=a==0?Color.TRANSPARENT:(a<<24)|insideRgb;
                continue;
            }
            float sum=exteriorLight+exteriorShade;
            int outerRgb=sum<.0001f?0:
                (mixRgb(shadowColor,hiColor,exteriorLight/sum)&0xffffff);
            // SRC_OVER premultiplied-alpha combination preserves the true
            // edge shape, while extending emboss across both sides.
            int addedAlpha=exteriorA;
            int outAlpha=Math.min(255,a+addedAlpha);
            if(outAlpha==0){out[i]=0;continue;}
            int r=Math.round((Color.red(insideRgb)*a+
                Color.red(outerRgb)*addedAlpha)/(float)outAlpha);
            int g=Math.round((Color.green(insideRgb)*a+
                Color.green(outerRgb)*addedAlpha)/(float)outAlpha);
            int b=Math.round((Color.blue(insideRgb)*a+
                Color.blue(outerRgb)*addedAlpha)/(float)outAlpha);
            out[i]=Color.argb(outAlpha,r,g,b);
        }
        Bitmap result=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        result.setPixels(out,0,W,0,0,W,H);
        return result;
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

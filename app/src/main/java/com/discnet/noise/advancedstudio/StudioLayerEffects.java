package com.discnet.noise.advancedstudio;

import android.graphics.*;
import android.util.LruCache;
import org.json.*;
import java.util.*;

/**
 * Independent, serializable layer processing. Renders real pixels; originals
 * remain unchanged. The same entry point is used in preview and PNG export.
 */
public final class StudioLayerEffects {
    private StudioLayerEffects(){}
    public enum Kind { GAUSSIAN_BLUR, BRIGHTNESS, SATURATION }
    public static final int MAX_EFFECT_PIXELS=2_097_152;
    public static final class Effect {
        public Kind kind;
        public int value;
        public boolean enabled=true;
        public Effect(Kind kind,int value){this.kind=kind;this.value=value;}
        public Effect copy(){Effect e=new Effect(kind,value);e.enabled=enabled;return e;}
        public JSONObject toJson()throws JSONException{
            return new JSONObject().put("kind",kind.name())
                .put("value",value).put("enabled",enabled);
        }
        public static Effect fromJson(JSONObject j)throws Exception{
            Kind kind;
            try{kind=Kind.valueOf(j.getString("kind"));}
            catch(IllegalArgumentException e){throw new java.io.IOException("Efeito desconhecido",e);}
            Effect effect=new Effect(kind,j.getInt("value"));
            validate(effect);effect.enabled=j.optBoolean("enabled",true);return effect;
        }
    }
    public static void validate(Effect fx){
        int v=fx.value;
        boolean okay=(fx.kind==Kind.GAUSSIAN_BLUR?v>=1&&v<=8:v>=-100&&v<=100);
        if(!okay)throw new IllegalArgumentException("Parâmetro fora dos limites");
    }
    /** LRU prevents repeated CPU filters during pan/zoom and bounds cache RAM. */
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(16*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private static String key(StudioDocument.Layer l){
        StringBuilder b=new StringBuilder(l.id).append(':')
            .append(System.identityHashCode(l.bitmap)).append(':')
            .append(System.identityHashCode(l.mask)).append(':')
            .append(l.maskEnabled).append(':').append(l.maskDensity)
            .append(':').append(l.type).append(':').append(l.color)
            .append(':').append(l.fontSize).append(':').append(l.text)
            .append(':').append(l.objectWidth).append(':').append(l.objectHeight);
        for(Effect f:l.effects)b.append(':').append(f.kind).append(':')
            .append(f.value).append(':').append(f.enabled);
        return b.toString();
    }
    public static void invalidate(StudioDocument.Layer l){CACHE.remove(key(l));}
    public static boolean requiresProcessing(StudioDocument.Layer l){
        if(l.mask!=null&&l.maskEnabled)return true;
        for(Effect effect:l.effects)if(effect.enabled)return true;
        return false;
    }
    /** Converts parametric layers only for temporary compositing, never persistence. */
    public static Bitmap sourceBitmap(StudioDocument.Layer l){
        if(l.type==StudioDocument.Type.RASTER)return l.bitmap;
        int w=l.naturalWidth(),h=l.naturalHeight();
        if(w<1||h<1||(long)w*h>MAX_EFFECT_PIXELS)
            throw new IllegalArgumentException("Elemento grande demais para efeitos nesta fase");
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(l.color);
        if(l.type==StudioDocument.Type.TEXT){
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            p.setTextSize(l.fontSize);
            String[] lines=l.text.split("\n",-1);
            for(int i=0;i<lines.length;i++)
                c.drawText(lines[i],0,l.fontSize*(i+1)*1.16f,p);
        }else if(l.type==StudioDocument.Type.RECTANGLE){
            c.drawRect(0,0,l.objectWidth,l.objectHeight,p);
        }else if(l.type==StudioDocument.Type.ELLIPSE){
            c.drawOval(0,0,l.objectWidth,l.objectHeight,p);
        }
        return out;
    }
    public static Bitmap processed(StudioDocument.Layer l){
        Bitmap source=sourceBitmap(l);
        if(source==null)return null;
        if(!requiresProcessing(l))return source;
        long pixels=(long)source.getWidth()*source.getHeight();
        if(pixels>MAX_EFFECT_PIXELS)
            throw new IllegalArgumentException("Efeitos limitados a 2 MP nesta etapa");
        String k=key(l);
        Bitmap cached=CACHE.get(k);
        if(cached!=null&&!cached.isRecycled()){
            if(source!=l.bitmap)source.recycle();
            return cached;
        }
        Bitmap stage=source.copy(Bitmap.Config.ARGB_8888,true);
        if(source!=l.bitmap)source.recycle();
        for(Effect f:l.effects){
            if(!f.enabled)continue;
            validate(f);
            Bitmap result=apply(stage,f);
            stage.recycle();stage=result;
        }
        if(l.mask!=null&&l.maskEnabled){
            Bitmap masked=mask(stage,l.mask,l.maskDensity);
            stage.recycle();stage=masked;
        }
        CACHE.put(k,stage);return stage;
    }
    public static Bitmap apply(Bitmap src,Effect f){
        validate(f);
        if(f.kind==Kind.GAUSSIAN_BLUR)return gaussian(src,f.value);
        Bitmap out=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        ColorMatrix matrix=new ColorMatrix();
        if(f.kind==Kind.SATURATION)matrix.setSaturation(1+f.value/100f);
        else{
            float factor=1f+f.value/100f;
            matrix.set(new float[]{
                factor,0,0,0,0, 0,factor,0,0,0,
                0,0,factor,0,0, 0,0,0,1,0});
        }
        Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(matrix));
        c.drawBitmap(src,0,0,p);return out;
    }
    private static Bitmap gaussian(Bitmap input,int radius){
        int w=input.getWidth(),h=input.getHeight();
        if((long)w*h>MAX_EFFECT_PIXELS)
            throw new IllegalArgumentException("Filtro excede o limite de memória");
        int n=w*h;
        int[] data=new int[n],mid=new int[n],out=new int[n];
        input.getPixels(data,0,w,0,0,w,h);
        float sigma=Math.max(.8f,radius/2.4f),sum=0;
        float[] weights=new float[radius*2+1];
        for(int i=-radius;i<=radius;i++){
            float v=(float)Math.exp(-i*i/(2*sigma*sigma));
            weights[i+radius]=v;sum+=v;
        }
        for(int i=0;i<weights.length;i++)weights[i]/=sum;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double a=0,pr=0,pg=0,pb=0;
            for(int k=-radius;k<=radius;k++){
                int color=data[y*w+Math.max(0,Math.min(w-1,x+k))];
                double wa=weights[k+radius]*Color.alpha(color);
                a+=wa;pr+=wa*Color.red(color);pg+=wa*Color.green(color);
                pb+=wa*Color.blue(color);
            }
            mid[y*w+x]=argb(a,pr,pg,pb);
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double a=0,pr=0,pg=0,pb=0;
            for(int k=-radius;k<=radius;k++){
                int color=mid[Math.max(0,Math.min(h-1,y+k))*w+x];
                double wa=weights[k+radius]*Color.alpha(color);
                a+=wa;pr+=wa*Color.red(color);pg+=wa*Color.green(color);
                pb+=wa*Color.blue(color);
            }
            out[y*w+x]=argb(a,pr,pg,pb);
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        result.setPixels(out,0,w,0,0,w,h);
        return result;
    }
    private static int argb(double a,double r,double g,double b){
        int alpha=Math.max(0,Math.min(255,(int)Math.round(a)));
        if(a<.001)return 0;
        return Color.argb(alpha,channel(r/a),channel(g/a),channel(b/a));
    }
    private static int channel(double value){
        return Math.max(0,Math.min(255,(int)Math.round(value)));
    }
    public static Bitmap mask(Bitmap source,Bitmap mask,int density){
        if(source.getWidth()!=mask.getWidth()||source.getHeight()!=mask.getHeight())
            throw new IllegalArgumentException("Máscara e camada têm tamanhos diferentes");
        int w=source.getWidth(),h=source.getHeight(),n=w*h;
        int[] pix=new int[n],gray=new int[n];
        source.getPixels(pix,0,w,0,0,w,h);
        mask.getPixels(gray,0,w,0,0,w,h);
        float strength=Math.max(0,Math.min(100,density))/100f;
        for(int i=0;i<n;i++){
            int g=Color.red(gray[i]);
            float reveal=1f-strength+strength*g/255f;
            int a=Math.round(Color.alpha(pix[i])*reveal);
            pix[i]=(pix[i]&0x00ffffff)|(a<<24);
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        out.setPixels(pix,0,w,0,0,w,h);return out;
    }
    public static void draw(Canvas c,StudioDocument.Layer l,Paint p){
        Bitmap filtered=processed(l);
        if(filtered==null)return;
        if(l.shadowEnabled){
            Paint blur=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            blur.setMaskFilter(new BlurMaskFilter(Math.max(1,l.shadowRadius),
                BlurMaskFilter.Blur.NORMAL));
            int[] offsets=new int[2];
            Bitmap shadow=filtered.extractAlpha(blur,offsets);
            Paint shadowPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            shadowPaint.setColor(l.shadowColor);
            shadowPaint.setAlpha(Math.round(Color.alpha(l.shadowColor)*
                Math.max(0,Math.min(1,l.shadowOpacity))*l.opacity));
            c.drawBitmap(shadow,l.shadowX+offsets[0],
                l.shadowY+offsets[1],shadowPaint);
            shadow.recycle();
        }
        c.drawBitmap(filtered,0,0,p);
        if(l.type!=StudioDocument.Type.RASTER&&!requiresProcessing(l))
            filtered.recycle();
    }
    public static void createMask(StudioDocument doc,boolean white){
        StudioDocument.Layer layer=doc.active();
        if(layer==null||layer.locked)throw new IllegalStateException("Camada bloqueada");
        int w=layer.naturalWidth(),h=layer.naturalHeight();
        if(w<1||h<1||(long)w*h>MAX_EFFECT_PIXELS)
            throw new IllegalStateException("Máscara limitada a 2 MP nesta etapa");
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(white?Color.WHITE:Color.BLACK);
        doc.checkpoint();layer.mask=bitmap;layer.maskEnabled=true;layer.maskDensity=100;
    }
    public static void invertMask(StudioDocument doc){
        StudioDocument.Layer layer=doc.active();
        if(layer==null||layer.mask==null||layer.locked)
            throw new IllegalStateException("Máscara indisponível");
        Bitmap src=layer.mask;
        int w=src.getWidth(),h=src.getHeight();
        int[] px=new int[w*h];src.getPixels(px,0,w,0,0,w,h);
        for(int i=0;i<px.length;i++){int gray=255-Color.red(px[i]);
            px[i]=Color.rgb(gray,gray,gray);}
        Bitmap inverse=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        inverse.setPixels(px,0,w,0,0,w,h);
        doc.checkpoint();layer.mask=inverse;
    }
    public static final class MaskStroke {
        final Canvas canvas;
        final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        final StudioDocument.Layer layer;
        final StudioDocument doc;
        float previousX,previousY;
        MaskStroke(StudioDocument doc,StudioDocument.Layer l,Bitmap copy,int gray,float width){
            this.doc=doc;layer=l;canvas=new Canvas(copy);
            pen.setColor(gray>=128?Color.WHITE:Color.BLACK);
            pen.setStrokeWidth(width);pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);pen.setStyle(Paint.Style.STROKE);
        }
        private void clip(){
            if(doc.selection!=null)
                canvas.clipRect(doc.selection.left-layer.x,doc.selection.top-layer.y,
                    doc.selection.right-layer.x,doc.selection.bottom-layer.y);
        }
        public void start(float x,float y){
            previousX=x;previousY=y;canvas.save();clip();
            pen.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x,y,pen.getStrokeWidth()/2,pen);
            pen.setStyle(Paint.Style.STROKE);canvas.restore();
            invalidate(layer);
        }
        public void move(float x,float y){
            canvas.save();clip();canvas.drawLine(previousX,previousY,x,y,pen);
            canvas.restore();previousX=x;previousY=y;
            invalidate(layer);
        }
    }
    public static MaskStroke beginMaskStroke(StudioDocument doc,float x,float y,
                                             boolean reveal,float size){
        StudioDocument.Layer l=doc.active();
        if(l==null||l.locked||l.mask==null)
            throw new IllegalStateException("Selecione uma camada com máscara");
        if(l.scaleX!=1f||l.scaleY!=1f||l.rotation!=0)
            throw new IllegalStateException("Edite a máscara antes de transformar");
        Bitmap copy=l.mask.copy(Bitmap.Config.ARGB_8888,true);
        if(copy==null)throw new IllegalStateException("Memória insuficiente");
        doc.checkpoint();l.mask=copy;
        MaskStroke stroke=new MaskStroke(doc,l,copy,reveal?255:0,size);
        stroke.start(x,y);return stroke;
    }
}

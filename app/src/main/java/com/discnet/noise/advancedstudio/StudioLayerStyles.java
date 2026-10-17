package com.discnet.noise.advancedstudio;

import android.graphics.*;
import android.os.Build;
import android.util.LruCache;
import org.json.*;
import java.util.*;

/**
 * Non-destructive, alpha-derived layer styles. Renders identically in the canvas and PNG export.
 * CPU implementation is bounded to 2 MP; original layer pixels are never changed.
 */
public final class StudioLayerStyles {
    private StudioLayerStyles(){}
    public static final int MAX_PIXELS=2_097_152, MAX_STYLES=10;
    public enum Kind {
        BEVEL_EMBOSS("Bisel e relevo"),DROP_SHADOW("Sombra projetada"),
        INNER_SHADOW("Sombra interna"),OUTER_GLOW("Brilho externo"),
        INNER_GLOW("Brilho interno"),STROKE("Contorno"),
        COLOR_OVERLAY("Sobreposição de cor"),GRADIENT_OVERLAY("Sobreposição de degradê");
        public final String title;
        Kind(String title){this.title=title;}
    }
    public static final class Style {
        public final Kind kind;
        public boolean enabled=true,reverse=false,sourceCenter=false,antiAlias=true;
        public String blend="NORMAL",position="OUTSIDE",contour="LINEAR",bevelStyle="INNER_BEVEL",
            technique="SMOOTH",direction="UP";
        public int color=0xff000000,color2=0xffffffff,highlight=0xffffffff,shade=0xff000000;
        public float opacity=.75f,size=8,distance=7,angle=120,spread=0,depth=100,
            altitude=35,soften=0,gradientScale=100,highlightOpacity=.75f,shadowOpacity=.75f;
        public float[] customCurve={0,.25f,.5f,.75f,1};
        public Style(Kind kind){
            this.kind=kind;
            if(kind==Kind.OUTER_GLOW||kind==Kind.INNER_GLOW){color=0xffc78bff;size=12;blend="SCREEN";}
            if(kind==Kind.STROKE){color=0xfffbfcff;size=4;opacity=1;}
            if(kind==Kind.COLOR_OVERLAY){color=0xffb77fff;opacity=1;}
            if(kind==Kind.GRADIENT_OVERLAY){color=0xff7545dc;color2=0xfffcab66;opacity=1;}
            if(kind==Kind.BEVEL_EMBOSS){size=5;depth=100;}
        }
        public Style copy(){
            Style s=new Style(kind);s.enabled=enabled;s.reverse=reverse;s.sourceCenter=sourceCenter;
            s.antiAlias=antiAlias;s.blend=blend;s.position=position;s.contour=contour;
            s.bevelStyle=bevelStyle;s.technique=technique;s.direction=direction;
            s.color=color;s.color2=color2;s.highlight=highlight;s.shade=shade;
            s.opacity=opacity;s.size=size;s.distance=distance;s.angle=angle;s.spread=spread;
            s.depth=depth;s.altitude=altitude;s.soften=soften;s.gradientScale=gradientScale;
            s.highlightOpacity=highlightOpacity;s.shadowOpacity=shadowOpacity;
            s.customCurve=customCurve.clone();return s;
        }
        public JSONObject toJson()throws JSONException{
            JSONObject j=new JSONObject().put("kind",kind.name()).put("enabled",enabled)
                .put("reverse",reverse).put("sourceCenter",sourceCenter)
                .put("antiAlias",antiAlias).put("blend",blend).put("position",position)
                .put("contour",contour).put("bevelStyle",bevelStyle)
                .put("technique",technique).put("direction",direction)
                .put("color",color).put("color2",color2).put("highlight",highlight)
                .put("shade",shade).put("opacity",opacity).put("size",size)
                .put("distance",distance).put("angle",angle).put("spread",spread)
                .put("depth",depth).put("altitude",altitude).put("soften",soften)
                .put("gradientScale",gradientScale).put("highlightOpacity",highlightOpacity)
                .put("shadowOpacity",shadowOpacity);
            JSONArray points=new JSONArray();
            for(float p:customCurve)points.put(p);
            return j.put("curve",points);
        }
        public static Style fromJson(JSONObject j)throws Exception{
            Style s=new Style(Kind.valueOf(j.getString("kind")));
            s.enabled=j.optBoolean("enabled",true);s.reverse=j.optBoolean("reverse",false);
            s.sourceCenter=j.optBoolean("sourceCenter",false);s.antiAlias=j.optBoolean("antiAlias",true);
            s.blend=j.optString("blend","NORMAL");s.position=j.optString("position","OUTSIDE");
            s.contour=j.optString("contour","LINEAR");s.bevelStyle=j.optString("bevelStyle","INNER_BEVEL");
            s.technique=j.optString("technique","SMOOTH");s.direction=j.optString("direction","UP");
            s.color=j.optInt("color",s.color);s.color2=j.optInt("color2",s.color2);
            s.highlight=j.optInt("highlight",s.highlight);s.shade=j.optInt("shade",s.shade);
            s.opacity=(float)j.optDouble("opacity",s.opacity);s.size=(float)j.optDouble("size",s.size);
            s.distance=(float)j.optDouble("distance",s.distance);s.angle=(float)j.optDouble("angle",s.angle);
            s.spread=(float)j.optDouble("spread",s.spread);s.depth=(float)j.optDouble("depth",s.depth);
            s.altitude=(float)j.optDouble("altitude",s.altitude);s.soften=(float)j.optDouble("soften",s.soften);
            s.gradientScale=(float)j.optDouble("gradientScale",s.gradientScale);
            s.highlightOpacity=(float)j.optDouble("highlightOpacity",s.highlightOpacity);
            s.shadowOpacity=(float)j.optDouble("shadowOpacity",s.shadowOpacity);
            JSONArray curve=j.optJSONArray("curve");
            if(curve!=null&&curve.length()==5)for(int i=0;i<5;i++)s.customCurve[i]=(float)curve.getDouble(i);
            validate(s);return s;
        }
    }
    public static void validate(Style s){
        if(!Float.isFinite(s.size)||s.size<0||s.size>128||
            !Float.isFinite(s.distance)||s.distance<0||s.distance>128||
            !Float.isFinite(s.spread)||s.spread<0||s.spread>100||
            !Float.isFinite(s.opacity)||s.opacity<0||s.opacity>1||
            !Float.isFinite(s.depth)||s.depth<0||s.depth>1000||
            !Float.isFinite(s.angle)||Math.abs(s.angle)>360||
            !Float.isFinite(s.altitude)||s.altitude<0||s.altitude>90||
            !Float.isFinite(s.soften)||s.soften<0||s.soften>64||
            !Float.isFinite(s.gradientScale)||s.gradientScale<10||s.gradientScale>400)
            throw new IllegalArgumentException("Parâmetros de estilo inválidos");
        for(float n:s.customCurve)if(!Float.isFinite(n)||n<0||n>1)
            throw new IllegalArgumentException("Pontos de curva inválidos");
    }
    public static boolean hasStyles(StudioDocument.Layer l){
        for(Style s:l.styles)if(s.enabled)return true;return false;
    }
    private static float cap(float n){return Math.max(0,Math.min(1,n));}
    private static int lerp(int a,int b,float t){
        t=cap(t);return Color.argb(Math.round(Color.alpha(a)*(1-t)+Color.alpha(b)*t),
            Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
            Math.round(Color.green(a)*(1-t)+Color.green(b)*t),
            Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    public static float curve(Style s,float t){
        t=cap(t);
        switch(s.contour){
            case "ROUND":return (float)Math.sin(t*Math.PI*.5);
            case "SHARP":return t*t;
            case "CONE":return 1-Math.abs(2*t-1);
            case "RING":return .5f+.5f*(float)Math.cos(t*2*Math.PI);
            case "HALF_ROUND":return t<.5f?0:(float)Math.sin((t-.5f)*Math.PI);
            case "CUSTOM":
                float z=t*4;int index=Math.min(3,(int)z);
                return s.customCurve[index]+(s.customCurve[index+1]-s.customCurve[index])*(z-index);
            default:return t;
        }
    }
    private static Paint compositingPaint(Style s,float opacity){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(Math.round(255*cap(opacity)));
        if(Build.VERSION.SDK_INT>=29){
            BlendMode bm=BlendMode.SRC_OVER;
            switch(s.blend){
                case "MULTIPLY":bm=BlendMode.MULTIPLY;break;
                case "SCREEN":bm=BlendMode.SCREEN;break;
                case "OVERLAY":bm=BlendMode.OVERLAY;break;
                case "DARKEN":bm=BlendMode.DARKEN;break;
                case "LIGHTEN":bm=BlendMode.LIGHTEN;break;
                case "ADD":bm=BlendMode.PLUS;break;
            }
            p.setBlendMode(bm);
        }else if(s.blend.equals("MULTIPLY"))
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        else if(s.blend.equals("SCREEN"))
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        return p;
    }
    private static int[] distance(int[] alpha,int w,int h,boolean inside){
        final int FAR=32767;int n=w*h;int[] result=new int[n];
        for(int i=0;i<n;i++){
            boolean solid=alpha[i]>15;
            result[i]=(solid==inside)?FAR:0;
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x,d=result[i];
            if(x>0)d=Math.min(d,result[i-1]+3);
            if(y>0){d=Math.min(d,result[i-w]+3);
                if(x>0)d=Math.min(d,result[i-w-1]+4);
                if(x<w-1)d=Math.min(d,result[i-w+1]+4);}
            result[i]=d;
        }
        for(int y=h-1;y>=0;y--)for(int x=w-1;x>=0;x--){
            int i=y*w+x,d=result[i];
            if(x<w-1)d=Math.min(d,result[i+1]+3);
            if(y<h-1){d=Math.min(d,result[i+w]+3);
                if(x>0)d=Math.min(d,result[i+w-1]+4);
                if(x<w-1)d=Math.min(d,result[i+w+1]+4);}
            result[i]=d;
        }
        return result;
    }
    /** Returns the computed style pixels, including outside-of-layer effects. */
    private static Bitmap drawStyle(Bitmap source,Style s,int padding){
        int w=source.getWidth(),h=source.getHeight();
        int W=w+padding*2,H=h+padding*2,n=W*H;
        int[] src=new int[w*h];source.getPixels(src,0,w,0,0,w,h);
        int[] argb=new int[n],alpha=new int[n];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            alpha[(y+padding)*W+x+padding]=Color.alpha(src[y*w+x]);
        int[] inner=distance(alpha,W,H,true),outer=distance(alpha,W,H,false);
        float radians=(float)Math.toRadians(s.angle);
        float dx=(float)Math.cos(radians),dy=(float)-Math.sin(radians);
        float radius=Math.max(.01f,s.size),fade=Math.max(.1f,radius*(1-s.spread/110f));
        boolean bevel=s.kind==Kind.BEVEL_EMBOSS;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int i=y*W+x,a=alpha[i];float coverage=a/255f;
            float di=inner[i]/3f,de=outer[i]/3f,t=0;
            int pigment=s.color;
            switch(s.kind){
                case DROP_SHADOW: {
                    int sx=Math.round(x-dx*s.distance),sy=Math.round(y-dy*s.distance);
                    if(sx>=0&&sx<W&&sy>=0&&sy<H){
                        int q=sy*W+sx;
                        float shifted=alpha[q]/255f;
                        float away=outer[q]/3f;
                        // Shifted matte with edge softness, including transparent halo.
                        float soft=cap(1-de/Math.max(1,radius));
                        t=Math.max(shifted,soft*shifted*.45f)*(1-coverage*.2f);
                    }
                    break;
                }
                case OUTER_GLOW:
                    t=(1-coverage)*curve(s,cap(1-de/fade));break;
                case INNER_GLOW:
                    t=coverage*curve(s,s.sourceCenter?
                        cap(di/Math.max(1,radius)):cap(1-di/fade));break;
                case INNER_SHADOW: {
                    int sx=Math.round(x+dx*s.distance),sy=Math.round(y+dy*s.distance);
                    float shifted= sx>=0&&sx<W&&sy>=0&&sy<H?
                        alpha[sy*W+sx]/255f:0;
                    t=coverage*(1-shifted)*cap(1-di/Math.max(1,radius));
                    break;
                }
                case STROKE:
                    if("INSIDE".equals(s.position))t=coverage*(di<=s.size?1:0);
                    else if("CENTER".equals(s.position))
                        t=di<=s.size*.5f&&coverage>0?coverage:
                            (de<=s.size*.5f?1-coverage:0);
                    else t=(1-coverage)*(de<=s.size?1:0);
                    break;
                case COLOR_OVERLAY:t=coverage;break;
                case GRADIENT_OVERLAY: {
                    float gradient=(float)((x-padding-w*.5)*dx+
                        (y-padding-h*.5)*dy)/(Math.max(1,Math.max(w,h))*s.gradientScale/100f)+.5f;
                    pigment=lerp(s.color,s.color2,s.reverse?1-cap(gradient):cap(gradient));
                    t=coverage;break;
                }
                case BEVEL_EMBOSS: {
                    if(di>radius||a==0)break;
                    float left=inner[y*W+Math.max(0,x-1)]/3f;
                    float right=inner[y*W+Math.min(W-1,x+1)]/3f;
                    float up=inner[Math.max(0,y-1)*W+x]/3f;
                    float down=inner[Math.min(H-1,y+1)*W+x]/3f;
                    float light= (right-left)*dx+(down-up)*dy;
                    if("DOWN".equals(s.direction))light=-light;
                    float strength=cap((radius-di)/radius)*Math.min(3,s.depth/100f);
                    float elevation=(float)Math.sin(Math.toRadians(s.altitude));
                    float contour=curve(s,cap((radius-di)/radius));
                    t=coverage*strength*contour*Math.max(.15f,elevation);
                    pigment=light>=0?s.highlight:s.shade;
                    t*=light>=0?s.highlightOpacity:s.shadowOpacity;
                    break;
                }
            }
            int visible=Math.round(255*cap(t)*Color.alpha(pigment)/255f);
            if(visible>0)argb[i]=(visible<<24)|(pigment&0xffffff);
        }
        Bitmap output=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        output.setPixels(argb,0,W,0,0,W,H);return output;
    }
    public static void draw(Canvas canvas,StudioDocument.Layer layer,Bitmap source,Paint paint){
        if(source==null)return;
        if(!hasStyles(layer)){canvas.drawBitmap(source,0,0,paint);return;}
        if((long)source.getWidth()*source.getHeight()>MAX_PIXELS){
            canvas.drawBitmap(source,0,0,paint);return;
        }
        int padding=0;
        for(Style s:layer.styles)if(s.enabled)
            padding=Math.max(padding,(int)Math.ceil(s.size+s.distance+4));
        padding=Math.min(264,padding);
        if((long)(source.getWidth()+padding*2)*(source.getHeight()+padding*2)>MAX_PIXELS*2L)
            padding=0;
        for(Style s:layer.styles)if(s.enabled&&
                (s.kind==Kind.DROP_SHADOW||s.kind==Kind.OUTER_GLOW||
                s.kind==Kind.STROKE&&!"INSIDE".equals(s.position))){
            Bitmap fx=drawStyle(source,s,padding);
            canvas.drawBitmap(fx,-padding,-padding,compositingPaint(s,layer.opacity*s.opacity));
            fx.recycle();
        }
        canvas.drawBitmap(source,0,0,paint);
        for(Style s:layer.styles)if(s.enabled&&
            (s.kind==Kind.COLOR_OVERLAY||s.kind==Kind.GRADIENT_OVERLAY||
             s.kind==Kind.INNER_SHADOW||s.kind==Kind.INNER_GLOW||
             s.kind==Kind.BEVEL_EMBOSS||
             s.kind==Kind.STROKE&&"INSIDE".equals(s.position))){
            Bitmap fx=drawStyle(source,s,padding);
            canvas.drawBitmap(fx,-padding,-padding,compositingPaint(s,layer.opacity*s.opacity));
            fx.recycle();
        }
    }
}

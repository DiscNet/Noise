package com.discnet.noise.advancedstudio;

import android.graphics.BlendMode;
import android.os.Build;
import android.graphics.Color;

/**
 * Extended Photoshop/PhotoCraft-style RGB blend modes. Values use straight
 * alpha and the W3C backdrop/source compositing equation; all input channels
 * are clamped to [0,1]. Used for modes not supplied by Android Canvas.
 */
public final class StudioBlendMath {
    private StudioBlendMath(){}
    public enum Mode {
        NORMAL("Normal"), DISSOLVE("Dissolver"),
        DARKEN("Escurecer"), MULTIPLY("Multiplicar"), COLOR_BURN("Superexposição de cor"),
        LINEAR_BURN("Superexposição linear"), DARKER_COLOR("Cor mais escura"),
        LIGHTEN("Clarear"), SCREEN("Divisão / Tela"), COLOR_DODGE("Subexposição de cor"),
        LINEAR_DODGE("Subexposição linear (Adicionar)"), LIGHTER_COLOR("Cor mais clara"),
        OVERLAY("Sobrepor"), SOFT_LIGHT("Luz suave"), HARD_LIGHT("Luz direta"),
        VIVID_LIGHT("Luz vívida"), LINEAR_LIGHT("Luz linear"), PIN_LIGHT("Luz de pino"),
        HARD_MIX("Mistura sólida"), DIFFERENCE("Diferença"), EXCLUSION("Exclusão"),
        SUBTRACT("Subtrair"), DIVIDE("Dividir"),
        HUE("Matiz"), SATURATION("Saturação"), COLOR("Cor"), LUMINOSITY("Luminosidade");
        public final String label;
        Mode(String label){this.label=label;}
    }
    public static String[] names(){
        Mode[] values=Mode.values();String[] labels=new String[values.length];
        for(int i=0;i<values.length;i++)labels[i]=values[i].label;return labels;
    }
    public static String[] ids(){
        Mode[] values=Mode.values();String[] names=new String[values.length];
        for(int i=0;i<values.length;i++)names[i]=values[i].name();return names;
    }
    public static Mode of(String name){
        if("ADD".equals(name)||"PLUS".equals(name))return Mode.LINEAR_DODGE;
        try{return Mode.valueOf(name);}catch(Exception e){return Mode.NORMAL;}
    }
    public static BlendMode nativeBlend(Mode m){
        if(Build.VERSION.SDK_INT<29)return null;
        switch(m){
            case NORMAL:return BlendMode.SRC_OVER;
            case DARKEN:return BlendMode.DARKEN;
            case MULTIPLY:return BlendMode.MULTIPLY;
            case COLOR_BURN:return BlendMode.COLOR_BURN;
            case LIGHTEN:return BlendMode.LIGHTEN;
            case SCREEN:return BlendMode.SCREEN;
            case COLOR_DODGE:return BlendMode.COLOR_DODGE;
            case LINEAR_DODGE:return BlendMode.PLUS;
            case OVERLAY:return BlendMode.OVERLAY;
            case SOFT_LIGHT:return BlendMode.SOFT_LIGHT;
            case HARD_LIGHT:return BlendMode.HARD_LIGHT;
            case DIFFERENCE:return BlendMode.DIFFERENCE;
            case EXCLUSION:return BlendMode.EXCLUSION;
            case HUE:return BlendMode.HUE;
            case SATURATION:return BlendMode.SATURATION;
            case COLOR:return BlendMode.COLOR;
            case LUMINOSITY:return BlendMode.LUMINOSITY;
            default:return null;
        }
    }
    public static boolean needsSoftware(Mode m){
        return nativeBlend(m)==null;
    }
    private static float clip(float f){return Math.min(1f,Math.max(0f,f));}
    private static float lum(float[] c){return .3f*c[0]+.59f*c[1]+.11f*c[2];}
    private static float sat(float[] c){
        return Math.max(c[0],Math.max(c[1],c[2]))-Math.min(c[0],Math.min(c[1],c[2]));
    }
    private static float[] clipColor(float[] c){
        float l=lum(c),low=Math.min(c[0],Math.min(c[1],c[2])),high=Math.max(c[0],Math.max(c[1],c[2]));
        if(low<0)for(int i=0;i<3;i++)c[i]=l+(c[i]-l)*l/(l-low);
        if(high>1)for(int i=0;i<3;i++)c[i]=l+(c[i]-l)*(1-l)/(high-l);
        for(int i=0;i<3;i++)c[i]=clip(c[i]);return c;
    }
    private static float[] setLum(float[] c,float target){
        float d=target-lum(c);
        for(int i=0;i<3;i++)c[i]+=d;
        return clipColor(c);
    }
    private static float[] setSat(float[] src,float saturation){
        int[] idx={0,1,2};
        for(int a=0;a<3;a++)for(int b=a+1;b<3;b++)
            if(src[idx[b]]<src[idx[a]]){int tmp=idx[a];idx[a]=idx[b];idx[b]=tmp;}
        float[] c=new float[3];int lo=idx[0],mid=idx[1],hi=idx[2];
        if(src[hi]>src[lo]){
            c[mid]=(src[mid]-src[lo])*saturation/(src[hi]-src[lo]);
            c[hi]=saturation;
        }
        c[lo]=0;return c;
    }
    private static float channel(Mode m,float b,float s){
        switch(m){
            case DARKEN:return Math.min(b,s);
            case MULTIPLY:return b*s;
            case COLOR_BURN:return b>=1-.0001f?1:s<=0?0:clip(1-(1-b)/s);
            case LINEAR_BURN:return clip(b+s-1);
            case LIGHTEN:return Math.max(b,s);
            case SCREEN:return b+s-b*s;
            case COLOR_DODGE:return b<=.0001f?0:s>=1?1:clip(b/(1-s));
            case LINEAR_DODGE:return clip(b+s);
            case OVERLAY:return b<=.5f?2*b*s:1-2*(1-b)*(1-s);
            case SOFT_LIGHT:return s<=.5f?
                b-(1-2*s)*b*(1-b):
                b+(2*s-1)*((b<=.25f?((16*b-12)*b+4)*b:(float)Math.sqrt(b))-b);
            case HARD_LIGHT:return s<=.5f?2*b*s:1-2*(1-b)*(1-s);
            case VIVID_LIGHT:return s<=.5f?(s<=0?0:clip(1-(1-b)/(2*s))):
                (s>=1?1:clip(b/(2*(1-s))));
            case LINEAR_LIGHT:return clip(b+2*s-1);
            case PIN_LIGHT:return s<=.5f?Math.min(b,2*s):Math.max(b,2*s-1);
            case HARD_MIX:return channel(Mode.VIVID_LIGHT,b,s)>=.5f?1:0;
            case DIFFERENCE:return Math.abs(b-s);
            case EXCLUSION:return b+s-2*b*s;
            case SUBTRACT:return clip(b-s);
            case DIVIDE:return s<=.00001f?1:clip(b/s);
            default:return s;
        }
    }
    public static int composite(Mode m,int backdrop,int source,float opacity,int x,int y){
        float as=Color.alpha(source)/255f*clip(opacity);
        if(as<=0)return backdrop;
        float ab=Color.alpha(backdrop)/255f;
        if(m==Mode.DISSOLVE){
            int seed=(x*73856093)^(y*19349663)^0x5f3759df;
            seed^=seed>>>13;seed*=0x5bd1e995;seed^=seed>>>15;
            if(((seed>>>1)&0x7fffffff)/(float)0x7fffffff>=as)return backdrop;
            as=1f;
        }
        float ao=as+ab*(1-as);
        if(ao<=.00001f)return 0;
        float[] cb={Color.red(backdrop)/255f,Color.green(backdrop)/255f,Color.blue(backdrop)/255f};
        float[] cs={Color.red(source)/255f,Color.green(source)/255f,Color.blue(source)/255f};
        float[] out=new float[3];
        if(m==Mode.HUE)out=setLum(setSat(cs,sat(cb)),lum(cb));
        else if(m==Mode.SATURATION)out=setLum(setSat(cb,sat(cs)),lum(cb));
        else if(m==Mode.COLOR)out=setLum(cs,lum(cb));
        else if(m==Mode.LUMINOSITY)out=setLum(cb,lum(cs));
        else if(m==Mode.DARKER_COLOR||m==Mode.LIGHTER_COLOR){
            float sb=cb[0]+cb[1]+cb[2],ss=cs[0]+cs[1]+cs[2];
            out=(m==Mode.DARKER_COLOR?ss<sb:ss>sb)?cs:cb;
        }else for(int i=0;i<3;i++)out[i]=channel(m,cb[i],cs[i]);
        int[] result=new int[3];
        for(int i=0;i<3;i++){
            float v=(as*(1-ab)*cs[i]+as*ab*out[i]+(1-as)*ab*cb[i])/ao;
            result[i]=Math.round(255*clip(v));
        }
        return Color.argb(Math.round(255*ao),result[0],result[1],result[2]);
    }
    public static void blendTile(int[] dst,int[] src,int w,int h,int ox,int oy,Mode mode,float opacity){
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            dst[i]=composite(mode,dst[i],src[i],opacity,ox+x,oy+y);
        }
    }
}

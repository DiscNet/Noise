package com.discnet.noise;

import org.json.*;
import java.util.Arrays;

/** Serializable non-destructive recipe; it never owns or rewrites source pixels. */
final class EditorDocument {
    final float[] values=new float[AdjustmentSpec.COUNT];
    final boolean[] disabled=new boolean[AdjustmentSpec.COUNT];
    boolean invert,asciiEnabled,asciiColored,asciiDither,asciiSymbols,grainColored;
    int ditherMethod,ditherPalette,rotation;
    boolean flipX,flipY;
    float left=0,top=0,right=1,bottom=1;
    int outputLongEdge=0,jpegQuality=95;
    boolean jpeg=false;
    EditorDocument(){for(int i=0;i<values.length;i++)values[i]=AdjustmentSpec.neutral(i);}
    EditorDocument copy(){try{return fromJson(toJson());}catch(JSONException e){throw new IllegalStateException(e);}}
    float value(int i){return disabled[i]?AdjustmentSpec.neutral(i):values[i];}
    EditState gpu(boolean before){return new EditState(this,before);}
    void copyLook(EditorDocument from){
        System.arraycopy(from.values,0,values,0,values.length);
        System.arraycopy(from.disabled,0,disabled,0,disabled.length);
        invert=from.invert;asciiEnabled=from.asciiEnabled;asciiColored=from.asciiColored;
        asciiDither=from.asciiDither;asciiSymbols=from.asciiSymbols;grainColored=from.grainColored;
        ditherMethod=from.ditherMethod;ditherPalette=from.ditherPalette;
    }
    JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();o.put("schema",1);
        JSONArray v=new JSONArray(),d=new JSONArray();
        for(int i=0;i<values.length;i++){v.put((double)values[i]);d.put(disabled[i]);}
        o.put("values",v);o.put("disabled",d);o.put("invert",invert);
        o.put("asciiEnabled",asciiEnabled);o.put("asciiColored",asciiColored);o.put("asciiDither",asciiDither);o.put("asciiSymbols",asciiSymbols);
        o.put("grainColored",grainColored);o.put("ditherMethod",ditherMethod);o.put("ditherPalette",ditherPalette);
        o.put("rotation",rotation);o.put("flipX",flipX);o.put("flipY",flipY);
        o.put("crop",new JSONArray(new double[]{left,top,right,bottom}));
        o.put("outputLongEdge",outputLongEdge);o.put("jpeg",jpeg);o.put("jpegQuality",jpegQuality);return o;
    }
    static EditorDocument fromJson(JSONObject o)throws JSONException{
        if(o.optInt("schema",1)!=1)throw new JSONException("Versão de projeto não suportada");
        EditorDocument d=new EditorDocument();JSONArray v=o.optJSONArray("values"),off=o.optJSONArray("disabled");
        for(int i=0;i<d.values.length;i++){
            if(v!=null&&i<v.length())d.values[i]=AdjustmentSpec.clamp(i,(float)v.optDouble(i,d.values[i]));
            if(off!=null)d.disabled[i]=off.optBoolean(i,false);
        }
        d.invert=o.optBoolean("invert");d.asciiEnabled=o.optBoolean("asciiEnabled");d.asciiColored=o.optBoolean("asciiColored");
        d.asciiDither=o.optBoolean("asciiDither");d.asciiSymbols=o.optBoolean("asciiSymbols");d.grainColored=o.optBoolean("grainColored");
        d.ditherMethod=Math.max(0,Math.min(2,o.optInt("ditherMethod")));d.ditherPalette=Math.max(0,Math.min(2,o.optInt("ditherPalette")));
        d.rotation=Math.floorMod(o.optInt("rotation"),4);d.flipX=o.optBoolean("flipX");d.flipY=o.optBoolean("flipY");
        JSONArray c=o.optJSONArray("crop");
        if(c!=null&&c.length()==4){
            d.left=bounded(c.optDouble(0,0));d.top=bounded(c.optDouble(1,0));d.right=bounded(c.optDouble(2,1));d.bottom=bounded(c.optDouble(3,1));
            if(d.right-d.left<.001f||d.bottom-d.top<.001f){d.left=d.top=0;d.right=d.bottom=1;}
        }
        d.outputLongEdge=Math.max(0,Math.min(4096,o.optInt("outputLongEdge")));
        d.jpeg=o.optBoolean("jpeg");d.jpegQuality=Math.max(1,Math.min(100,o.optInt("jpegQuality",95)));return d;
    }
    private static float bounded(double n){return Double.isFinite(n)?(float)Math.max(0,Math.min(1,n)):0;}
    boolean same(EditorDocument d){try{return toJson().toString().equals(d.toJson().toString());}catch(JSONException e){return false;}}
    float aspect(int width,int height){float w=width*(right-left),h=height*(bottom-top);return rotation%2==0?w/h:h/w;}
    int[] outputSize(int width,int height,int limit){
        float w=width*(right-left),h=height*(bottom-top);
        if(rotation%2!=0){float t=w;w=h;h=t;}
        float edge=Math.min(limit,outputLongEdge>0?outputLongEdge:Math.max(w,h));
        float factor=Math.min(1,edge/Math.max(w,h));return new int[]{Math.max(1,Math.round(w*factor)),Math.max(1,Math.round(h*factor))};
    }
}

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
    int outputWidth=0,outputHeight=0; // V1.28 optional; legacy outputLongEdge retained
    boolean jpeg=false;
    String exportFormat="";
    // Six stops per editable curve: H→H, H→S, S→S. Zero is the identity curve.
    final float[] colorCurves=new float[18];
    // Three separate chromatic wheels: shadow, midtone, highlight (XY vectors).
    final float[] colorWheels=new float[6];
    int blendMode=0; // 0 normal, 1 multiply, 2 screen, 3 overlay, 4 soft light

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
        System.arraycopy(from.colorCurves,0,colorCurves,0,colorCurves.length);
        System.arraycopy(from.colorWheels,0,colorWheels,0,colorWheels.length);
        blendMode=from.blendMode;
    }
    /** Real non-destructive preset strength; geometry/output settings stay unchanged. */
    static EditorDocument blendLook(EditorDocument base,EditorDocument look,float amount){
        float t=Math.max(0f,Math.min(1f,amount));
        EditorDocument result=base.copy();
        for(int i=0;i<result.values.length;i++){
            float start=base.value(i),target=look.value(i);
            result.values[i]=AdjustmentSpec.clamp(i,start+(target-start)*t);
            result.disabled[i]=false;
        }
        for(int i=0;i<result.colorCurves.length;i++)
            result.colorCurves[i]=base.colorCurves[i]+
                (look.colorCurves[i]-base.colorCurves[i])*t;
        for(int i=0;i<result.colorWheels.length;i++)
            result.colorWheels[i]=base.colorWheels[i]+
                (look.colorWheels[i]-base.colorWheels[i])*t;
        if(t>.01f){
            // Discrete operations cannot be interpolated; enable at midpoint.
            if(t>=.50f){
                result.invert=look.invert;result.asciiEnabled=look.asciiEnabled;
                result.asciiColored=look.asciiColored;result.asciiDither=look.asciiDither;
                result.asciiSymbols=look.asciiSymbols;result.grainColored=look.grainColored;
                result.ditherMethod=look.ditherMethod;result.ditherPalette=look.ditherPalette;
                result.blendMode=look.blendMode;
            }
        }
        return result;
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
        o.put("outputLongEdge",outputLongEdge);
        o.put("outputWidth",outputWidth);o.put("outputHeight",outputHeight);
        o.put("blendMode",blendMode);
        JSONArray curves=new JSONArray(),wheels=new JSONArray();
        for(float v1:colorCurves)curves.put((double)v1);
        for(float v1:colorWheels)wheels.put((double)v1);
        o.put("colorCurves",curves);o.put("colorWheels",wheels);
        o.put("jpeg",jpeg);o.put("exportFormat",exportFormat);
        o.put("jpegQuality",jpegQuality);return o;
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
        d.outputWidth=Math.max(0,Math.min(4096,o.optInt("outputWidth",0)));
        d.outputHeight=Math.max(0,Math.min(4096,o.optInt("outputHeight",0)));
        if(d.outputWidth==0||d.outputHeight==0){d.outputWidth=0;d.outputHeight=0;}
        d.blendMode=Math.max(0,Math.min(4,o.optInt("blendMode",0)));
        JSONArray curves=o.optJSONArray("colorCurves"),wheels=o.optJSONArray("colorWheels");
        for(int i=0;i<d.colorCurves.length;i++)if(curves!=null&&i<curves.length()){
            double n=curves.optDouble(i,0);d.colorCurves[i]=Double.isFinite(n)?
                (float)Math.max(-1,Math.min(1,n)):0f;
        }
        for(int i=0;i<d.colorWheels.length;i++)if(wheels!=null&&i<wheels.length()){
            double n=wheels.optDouble(i,0);d.colorWheels[i]=Double.isFinite(n)?
                (float)Math.max(-1,Math.min(1,n)):0f;
        }
        d.jpeg=o.optBoolean("jpeg");
        String requested=o.optString("exportFormat","");
        d.exportFormat=(requested.equals("webp")||requested.equals("jpeg")||requested.equals("png"))
            ?requested:"";
        if(!d.exportFormat.isEmpty())d.jpeg=d.exportFormat.equals("jpeg");
        d.jpegQuality=Math.max(1,Math.min(100,o.optInt("jpegQuality",95)));return d;
    }
    private static float bounded(double n){return Double.isFinite(n)?(float)Math.max(0,Math.min(1,n)):0;}
    boolean same(EditorDocument d){try{return toJson().toString().equals(d.toJson().toString());}catch(JSONException e){return false;}}
    float aspect(int width,int height){float w=width*(right-left),h=height*(bottom-top);return rotation%2==0?w/h:h/w;}
    int[] nativeSize(int width,int height){
        float w=Math.max(1,width*(right-left)),h=Math.max(1,height*(bottom-top));
        if(rotation%2!=0){float t=w;w=h;h=t;}
        return new int[]{Math.max(1,Math.round(w)),Math.max(1,Math.round(h))};
    }
    int[] outputSize(int width,int height,int limit){
        int[] original=nativeSize(width,height);
        int safeLimit=Math.max(1,Math.min(4096,limit));
        int reqW=outputWidth>0?outputWidth:original[0];
        int reqH=outputHeight>0?outputHeight:original[1];
        if(outputWidth==0||outputHeight==0){
            float edge=outputLongEdge>0?outputLongEdge:Math.max(original[0],original[1]);
            float scale=Math.min(1,edge/Math.max(original[0],original[1]));
            reqW=Math.max(1,Math.round(original[0]*scale));
            reqH=Math.max(1,Math.round(original[1]*scale));
        }
        // Deliberate v1.28 policy: allow reductions only. Do not advertise
        // artificial detail restoration or exceed the actual GPU texture limit.
        float safety=Math.min(1f,Math.min(safeLimit/(float)Math.max(1,reqW),safeLimit/(float)Math.max(1,reqH)));
        safety=Math.min(safety,Math.min(original[0]/(float)Math.max(1,reqW),original[1]/(float)Math.max(1,reqH)));
        return new int[]{Math.max(1,Math.round(reqW*safety)),Math.max(1,Math.round(reqH*safety))};
    }
}

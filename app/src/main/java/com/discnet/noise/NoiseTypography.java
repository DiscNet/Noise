package com.discnet.noise;

import android.content.Context;
import android.graphics.Typeface;

/** Shared, cached, SIL OFL Manrope variable-font faces, with platform fallback. */
final class NoiseTypography {
    private static Typeface regular,medium,bold;
    private static Typeface load(Context c,int weight){
        try{
            return new Typeface.Builder(c.getAssets(),"manrope.ttf")
                .setFontVariationSettings("'wght' "+weight).build();
        }catch(RuntimeException e){
            return Typeface.create(weight>=650?"sans-serif-medium":"sans-serif",
                weight>=650?Typeface.BOLD:Typeface.NORMAL);
        }
    }
    static synchronized Typeface regular(Context c){
        if(regular==null)regular=load(c,400);return regular;
    }
    static synchronized Typeface medium(Context c){
        if(medium==null)medium=load(c,550);return medium;
    }
    static synchronized Typeface bold(Context c){
        if(bold==null)bold=load(c,700);return bold;
    }
    private NoiseTypography(){}
}

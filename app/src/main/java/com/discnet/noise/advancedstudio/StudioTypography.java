package com.discnet.noise.advancedstudio;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Shared parametric text measurement/rendering for preview, effects and export. */
public final class StudioTypography {
    private StudioTypography(){}
    public static Typeface font(StudioDocument.Layer layer){
        int style=(layer.fontBold?Typeface.BOLD:0)|(layer.fontItalic?Typeface.ITALIC:0);
        Typeface custom=StudioFontLibrary.resolve(layer.fontFamily,style);
        if(custom!=null)return custom;
        try {return Typeface.create(layer.fontFamily,style);}
        catch(Exception ignored){return Typeface.create("sans-serif",style);}
    }
    private static Paint measurePaint(StudioDocument.Layer layer){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(font(layer));p.setTextSize(layer.fontSize);
        return p;
    }
    private static float width(Paint paint,StudioDocument.Layer layer,String line){
        float value=paint.measureText(line);
        if(!line.isEmpty())value+=(line.codePointCount(0,line.length())-1)
            *layer.letterSpacing*layer.fontSize;
        return Math.max(0,value);
    }
    public static int width(StudioDocument.Layer layer){
        Paint p=measurePaint(layer);float max=0;
        for(String line:layer.text.split("\n",-1))max=Math.max(max,width(p,layer,line));
        return Math.max(1,(int)Math.ceil(Math.min(8192,max+2)));
    }
    public static int height(StudioDocument.Layer layer){
        int count=layer.text.split("\n",-1).length;
        return Math.max(1,(int)Math.ceil(Math.min(8192,
            layer.fontSize*(1+(count-1)*layer.lineSpacing)+layer.fontSize*.24f)));
    }
    public static void draw(Canvas canvas,StudioDocument.Layer layer,Paint paint){
        paint.setTypeface(font(layer));paint.setTextSize(layer.fontSize);
        String[] lines=layer.text.split("\n",-1);
        float extent=width(layer);
        for(int line=0;line<lines.length;line++){
            String value=lines[line];
            float span=width(paint,layer,value);
            float x="CENTER".equals(layer.textAlign)?(extent-span)/2:
                "RIGHT".equals(layer.textAlign)?extent-span:0;
            float baseline=layer.fontSize*(1+line*layer.lineSpacing);
            if(layer.letterSpacing==0)canvas.drawText(value,x,baseline,paint);
            else for(int i=0;i<value.length();){
                int cp=value.codePointAt(i);
                String glyph=new String(Character.toChars(cp));
                canvas.drawText(glyph,x,baseline,paint);
                x+=paint.measureText(glyph)+layer.letterSpacing*layer.fontSize;
                i+=Character.charCount(cp);
            }
        }
    }
}

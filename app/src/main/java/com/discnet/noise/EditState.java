package com.discnet.noise;

/** Immutable GPU state. The preview and export receive the same adjustment values. */
public final class EditState {
    public static final float[] DITHER_DEFAULTS = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
    public static final float[] ART_DEFAULTS = {
        0f,.5f,.5f,.5f,.5f,.25f,  // rings: strength, spacing, width, center X/Y, wear
        0f,.5f,.5f,.5f,.5f,.5f,   // CRT: strength, scan pitch, softness, curvature, vignette, tint
        0f,.5f,.5f,.5f,.5f,1f    // glitch: strength, stripes, shift, frequency, grain, monochrome
    };
    // Character art: density, contrast, brightness, threshold, spacing, glyph scale.
    public static final float[] ASCII_DEFAULTS = {.50f,.50f,.50f,.15f,.10f,.50f};
    public static final EditState NEUTRAL = new EditState(new float[9],new float[3],new float[8],DITHER_DEFAULTS,ART_DEFAULTS,false,false);
    final float[] color, tone, hue, style, fxA, fxB, ditherA, ditherB;
    final float[] ringsA, ringsB, crtA, crtB, glitchA, glitchB;
    final float[] asciiA, asciiB;
    final boolean asciiEnabled, asciiColored, asciiDither, asciiSymbols;
    final boolean invert, original;

    public EditState(float[] values, boolean invert, boolean original) {
        this(values,new float[3],new float[8],DITHER_DEFAULTS,ART_DEFAULTS,invert,original);
    }
    public EditState(float[] values,float[] styles,boolean invert,boolean original) {
        this(values,styles,new float[8],DITHER_DEFAULTS,ART_DEFAULTS,invert,original);
    }
    public EditState(float[] values,float[] styles,float[] filters,boolean invert,boolean original) {
        this(values,styles,filters,DITHER_DEFAULTS,ART_DEFAULTS,invert,original);
    }
    /** dither: profundidade, posição X, posição Y, escala, densidade e ondulação. */
    public EditState(float[] values,float[] styles,float[] filters,float[] dither,boolean invert,boolean original) {
        this(values,styles,filters,dither,ART_DEFAULTS,invert,original);
    }
    /** Three independently adjustable analog-art effects with six controls each. */
    public EditState(float[] values,float[] styles,float[] filters,float[] dither,
                     float[] art,boolean invert,boolean original) {
        this(values,styles,filters,dither,art,ASCII_DEFAULTS,false,false,false,invert,original);
    }
    /** The same character atlas and settings are consumed by GPU preview and export. */
    public EditState(float[] values,float[] styles,float[] filters,float[] dither,
                     float[] art,float[] ascii,boolean asciiEnabled,boolean asciiColored,
                     boolean asciiSymbols,boolean invert,boolean original) {
        this(values,styles,filters,dither,art,ascii,asciiEnabled,asciiColored,false,
            asciiSymbols,invert,original);
    }
    /** Dither colors only recolor the glyphs; the Dither texture stays independent. */
    public EditState(float[] values,float[] styles,float[] filters,float[] dither,
                     float[] art,float[] ascii,boolean asciiEnabled,boolean asciiColored,
                     boolean asciiDither,boolean asciiSymbols,boolean invert,boolean original) {
        if(values == null || values.length != 9 || styles == null || styles.length != 3
            || filters == null || (filters.length != 7 && filters.length != 8)
            || dither == null || dither.length != 6 || art == null || art.length != 18
            || ascii == null || ascii.length != 6)
            throw new IllegalArgumentException("Invalid adjustment count");
        float[] v=values.clone();
        for(int i=0;i<9;i++)v[i]=Float.isFinite(v[i])?Math.max(-1f,Math.min(1f,v[i])):0f;
        float[] st=styles.clone(), fx=new float[8], dp=dither.clone(), ar=art.clone();
        for(int i=0;i<3;i++)st[i]=i<2?safeGain(st[i]):safe(st[i]);
        // Intensities may reach 200%; geometry/positions remain within 0..100%.
        for(int i=0;i<filters.length;i++)
            fx[i]=(i==0||i==1||i==2||i==3||i==5||i==6||i==7)
                ?safeGain(filters[i]):safe(filters[i]);
        for(int i=0;i<6;i++)dp[i]=safe(dp[i]);
        for(int i=0;i<18;i++)ar[i]=(i%6==0)?safeGain(ar[i]):safe(ar[i]);
        color=new float[]{1+v[0],v[1],(float)Math.pow(2,3*v[2]),(float)Math.pow(2,v[3])};
        tone=new float[]{v[4],v[5],v[6],v[7]};
        hue=new float[]{(float)Math.cos(v[8]*Math.PI),
            (float)Math.sin(v[8]*Math.PI)/(float)Math.sqrt(3)};
        style=st;
        fxA=new float[]{fx[0],fx[1],fx[2],fx[3]};
        fxB=new float[]{fx[4],fx[5],fx[6],fx[7]};
        ditherA=new float[]{dp[0],dp[1],dp[2],dp[3]};
        ditherB=new float[]{dp[4],dp[5]};
        ringsA=new float[]{ar[0],ar[1],ar[2],ar[3]};
        ringsB=new float[]{ar[4],ar[5]};
        crtA=new float[]{ar[6],ar[7],ar[8],ar[9]};
        crtB=new float[]{ar[10],ar[11]};
        glitchA=new float[]{ar[12],ar[13],ar[14],ar[15]};
        glitchB=new float[]{ar[16],ar[17]};
        float[] asc=ascii.clone();
        for(int i=0;i<asc.length;i++)asc[i]=safe(asc[i]);
        asciiA=new float[]{asc[0],asc[1],asc[2],asc[3]};
        asciiB=new float[]{asc[4],asc[5]};
        this.asciiEnabled=asciiEnabled;
        this.asciiColored=asciiColored && !asciiDither;
        this.asciiDither=asciiDither;
        this.asciiSymbols=asciiSymbols;
        this.invert=invert; this.original=original;
    }
    private static float safe(float v){return Float.isFinite(v)?Math.max(0f,Math.min(1f,v)):0f;}
    private static float safeGain(float v){return Float.isFinite(v)?Math.max(0f,Math.min(2f,v)):0f;}
}

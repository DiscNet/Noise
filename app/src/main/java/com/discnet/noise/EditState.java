package com.discnet.noise;

/** Immutable GPU state. The preview and export receive the same adjustment values. */
public final class EditState {
    public static final float[] DITHER_DEFAULTS = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
    public static final EditState NEUTRAL = new EditState(new float[9],new float[3],new float[8],DITHER_DEFAULTS,false,false);
    final float[] color, tone, hue, style, fxA, fxB, ditherA, ditherB;
    final boolean invert, original;

    public EditState(float[] values, boolean invert, boolean original) {
        this(values,new float[3],new float[8],DITHER_DEFAULTS,invert,original);
    }
    public EditState(float[] values,float[] styles,boolean invert,boolean original) {
        this(values,styles,new float[8],DITHER_DEFAULTS,invert,original);
    }
    public EditState(float[] values,float[] styles,float[] filters,boolean invert,boolean original) {
        this(values,styles,filters,DITHER_DEFAULTS,invert,original);
    }
    /** dither: profundidade, posição X, posição Y, escala, densidade e ondulação. */
    public EditState(float[] values,float[] styles,float[] filters,float[] dither,boolean invert,boolean original) {
        if(values == null || values.length != 9 || styles == null || styles.length != 3
            || filters == null || (filters.length != 7 && filters.length != 8)
            || dither == null || dither.length != 6)
            throw new IllegalArgumentException("Invalid adjustment count");
        float[] v=values.clone();
        for(int i=0;i<9;i++)v[i]=Float.isFinite(v[i])?Math.max(-1f,Math.min(1f,v[i])):0f;
        float[] st=styles.clone(), fx=new float[8], dp=dither.clone();
        for(int i=0;i<3;i++)st[i]=safe(st[i]);
        for(int i=0;i<filters.length;i++)fx[i]=safe(filters[i]);
        for(int i=0;i<6;i++)dp[i]=safe(dp[i]);
        color=new float[]{1+v[0],v[1],(float)Math.pow(2,3*v[2]),(float)Math.pow(2,v[3])};
        tone=new float[]{v[4],v[5],v[6],v[7]};
        hue=new float[]{(float)Math.cos(v[8]*Math.PI),
            (float)Math.sin(v[8]*Math.PI)/(float)Math.sqrt(3)};
        style=st;
        fxA=new float[]{fx[0],fx[1],fx[2],fx[3]};
        fxB=new float[]{fx[4],fx[5],fx[6],fx[7]};
        ditherA=new float[]{dp[0],dp[1],dp[2],dp[3]};
        ditherB=new float[]{dp[4],dp[5]};
        this.invert=invert; this.original=original;
    }
    private static float safe(float v){return Float.isFinite(v)?Math.max(0f,Math.min(1f,v)):0f;}
}

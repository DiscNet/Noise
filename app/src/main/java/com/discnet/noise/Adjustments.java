package com.discnet.noise;

/** Deterministic, non-destructive sRGB editing pipeline. */
public final class Adjustments {
    private static float clamp(float x) { return Math.max(0, Math.min(1, x)); }
    private static float smooth(float a, float b, float x) { float t=clamp((x-a)/(b-a)); return t*t*(3-2*t); }
    public static int[] apply(int[] source, int w, int h, float[] v) {
        int[] out = new int[source.length];
        for (int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i=y*w+x, c=source[i]; float r=(c>>16&255)/255f,g=(c>>8&255)/255f,b=(c&255)/255f;
            if(v[7]<0) {
                float sr=0,sg=0,sb=0,weight=0;
                for(int dy=-1;dy<=1;dy++) for(int dx=-1;dx<=1;dx++) {
                    int n=source[Math.max(0,Math.min(h-1,y+dy))*w+Math.max(0,Math.min(w-1,x+dx))];
                    float nr=(n>>16&255)/255f,ng=(n>>8&255)/255f,nb=(n&255)/255f;
                    float d=(nr-r)*(nr-r)+(ng-g)*(ng-g)+(nb-b)*(nb-b);
                    float q=(float)Math.exp(-d/0.025f);sr+=nr*q;sg+=ng*q;sb+=nb*q;weight+=q;
                }
                float a=-v[7]; r+=(sr/weight-r)*a;g+=(sg/weight-g)*a;b+=(sb/weight-b)*a;
            }
            float lum=.2126f*r+.7152f*g+.0722f*b;
            float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
            float sat=(1+v[0])*(1+v[1]*(1-(max-min)));
            r=lum+(r-lum)*sat;g=lum+(g-lum)*sat;b=lum+(b-lum)*sat;
            // Hue rotation around the neutral RGB axis (preserves greys).
            double angle=v[8]*Math.PI;float co=(float)Math.cos(angle),si=(float)Math.sin(angle)/1.7320508f;
            float k=(r+g+b)*(1-co)/3,rr=r*co+(b-g)*si+k,gg=g*co+(r-b)*si+k,bb=b*co+(g-r)*si+k;
            float exposure=(float)Math.pow(2,v[2]*3),contrast=(float)Math.pow(2,v[3]);
            r=(rr*exposure-.5f)*contrast+.5f;g=(gg*exposure-.5f)*contrast+.5f;b=(bb*exposure-.5f)*contrast+.5f;
            lum=clamp(.2126f*r+.7152f*g+.0722f*b);
            float tone=v[4]*.45f*smooth(.45f,1,lum)+v[5]*.35f*smooth(.7f,1,lum)+v[6]*.35f*(1-smooth(0,.35f,lum));
            float noise=0;
            if(v[7]>0) { int hash=i*374761393+668265263;hash=(hash^(hash>>>13))*1274126177;noise=(((hash>>>8)&65535)/65535f-.5f)*v[7]*.3f; }
            out[i]=(c&0xff000000)|(Math.round(clamp(r+tone+noise)*255)<<16)|(Math.round(clamp(g+tone+noise)*255)<<8)|Math.round(clamp(b+tone+noise)*255);
        }
        return out;
    }
}

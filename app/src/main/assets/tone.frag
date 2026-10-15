#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vTexCoord;
uniform sampler2D uImage;
uniform vec2 uSize;
uniform vec4 uColor;  // saturation, vibrance, exposure, contrast
uniform vec4 uTone;   // highlights, whites, blacks, noise
uniform vec2 uHue;
uniform vec3 uStyle;  // neon dither, independently adjustable glow, RGB shift
uniform vec4 uFxA;    // Fade, skin tone, dust, vignette
uniform vec4 uFxB;    // aberration, mist, sharpen, rotational blur
uniform vec4 uDitherA; // depth, pattern offset X and Y, pattern scale
uniform vec2 uDitherB; // dot density, wave distortion
uniform vec4 uRingsA; // blend, spacing, line thickness, center X
uniform vec2 uRingsB; // center Y, scratches
uniform vec4 uCrtA; // blend, scanline pitch, softness, barrel distortion
uniform vec2 uCrtB; // edge vignette, phosphor cool/warm tint
uniform vec4 uGlitchA; // blend, row size, shift, corruption frequency
uniform vec2 uGlitchB; // grain, grayscale
uniform sampler2D uGlyphs; // two rows of real monospaced character shapes
uniform vec4 uAsciiA; // columns, contrast, brightness, black threshold
uniform vec2 uAsciiB; // character spacing, glyph size
uniform bool uAsciiEnabled;
uniform bool uAsciiColored;
uniform bool uAsciiDither;
uniform bool uAsciiSymbols;
uniform float uPatternScale; // source-pixels per display pixel, 1.0 for export
uniform bool uInvert;
uniform bool uOriginal;
uniform bool uExport;
uniform vec4 uLight;
uniform vec2 uNoiseControl; // reduction strength, edge preservation
uniform vec2 uHDR;          // tone range, local microcontrast (editor pass)
uniform vec2 uBlend;        // processed layer opacity and blend mode
uniform vec4 uFilm;         // grain, stock response, halation, light leak
uniform vec2 uWheelShadows;
uniform vec2 uWheelMidtones;
uniform vec2 uWheelHighlights;
uniform vec3 uCurveHH0, uCurveHH1;
uniform vec3 uCurveHS0, uCurveHS1;
uniform vec3 uCurveSS0, uCurveSS1;

float hash(vec2 p) {
    // Stable per-cell noise. Unlike the old grain, coordinates never wrap modulo 256.
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 33.33);
    return fract((p.x + p.y) * p.x);
}

vec3 straight(vec4 c) {
    return c.a > 0.0001 ? c.rgb / c.a : vec3(0.0);
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}


vec3 rgbToHsv(vec3 c){
    vec4 k=vec4(0.0,-1.0/3.0,2.0/3.0,-1.0);
    vec4 p=mix(vec4(c.bg,k.wz),vec4(c.gb,k.xy),step(c.b,c.g));
    vec4 q=mix(vec4(p.xyw,c.r),vec4(c.r,p.yzx),step(p.x,c.r));
    float delta=q.x-min(q.w,q.y), eps=1.0e-6;
    return vec3(abs(q.z+(q.w-q.y)/(6.0*delta+eps)),delta/(q.x+eps),q.x);
}
vec3 hsvToRgb(vec3 c){
    vec3 p=abs(fract(c.xxx+vec3(0.0,2.0/3.0,1.0/3.0))*6.0-3.0);
    return c.z*mix(vec3(1.0),clamp(p-1.0,0.0,1.0),c.y);
}
float knot(vec3 lo,vec3 hi,int i){
    if(i==0)return lo.x;if(i==1)return lo.y;if(i==2)return lo.z;
    if(i==3)return hi.x;if(i==4)return hi.y;return hi.z;
}
float curve6(float t,vec3 lo,vec3 hi,bool cyclic){
    float index=clamp(t,0.0,.999999)* (cyclic?6.0:5.0);
    int j=int(floor(index));
    float a=knot(lo,hi,j);
    float b=knot(lo,hi,j>=5?(cyclic?0:5):j+1);
    float f=fract(index);f=f*f*(3.0-2.0*f);
    return mix(a,b,f);
}
vec3 blendColors(vec3 base,vec3 graded,float mode){
    if(mode<.5)return graded;
    if(mode<1.5)return base*graded;
    if(mode<2.5)return 1.0-(1.0-base)*(1.0-graded);
    if(mode<3.5){
        vec3 low=2.0*base*graded;
        vec3 high=1.0-2.0*(1.0-base)*(1.0-graded);
        return mix(low,high,step(vec3(.5),base));
    }
    // Soft light
    return (1.0-2.0*graded)*base*base+2.0*graded*base;
}
vec3 tintFromWheel(vec2 wheel){
    return vec3(wheel.x,-.5*wheel.x+.8660254*wheel.y,
        -.5*wheel.x-.8660254*wheel.y);
}


void main(){
 vec4 source=texture2D(uImage,vTexCoord);
 vec3 c=straight(source);
        float reduction=max(max(-uTone.w,0.0),uNoiseControl.x);
        if(reduction>.001){
            // Bilateral 3x3 spatial denoise: high strength averages noisy flat
            // regions while chroma edge protection reduces color bleeding.
            float rad=mix(.9,2.7,reduction);
            float sigma=mix(.31,.085,clamp(uNoiseControl.y,0.0,1.0));
            float inv=1.0/max(.0001,2.0*sigma*sigma);
            vec3 sum=c*1.8;float weights=1.8;
            for(int iy=-1;iy<=1;iy++)for(int ix=-1;ix<=1;ix++){
                if(ix==0&&iy==0)continue;
                vec2 offs=vec2(float(ix),float(iy));
                vec4 rgba=texture2D(uImage,clamp(vTexCoord+offs*rad/uSize,0.0,1.0));
                vec3 n=straight(rgba);
                float local=exp(-dot(n-c,n-c)*inv);
                float spatial=exp(-dot(offs,offs)*.36);
                float weight=local*spatial*rgba.a;
                sum+=n*weight;weights+=weight;
            }
            c=mix(c,sum/max(weights,.0001),clamp(reduction*.98,0.0,1.0));
        }
        c *= exp2(vec3(uLight.z*.45+uLight.w*.12, -uLight.w*.24, -uLight.z*.45+uLight.w*.12));
        float lum = luminance(c);
        float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
        c = vec3(lum) + (c - lum) * uColor.x * (1.0 + uColor.y * (1.0 - chroma));
        c = c * uHue.x + (c.brg - c.gbr) * uHue.y
          + dot(c, vec3((1.0 - uHue.x) / 3.0));
        // Curves operate on real per-pixel H/S, not overall photo tint.
        vec3 hsv=rgbToHsv(clamp(c,0.0,1.0));
        float shift=curve6(hsv.x,uCurveHH0,uCurveHH1,true);
        float satHue=curve6(hsv.x,uCurveHS0,uCurveHS1,true);
        float satSat=curve6(hsv.y,uCurveSS0,uCurveSS1,false);
        hsv.x=fract(hsv.x+shift*.28+1.0);
        hsv.y=clamp(hsv.y*(1.0+satHue*.88+satSat*.88),0.0,1.0);
        c=hsvToRgb(hsv);
        c = (c * uColor.z - 0.5) * uColor.w + 0.5;
        lum = clamp(luminance(c), 0.0, 1.0);
        // Realces and pretos retain their own curves.
        c += uTone.x * 0.45 * smoothstep(0.45, 1.0, lum)
           + uTone.z * 0.35 * (1.0 - smoothstep(0.0, 0.35, lum));

        // Brancos: adaptive white-point curve. Bright colored areas and
        // upper-midtone pixels now respond as well as near-white grayscale.
        // The older double attenuation (mix by upper AND exponent by upper)
        // made photos without strong highlights look unchanged.
        float peak = max(c.r, max(c.g, c.b));
        float whiteSignal = clamp(mix(luminance(c), peak, 0.28), 0.0, 1.0);
        float upper = pow(smoothstep(0.16, 0.89, whiteSignal), 0.72);
        float amount = uTone.y;
        if (abs(amount) > 0.0001) {
            float strength = 1.65 * abs(amount) * upper;
            float response = 0.92 * (1.0 - exp(-strength));
            // Shadows are protected by upper; smooth exponential compression
            // keeps colors continuous and avoids hard brightness cutoffs.
            c = amount > 0.0
                ? c + (vec3(1.0) - c) * response
                : c * (1.0 - response);
        }
        c += uLight.x*.25 + uLight.y*.4*(1.0-smoothstep(.05,.65,lum));
        // Shadow/midtone/highlight wheels act as separate color grade vectors.
        float shadowW=1.0-smoothstep(.12,.48,lum);
        float highlightW=smoothstep(.54,.92,lum);
        float midW=(1.0-shadowW)*(1.0-highlightW);
        vec3 tint=shadowW*tintFromWheel(uWheelShadows)
            +midW*tintFromWheel(uWheelMidtones)
            +highlightW*tintFromWheel(uWheelHighlights);
        c+=tint*.29;
        // HDR tonal range: open dark detail and compress very bright values.
        float hdr=clamp(uHDR.x,0.0,1.0);
        float ll=clamp(luminance(c),0.0,1.0);
        c+=hdr*.27*(1.0-smoothstep(.10,.54,ll))*(vec3(1.0)-c);
        c-=hdr*.15*smoothstep(.71,1.0,ll)*c;
        c=clamp(c,0.0,1.0);
        if(uFilm.y>.001){
            // Analog film stock: rounded shoulder, lifted toe, split-tone
            // chemistry and warmer skin/midtones. No source-image warping.
            float film=clamp(uFilm.y,0.0,1.0);
            float filmL=clamp(luminance(c),0.0,1.0);
            float toe=1.0-smoothstep(.06,.49,filmL);
            float shoulder=smoothstep(.45,.94,filmL);
            c+=film*toe*vec3(.022,.030,.055);
            c+=film*shoulder*vec3(.074,.039,-.025);
            c+=film*(1.0-toe)*vec3(.030,.008,-.043);
            c=mix(c,vec3(smoothstep(.02,.99,c.r),
                smoothstep(.025,.99,c.g),smoothstep(.028,.99,c.b)),
                film*.32);
            c=clamp(c,0.0,1.0);
        }
        // Blend the non-destructive grade over the unchanged source.
        c=mix(straight(source),blendColors(straight(source),c,uBlend.y),
            clamp(uBlend.x,0.0,1.0));


 gl_FragColor=vec4(c*source.a,source.a);
}

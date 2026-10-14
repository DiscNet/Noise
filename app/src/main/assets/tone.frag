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


void main(){
 vec4 source=texture2D(uImage,vTexCoord);
 vec3 c=straight(source);
        if (uTone.w < 0.0) {
            vec3 sum = vec3(0.0); float weights = 0.0;
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
                vec4 n4 = texture2D(uImage, vTexCoord + vec2(float(x), float(y)) / uSize);
                vec3 n = straight(n4);
                float wt = exp(-dot(n - c, n - c) / 0.025) * n4.a;
                sum += n * wt; weights += wt;
            }
            c = mix(c, sum / max(weights, 0.0001), -uTone.w);
        }
        c *= exp2(vec3(uLight.z*.45+uLight.w*.12, -uLight.w*.24, -uLight.z*.45+uLight.w*.12));
        float lum = luminance(c);
        float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
        c = vec3(lum) + (c - lum) * uColor.x * (1.0 + uColor.y * (1.0 - chroma));
        c = c * uHue.x + (c.brg - c.gbr) * uHue.y
          + dot(c, vec3((1.0 - uHue.x) / 3.0));
        c = (c * uColor.z - 0.5) * uColor.w + 0.5;
        lum = clamp(luminance(c), 0.0, 1.0);
        // Realces and pretos retain their own curves.
        c += uTone.x * 0.45 * smoothstep(0.45, 1.0, lum)
           + uTone.z * 0.35 * (1.0 - smoothstep(0.0, 0.35, lum));

        // Brancos: a smooth upper-midtone/white-point curve. The former
        // smoothstep(.70, 1.0) barely moved typical near-white photo pixels.
        // Both directions are non-linear and preserve a soft shoulder,
        // so the slider is effective without hard banding/clipping.
        float upper = smoothstep(0.43, 0.91, clamp(luminance(c), 0.0, 1.0));
        float amount = uTone.y;
        if (abs(amount) > 0.0001) {
            vec3 target = amount > 0.0
                ? vec3(1.0) - (vec3(1.0) - c) * exp2(-1.65 * amount * upper)
                : c * exp2(1.22 * amount * upper);
            c = mix(c, target, upper);
        }
        c += uLight.x*.25 + uLight.y*.4*(1.0-smoothstep(.05,.65,lum));
        c = clamp(c, 0.0, 1.0);


 gl_FragColor=vec4(c*source.a,source.a);
}

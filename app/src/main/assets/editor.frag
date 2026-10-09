#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vTexCoord;
uniform sampler2D uImage;
uniform vec2 uSize;
uniform vec4 uColor; // saturation, vibrance, exposure multiplier, contrast multiplier
uniform vec4 uTone;  // highlights, whites, blacks, noise
uniform vec2 uHue;   // cosine, sine / sqrt(3)
uniform bool uInvert;
uniform bool uOriginal;
uniform bool uExport;

vec3 straight(vec4 c) { return c.a > 0.0001 ? c.rgb / c.a : vec3(0.0); }
void main() {
    vec4 source = texture2D(uImage, vTexCoord);
    vec3 c = straight(source);
    if (!uOriginal) {
        if (uTone.w < 0.0) {
            vec3 sum = vec3(0.0);
            float weights = 0.0;
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    vec4 neighbor = texture2D(uImage, vTexCoord + vec2(float(x), float(y)) / uSize);
                    vec3 n = straight(neighbor);
                    vec3 delta = n - c;
                    float w = exp(-dot(delta, delta) / 0.025) * neighbor.a;
                    sum += n * w;
                    weights += w;
                }
            }
            c = mix(c, sum / max(weights, 0.0001), -uTone.w);
        }
        float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
        float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
        c = vec3(luma) + (c - luma) * uColor.x * (1.0 + uColor.y * (1.0 - chroma));
        c = c * uHue.x + (c.brg - c.gbr) * uHue.y + dot(c, vec3((1.0 - uHue.x) / 3.0));
        c = (c * uColor.z - 0.5) * uColor.w + 0.5;
        luma = clamp(dot(c, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
        c += uTone.x * 0.45 * smoothstep(0.45, 1.0, luma)
           + uTone.y * 0.35 * smoothstep(0.7, 1.0, luma)
           + uTone.z * 0.35 * (1.0 - smoothstep(0.0, 0.35, luma));
        if (uTone.w > 0.0) {
            vec2 pixel = mod(floor(vTexCoord * uSize), 256.0);
            float grain = fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
            c += (grain - 0.5) * uTone.w * 0.3;
        }
        c = clamp(c, 0.0, 1.0);
        if (uInvert) c = 1.0 - c;
    }
    if (uExport) {
        gl_FragColor = vec4(source.a > 0.0001 ? c : vec3(0.0), source.a);
    } else {
        float grid = mod(floor(gl_FragCoord.x / 16.0) + floor(gl_FragCoord.y / 16.0), 2.0);
        vec3 background = mix(vec3(0.075, 0.085, 0.12), vec3(0.12, 0.135, 0.17), grid);
        gl_FragColor = vec4(mix(background, c, source.a), 1.0);
    }
}

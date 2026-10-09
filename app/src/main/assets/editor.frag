#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vTexCoord;
uniform sampler2D uImage;
uniform vec2 uSize;
uniform vec4 uColor;
uniform vec4 uTone;
uniform vec2 uHue;
uniform vec3 uStyle; // dither, glow, RGB shift
uniform bool uInvert;
uniform bool uOriginal;
uniform bool uExport;

float hash(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 33.33);
    return fract((p.x + p.y) * p.x);
}
vec3 straight(vec4 c) { return c.a > 0.0001 ? c.rgb / c.a : vec3(0.0); }
vec3 palette(float t) {
    // Five-color neon palette, perceptual luminance mapped for recognizable silhouettes.
    vec3 blue = vec3(.04, .19, 1.0);
    vec3 violet = vec3(.46, .22, 1.0);
    vec3 pink = vec3(1.0, .05, .71);
    vec3 orange = vec3(1.0, .30, .025);
    vec3 ice = vec3(.86, .91, 1.0);
    if (t < .23) return mix(blue, violet, smoothstep(.02, .23, t));
    if (t < .49) return mix(violet, pink, smoothstep(.23, .49, t));
    if (t < .76) return mix(pink, orange, smoothstep(.49, .76, t));
    return mix(orange, ice, smoothstep(.76, 1.0, t));
}
void main() {
    vec4 source = texture2D(uImage, vTexCoord);
    vec3 c = straight(source);
    if (!uOriginal) {
        if (uTone.w < 0.0) {
            vec3 sum = vec3(0.0);
            float weights = 0.0;
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
                vec4 n4 = texture2D(uImage, vTexCoord + vec2(float(x), float(y)) / uSize);
                vec3 n = straight(n4);
                float w = exp(-dot(n - c, n - c) / .025) * n4.a;
                sum += n * w; weights += w;
            }
            c = mix(c, sum / max(weights, .0001), -uTone.w);
        }
        float luma = dot(c, vec3(.2126, .7152, .0722));
        float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
        c = vec3(luma) + (c - luma) * uColor.x * (1.0 + uColor.y * (1.0 - chroma));
        c = c * uHue.x + (c.brg - c.gbr) * uHue.y + dot(c, vec3((1.0 - uHue.x) / 3.0));
        c = (c * uColor.z - .5) * uColor.w + .5;
        luma = clamp(dot(c, vec3(.2126, .7152, .0722)), 0.0, 1.0);
        c += uTone.x * .45 * smoothstep(.45, 1.0, luma)
           + uTone.y * .35 * smoothstep(.7, 1.0, luma)
           + uTone.z * .35 * (1.0 - smoothstep(0.0, .35, luma));
        // Hash uses the full source pixel address, never wraps into a repeating 256px tile.
        if (uTone.w > 0.0) {
            vec2 pixel = floor(vTexCoord * uSize);
            float fine = hash(pixel);
            float coarse = hash(floor(pixel * .53) + vec2(127.1, 311.7));
            float grain = (fine * .77 + coarse * .23 - .5);
            c += grain * uTone.w * .37;
        }
        c = clamp(c, 0.0, 1.0);
        if (uStyle.z > .001) {
            vec2 offset = vec2(1.0 + 5.0 * uStyle.z, .4 + 1.6 * uStyle.z) / uSize;
            vec3 left = straight(texture2D(uImage, vTexCoord - offset));
            vec3 right = straight(texture2D(uImage, vTexCoord + offset));
            c.r = mix(c.r, left.r, uStyle.z * .85);
            c.b = mix(c.b, right.b, uStyle.z * .85);
        }
        if (uStyle.x > .001) {
            // Organic neon stipple: luminance-adaptive halftone + wavy rows.
            // All geometry is defined in SOURCE pixels so preview/export match.
            vec2 p = vTexCoord * uSize;
            float scale = mix(3.1, 1.9, uStyle.x);
            vec2 cell = floor(p / scale);
            float bend = sin(cell.y * .64 + sin(cell.x * .18) * 1.2) * .55;
            vec2 local = fract(vec2(p.x / scale + bend, p.y / scale)) - .5;
            float lum = clamp(dot(c, vec3(.2126, .7152, .0722)), 0.0, 1.0);
            // Pixel jitter varies across the entire image; no periodic noise texture.
            float jitter = hash(cell + 17.7) - .5;
            float threshold = mix(.31, .045, sqrt(lum)) + jitter * .11;
            float dotShape = 1.0 - smoothstep(threshold, threshold + .16, length(local));
            float wave = sin((p.y + sin(p.x * .035) * 2.0) * 3.14 / scale);
            float row = smoothstep(-.25, .75, wave);
            float ink = max(dotShape * .93, row * .48 * lum);
            float brightness = smoothstep(.04, .89, lum);
            vec3 neon = palette(clamp(lum * .80 + (c.r - c.b) * .18 + .11, 0.0, 1.0));
            vec3 styled = neon * ink * (.18 + 1.65 * brightness);
            styled += neon * pow(ink * brightness, 2.0) * .45;
            // Preserve some source detail at intermediate strengths.
            c = mix(c, clamp(styled, 0.0, 1.0), uStyle.x);
        }
        if (uStyle.y > .001) {
            // Cheap single-pass bloom approximation. Highlight-local and deterministic.
            vec2 stepUV = vec2(2.5 + 4.5 * uStyle.y) / uSize;
            vec3 glow = vec3(0.0);
            glow += straight(texture2D(uImage, vTexCoord + vec2(stepUV.x, 0.0)));
            glow += straight(texture2D(uImage, vTexCoord - vec2(stepUV.x, 0.0)));
            glow += straight(texture2D(uImage, vTexCoord + vec2(0.0, stepUV.y)));
            glow += straight(texture2D(uImage, vTexCoord - vec2(0.0, stepUV.y)));
            glow *= .25;
            float energy = smoothstep(.23, .8, dot(glow, vec3(.2126, .7152, .0722)));
            vec3 halo = mix(glow, palette(dot(glow, vec3(.2126, .7152, .0722))), uStyle.x);
            c = clamp(c + halo * energy * uStyle.y * .46, 0.0, 1.0);
        }
        if (uInvert) c = 1.0 - c;
    }
    if (uExport) {
        gl_FragColor = vec4(source.a > .0001 ? c : vec3(0.0), source.a);
    } else {
        // Alpha checkerboard for transparent images; opaque input remains unchanged.
        float grid = mod(floor(gl_FragCoord.x / 16.0) + floor(gl_FragCoord.y / 16.0), 2.0);
        vec3 background = mix(vec3(.065, .065, .08), vec3(.11, .11, .13), grid);
        gl_FragColor = vec4(mix(background, c, source.a), 1.0);
    }
}

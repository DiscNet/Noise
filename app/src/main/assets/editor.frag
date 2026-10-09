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
uniform vec3 uFxB;    // lens aberrations, mist, sharpen
uniform float uPatternScale; // source-pixels per display pixel, 1.0 for export
uniform bool uInvert;
uniform bool uOriginal;
uniform bool uExport;

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

// Ordered-dither threshold (4 x 4 Bayer). The visible texture itself remains
// irregular because dots are independently hashed in image-space cells.
float bayer4(vec2 p) {
    vec2 q = mod(floor(p), 4.0);
    float bx = mod(q.x, 2.0), by = mod(q.y, 2.0);
    float hx = floor(q.x * 0.5), hy = floor(q.y * 0.5);
    // Top-left 2x2 matrix: 0,2 / 3,1; top-right bits interleave.
    float low = (bx == by ? 0.0 : (bx > by ? 2.0 : 3.0));
    if (bx > 0.5 && by > 0.5) low = 1.0;
    float high = (hx == hy ? 0.0 : (hx > hy ? 2.0 : 3.0));
    if (hx > 0.5 && hy > 0.5) high = 1.0;
    return (4.0 * low + high + 0.5) / 16.0;
}

// The reference is false-color neon, not a conventional 2-color Bayer dither.
// Cold highlights run violet -> blue -> almost white; warm areas run magenta
// -> saturated red/orange, as in the provided tiger image.
vec3 neonPalette(float light, float warmth, float edge) {
    float t = clamp(light, 0.0, 1.0);
    float w = clamp(warmth, 0.0, 1.0);
    vec3 cold = mix(vec3(0.09, 0.23, 1.0), vec3(0.82, 0.79, 1.0), smoothstep(0.15, 0.86, t));
    vec3 hot = mix(vec3(1.0, 0.012, 0.48), vec3(1.0, 0.45, 0.015), smoothstep(0.21, 0.88, t));
    vec3 p = mix(cold, hot, w);
    // Magenta rim light around silhouettes, not flat purple everywhere.
    p = mix(p, vec3(1.0, 0.09, 0.67), edge * (0.19 + 0.25 * (1.0 - w)));
    return mix(p, vec3(0.91, 0.88, 1.0), smoothstep(0.79, 1.0, t) * (1.0 - w) * 0.65);
}

void main() {
    vec4 source = texture2D(uImage, vTexCoord);
    vec3 c = straight(source);

    if (!uOriginal) {
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
        float lum = luminance(c);
        float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
        c = vec3(lum) + (c - lum) * uColor.x * (1.0 + uColor.y * (1.0 - chroma));
        c = c * uHue.x + (c.brg - c.gbr) * uHue.y
          + dot(c, vec3((1.0 - uHue.x) / 3.0));
        c = (c * uColor.z - 0.5) * uColor.w + 0.5;
        lum = clamp(luminance(c), 0.0, 1.0);
        c += uTone.x * 0.45 * smoothstep(0.45, 1.0, lum)
           + uTone.y * 0.35 * smoothstep(0.70, 1.0, lum)
           + uTone.z * 0.35 * (1.0 - smoothstep(0.0, 0.35, lum));

        if (uTone.w > 0.0) {
            vec2 px = floor(vTexCoord * uSize);
            float fine = hash(px);
            float coarse = hash(floor(px * 0.53) + vec2(127.1, 311.7));
            c += (fine * 0.77 + coarse * 0.23 - 0.5) * uTone.w * 0.37;
        }
        c = clamp(c, 0.0, 1.0);

        // Tom de pele: selective warm/orange hue and luminosity adjustment.
        // This is an RGB color-range selection, not a face detector.
        if (uFxA.y > 0.001) {
            float redBlue = c.r - c.b;
            float redGreen = c.r - c.g;
            float warmMask = smoothstep(0.025, 0.24, redBlue)
                           * (1.0 - smoothstep(0.30, 0.69, abs(redGreen)))
                           * smoothstep(0.09, 0.28, luminance(c));
            vec3 skin = clamp(vec3(c.r * 1.08 + 0.022,
                                   c.g * 1.018 + 0.008,
                                   c.b * 0.89), 0.0, 1.0);
            c = mix(c, skin, uFxA.y * warmMask);
        }

        // Fade: film-matte tonal compression with raised blacks and muted whites.
        if (uFxA.x > 0.001) {
            float fade = uFxA.x;
            c = clamp(c * (1.0 - fade * 0.51)
                        + vec3(0.155 * fade), 0.0, 1.0);
        }

        // Nitidez: 4-tap unsharp mask, entirely skipped when set to zero.
        if (uFxB.z > 0.001) {
            vec2 onePixel = 1.35 / uSize;
            vec3 localAverage =
                straight(texture2D(uImage, vTexCoord + vec2(onePixel.x, 0.0))) +
                straight(texture2D(uImage, vTexCoord - vec2(onePixel.x, 0.0))) +
                straight(texture2D(uImage, vTexCoord + vec2(0.0, onePixel.y))) +
                straight(texture2D(uImage, vTexCoord - vec2(0.0, onePixel.y)));
            localAverage *= 0.25;
            c = clamp(c + (c - localAverage) * 1.35 * uFxB.z, 0.0, 1.0);
        }

        // Névoa: subtle spatial diffusion and atmosphere rather than static white overlay.
        if (uFxB.y > 0.001) {
            float radius = (2.0 + 0.008 * min(uSize.x, uSize.y)) * uFxB.y;
            vec2 shift = vec2(radius) / uSize;
            vec3 diffused = straight(texture2D(uImage, vTexCoord + vec2(shift.x, 0.0))) +
                            straight(texture2D(uImage, vTexCoord - vec2(shift.x, 0.0))) +
                            straight(texture2D(uImage, vTexCoord + vec2(0.0, shift.y))) +
                            straight(texture2D(uImage, vTexCoord - vec2(0.0, shift.y)));
            diffused *= 0.25;
            c = mix(c, diffused, uFxB.y * 0.41);
            c = c * (1.0 - uFxB.y * 0.16)
              + vec3(0.11, 0.125, 0.145) * uFxB.y;
            c = clamp(c, 0.0, 1.0);
        }

        if (uStyle.z > 0.001) {
            vec2 uv = vec2(1.0 + 5.0 * uStyle.z, 0.4 + 1.6 * uStyle.z) / uSize;
            vec3 left = straight(texture2D(uImage, vTexCoord - uv));
            vec3 right = straight(texture2D(uImage, vTexCoord + uv));
            c.r = mix(c.r, left.r, uStyle.z * 0.85);
            c.b = mix(c.b, right.b, uStyle.z * 0.85);
        }

        // Aberrações: radial lens fringe, distinct from the uniform RGB shift.
        if (uFxB.x > 0.001) {
            vec2 distanceFromCenter = (vTexCoord - 0.5) * 2.0;
            float radial = dot(distanceFromCenter, distanceFromCenter);
            vec2 separation = distanceFromCenter * radial *
                        (5.5 * uFxB.x) / uSize;
            float redChannel = straight(texture2D(uImage,
                 clamp(vTexCoord + separation, vec2(0.0), vec2(1.0)))).r;
            float blueChannel = straight(texture2D(uImage,
                 clamp(vTexCoord - separation, vec2(0.0), vec2(1.0)))).b;
            c.r = mix(c.r, redChannel, uFxB.x);
            c.b = mix(c.b, blueChannel, uFxB.x);
        }

        if (uStyle.x > 0.001) {
            // Five traits from the reference:
            // 1) nearly-black unlit background; 2) fine flowing horizontal
            // contour traces; 3) continuous cool/warm false-color bands;
            // 4) independent luminous dust; 5) bloom along illuminated edges.
            float amount = uStyle.x;
            vec2 pixel = vTexCoord * uSize / max(1.0, uPatternScale);
            float light = clamp(luminance(c), 0.0, 1.0);

            // Four filtered neighbor reads, only in dither mode. On GLES2 this
            // is far cheaper than generating intermediate full-resolution maps.
            vec2 texel = vec2(1.7 * max(1.0, uPatternScale)) / uSize;
            vec3 xc = straight(texture2D(uImage, vTexCoord + vec2(texel.x, 0.0)));
            vec3 xa = straight(texture2D(uImage, vTexCoord - vec2(texel.x, 0.0)));
            vec3 yc = straight(texture2D(uImage, vTexCoord + vec2(0.0, texel.y)));
            vec3 ya = straight(texture2D(uImage, vTexCoord - vec2(0.0, texel.y)));
            float hx = abs(luminance(xc) - luminance(xa));
            float hy = abs(luminance(yc) - luminance(ya));
            float rim = clamp((hx + hy) * 1.35, 0.0, 1.0);
            float detail = clamp((hx + hy) * 0.6, 0.0, 0.42);
            // Reference has deep black negative space, not a purple haze.
            float signal = smoothstep(0.06, 0.91, light);
            float silhouette = smoothstep(0.075, 0.29, light + rim * 0.26);

            // Posterize and ordered-dither LOCAL luminance, preserving smooth
            // contours with sparse stipple in darker halftone cells.
            float ordered = bayer4(pixel * 0.63);
            float levels = clamp(light + (ordered - 0.5) * 0.10, 0.0, 1.0);
            levels = floor(levels * 6.0 + 0.3) / 6.0;
            float warm = clamp(0.44 + (c.r - c.b) * 1.32 + (c.r - c.g) * 0.43, 0.0, 1.0);
            vec3 inkColor = neonPalette(levels, warm, rim);

            // Ripple traces: fine rows, with local displacement following
            // tonal surfaces and edge flow. No repeated overlay image.
            float flow = 1.25 * sin(pixel.x * 0.12 + pixel.y * 0.027)
                       + 0.54 * sin(pixel.x * 0.27 - pixel.y * 0.031)
                       + 2.15 * light + 1.55 * rim;
            float phase = abs(fract((pixel.y + flow) / 4.2) - 0.5);
            float trace = 1.0 - smoothstep(0.105, 0.31, phase);
            float coverage = clamp(0.08 + signal * 1.06 + rim * 0.48, 0.0, 1.0);
            // All bright portions retain connected lines; in shadows,
            // Bayer threshold thins them into short bright dashes.
            float interrupted = step(ordered, coverage);
            float lines = trace * silhouette * interrupted;

            // Irregular star/dot field; stable spatial hash, absolutely no
            // stretched tiled static noise texture.
            vec2 dotPos = pixel / 3.6;
            vec2 cell = floor(dotPos);
            vec2 local = fract(dotPos);
            vec2 jitter = vec2(hash(cell + 4.7), hash(cell + 78.6));
            vec2 center = vec2(0.21) + 0.58 * jitter;
            float radius = mix(0.10, 0.27, hash(cell + 112.0));
            float sparkShape = 1.0 - smoothstep(radius * 0.5, radius + 0.12, length(local - center));
            float density = clamp(0.015 + signal * 0.57 + rim * 0.30, 0.0, 0.87);
            float specks = sparkShape * step(hash(cell + 19.1), density) * silhouette;

            float brighten = (0.24 + 1.85 * pow(signal, 0.65) + detail);
            vec3 neon = inkColor * (lines + specks * 1.18) * brighten;

            // Thin, inexpensive emulated neon emission around neighboring
            // bright traces, with a little warm golden ambient sparkle.
            float softLine = 1.0 - smoothstep(0.27, 0.50, phase);
            float ambient = smoothstep(0.12, 0.48, (light + luminance(xc) +
                luminance(xa) + luminance(yc) + luminance(ya)) * 0.2);
            float halo = (softLine * 0.20 + specks * 0.28) * ambient * silhouette;
            neon += inkColor * halo * (0.28 + 0.95 * uStyle.y);
            // Subtle halo also extends to pixels adjacent to bright contours.
            neon += inkColor * smoothstep(0.28, 0.85, ambient) *
                 (0.035 + 0.18 * uStyle.y) * silhouette;
            vec3 dithered = clamp(neon, 0.0, 1.0);
            c = mix(c, dithered, amount);
        }

        if (uStyle.y > 0.001) {
            // Standalone Glow now works with OR without the Dither enabled.
            vec2 spread = vec2(2.5 + 4.5 * uStyle.y) / uSize;
            vec3 blur = straight(texture2D(uImage, vTexCoord + vec2(spread.x, 0.0)));
            blur += straight(texture2D(uImage, vTexCoord - vec2(spread.x, 0.0)));
            blur += straight(texture2D(uImage, vTexCoord + vec2(0.0, spread.y)));
            blur += straight(texture2D(uImage, vTexCoord - vec2(0.0, spread.y)));
            blur *= 0.25;
            float glowPower = smoothstep(0.23, 0.80, luminance(blur));
            vec3 haloColor = mix(blur,
                neonPalette(clamp(luminance(blur), 0.0, 1.0),
                clamp(0.45 + (blur.r - blur.b) * 1.3, 0.0, 1.0), 0.2),
                uStyle.x);
            c = clamp(c + haloColor * glowPower * uStyle.y *
                mix(0.46, 0.27, uStyle.x), 0.0, 1.0);
        }

        // Poeira: occasional randomly shaped film flecks and faint scratches.
        // Hashes image-space cells, not a tiled noise/particle texture.
        if (uFxA.z > 0.001) {
            vec2 dustP = vTexCoord * uSize / 13.0;
            vec2 dustCell = floor(dustP);
            vec2 pos = fract(dustP);
            vec2 jitter = vec2(hash(dustCell + 21.3), hash(dustCell + 93.7));
            vec2 fleckCenter = vec2(0.17) + 0.66 * jitter;
            float shape = length((pos - fleckCenter) *
                vec2(0.72 + hash(dustCell + 9.1), 1.1));
            float dustRadius = mix(0.08, 0.18, hash(dustCell + 63.5));
            float fleck = 1.0 - smoothstep(dustRadius * 0.47,
                                               dustRadius + 0.055, shape);
            float density = 0.13 + uFxA.z * 0.62;
            float particle = fleck * step(hash(dustCell + 49.6), density);
            float scratchId = hash(dustCell + 167.8);
            float scratch = (1.0 - smoothstep(0.015, 0.052,
                abs(pos.x - fleckCenter.x))) *
                step(0.995 - uFxA.z * 0.055, scratchId) *
                smoothstep(0.0, 0.17, pos.y) * (1.0 - smoothstep(0.83, 1.0, pos.y));
            float dustMask = clamp(max(particle, scratch * 0.34) * uFxA.z,
                                   0.0, 0.88);
            float bright = step(0.40, hash(dustCell + 7.6));
            vec3 dustInk = mix(vec3(0.028, 0.023, 0.035),
                               vec3(0.97, 0.86, 0.75), bright);
            c = mix(c, dustInk, dustMask);
        }

        // Vinheta stays centered in source-image coordinates.
        if (uFxA.w > 0.001) {
            float edgeDistance = length((vTexCoord - 0.5) * 2.0);
            float falloff = smoothstep(0.35, 1.34, edgeDistance);
            c *= 1.0 - 0.93 * uFxA.w * falloff;
        }

        if (uInvert) c = 1.0 - c;
    }

    if (uExport) {
        gl_FragColor = vec4(source.a > 0.0001 ? c : vec3(0.0), source.a);
    } else {
        float grid = mod(floor(gl_FragCoord.x / 16.0) + floor(gl_FragCoord.y / 16.0), 2.0);
        vec3 background = mix(vec3(0.065, 0.065, 0.08), vec3(0.11, 0.11, 0.13), grid);
        gl_FragColor = vec4(mix(background, c, source.a), 1.0);
    }
}

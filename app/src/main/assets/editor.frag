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
uniform bool uAsciiSymbols;
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
            vec2 pixel = (vTexCoord * uSize
                + (uDitherA.yz - vec2(0.5)) * uSize * 0.42)
                / max(1.0, uPatternScale);
            // Dither scaling happens in image space: controls are independent
            // from the resolution of the preview surface or exported PNG.
            pixel /= exp2((uDitherA.w - 0.5) * 3.1);
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
            float steps = mix(3.0, 11.0, uDitherA.x);
            levels = floor(levels * steps + 0.3) / steps;
            float warm = clamp(0.44 + (c.r - c.b) * 1.32 + (c.r - c.g) * 0.43, 0.0, 1.0);
            vec3 inkColor = neonPalette(levels, warm, rim);

            // Ripple traces: fine rows, with local displacement following
            // tonal surfaces and edge flow. No repeated overlay image.
            float waveAmp = 0.12 + 1.76 * uDitherB.y;
            float flow = waveAmp * (1.25 * sin(pixel.x * 0.12 + pixel.y * 0.027)
                       + 0.54 * sin(pixel.x * 0.27 - pixel.y * 0.031))
                       + 2.15 * light + 1.55 * rim;
            float phase = abs(fract((pixel.y + flow) / 4.2) - 0.5);
            float trace = 1.0 - smoothstep(0.105, 0.31, phase);
            float coverage = clamp((0.08 + signal * 1.06 + rim * 0.48)
                      * mix(0.35, 1.65, uDitherB.x), 0.0, 1.0);
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
            float density = clamp((0.015 + signal * 0.57 + rim * 0.30)
                      * mix(0.20, 1.9, uDitherB.x), 0.0, 0.92);
            float specks = sparkShape * step(hash(cell + 19.1), density) * silhouette;

            float brighten = (0.24 + 1.85 * pow(signal, 0.65) + detail);
            vec3 neon = inkColor * (lines + specks * 1.18) * brighten;

            // Thin, inexpensive emulated neon emission around neighboring
            // bright traces, with a little warm golden ambient sparkle.
            float softLine = 1.0 - smoothstep(0.27, 0.50, phase);
            float ambient = smoothstep(0.12, 0.48, (light + luminance(xc) +
                luminance(xa) + luminance(yc) + luminance(ya)) * 0.2);
            float halo = (softLine * 0.26 + specks * 0.30) * ambient * silhouette;
            // Larger luminous shells follow the same waviness and local tone
            // as the bright lines; never a uniform fog on dark pixels.
            float coreGlow = exp(-pow(phase / 0.245, 2.0));
            float rimGlow = exp(-pow(phase / 0.50, 2.0));
            float luminousLine = clamp(signal * 0.73 + rim * 0.52, 0.0, 1.0);
            neon += inkColor * luminousLine * silhouette *
                (coreGlow * 0.47 + rimGlow * 0.23);
            neon += inkColor * halo * (0.46 + 1.16 * uStyle.y);
            neon += inkColor * smoothstep(0.28, 0.85, ambient) *
                 (0.021 + 0.13 * uStyle.y) * silhouette;
            vec3 dithered = clamp(neon, 0.0, 1.0);
            c = mix(c, dithered, amount);
        }

        if (uStyle.y > 0.001) {
            // Isotropic 8-direction bloom kernel; symmetrical in X and Y.
            // No shifted one-way sampling or motion-blur streaks.
            float r = (1.8 + 11.0 * uStyle.y);
            vec2 uv = vec2(r) / uSize;
            vec2 diag = uv * 0.70710678;
            vec3 bloom = vec3(0.0);
            float energy = 0.0;
            for (int i=0; i<8; i++) {
                vec2 d = vec2(0.0);
                if(i==0)d=vec2(uv.x,0.0);
                if(i==1)d=vec2(-uv.x,0.0);
                if(i==2)d=vec2(0.0,uv.y);
                if(i==3)d=vec2(0.0,-uv.y);
                if(i==4)d=vec2(diag.x,diag.y);
                if(i==5)d=vec2(-diag.x,-diag.y);
                if(i==6)d=vec2(diag.x,-diag.y);
                if(i==7)d=vec2(-diag.x,diag.y);
                vec3 sampleColor = straight(texture2D(uImage,clamp(vTexCoord+d,vec2(0.0),vec2(1.0))));
                float bright = smoothstep(0.43,0.86,luminance(sampleColor));
                bloom += sampleColor * bright;
                energy += bright;
            }
            bloom /= 8.0;
            // Keep the original image sharp: add thresholded scattered light,
            // do not average its base pixels.
            vec3 haloColor = mix(bloom,
                neonPalette(clamp(luminance(bloom)*1.6,0.0,1.0),
                clamp(0.48+(bloom.r-bloom.b)*1.3,0.0,1.0),0.0),
                uStyle.x*0.5);
            c = clamp(c + haloColor * uStyle.y *
                (0.40 + energy * 0.095), 0.0, 1.0);
        }

        // Analog film dust: multiscale fibers, hairline scratches, flecks,
        // translucent spots and grain. Fully deterministic at image coordinates.
        if (uFxA.z > 0.001) {
            vec2 px = vTexCoord*uSize;
            vec2 zone = floor(px/28.0);
            vec2 local = fract(px/28.0);
            vec2 jitter = vec2(hash(zone+vec2(19.7,3.4)),
                               hash(zone+vec2(8.9,74.1)));
            vec2 center = vec2(0.14)+0.72*jitter;
            vec2 dist = local-center;
            float theta = hash(zone+34.8)*6.2831853;
            vec2 axis = vec2(cos(theta),sin(theta));
            vec2 normal = vec2(-axis.y,axis.x);
            float along = dot(dist,axis);
            float across = dot(dist,normal);
            float size = mix(0.026,0.13,hash(zone+116.0));
            float flake = (1.0-smoothstep(size*0.45,size*1.3,length(dist))) *
                step(hash(zone+22.4),0.17+0.57*uFxA.z);
            // Variable-angle thin scratches, broken at random length.
            float fiberLength = mix(0.17,0.87,hash(zone+17.2));
            float line = (1.0-smoothstep(0.007,0.025,abs(across))) *
                (1.0-smoothstep(fiberLength*.55,fiberLength*.80,abs(along))) *
                step(0.955-0.080*uFxA.z,hash(zone+5.2));
            // Sparse soft pinholes and authentic circular emulsion marks.
            vec2 bigZone = floor(px/67.0);
            vec2 bigP = fract(px/67.0)-vec2(hash(bigZone+11.3),hash(bigZone+35.1));
            float rr=length(bigP);
            float ring = exp(-pow((rr-(0.13+hash(bigZone+7.4)*0.12))*43.0,2.0));
            ring *= step(0.88,hash(bigZone+87.2));
            float grain = hash(floor(px)+vec2(65.1,12.8))-0.5;
            float grainAmp=0.045*uFxA.z;
            float dirty = max(flake,line*0.64);
            float warm = hash(zone+92.9);
            vec3 dustInk = mix(vec3(0.09,0.055,0.08),
                               vec3(0.98,0.87,0.69),step(0.32,warm));
            c = mix(c,dustInk,dirty*uFxA.z*0.90);
            c += vec3(0.38,0.31,0.24)*ring*uFxA.z*0.24;
            c += grain*grainAmp;
            c=clamp(c,0.0,1.0);
        }

        // Rotational radial blur: tangent to concentric rings and constrained
        // to the outer image. Angle increases with distance and slider.
        if (uFxB.w > 0.001) {
            vec2 centerVec=(vTexCoord-0.5)*vec2(uSize.x/uSize.y,1.0);
            float radial=length(centerVec);
            float maxRad=length(vec2(0.5*uSize.x/uSize.y,0.5));
            float edge=smoothstep(0.44,0.94,radial/maxRad);
            float angle=0.083*uFxB.w*edge;
            vec3 radialSum=vec3(0.0);
            for(int i=0;i<8;i++){
                float t=(float(i)-3.5)/3.5;
                float a=angle*t;
                float co=cos(a), si=sin(a);
                vec2 rotated=vec2(centerVec.x*co-centerVec.y*si,
                                  centerVec.x*si+centerVec.y*co);
                vec2 uv=clamp(rotated/vec2(uSize.x/uSize.y,1.0)+0.5,
                              vec2(0.0),vec2(1.0));
                radialSum += straight(texture2D(uImage,uv));
            }
            c=mix(c,radialSum/8.0,edge*uFxB.w);
        }

        // Vinheta stays centered in source-image coordinates.
        if (uFxA.w > 0.001) {
            float edgeDistance = length((vTexCoord - 0.5) * 2.0);
            float falloff = smoothstep(0.35, 1.34, edgeDistance);
            c *= 1.0 - 0.93 * uFxA.w * falloff;
        }

        // 01 • Vinyl / topographic concentric engraving.
        // All the knobs affect independent visible properties; this is
        // procedural artwork, not a still image laid over the photograph.
        if (uRingsA.x > 0.001) {
            vec2 center = vec2(uRingsA.w, uRingsB.x);
            vec2 pos=(vTexCoord-center)*uSize;
            float radius=length(pos);
            float angle=atan(pos.y,pos.x);
            float pitch=max(mix(7.0,37.0,uRingsA.y),2.8*uPatternScale);
            float width=max(mix(0.65,4.2,uRingsA.z),0.82*uPatternScale);
            // Subtle vinyl wobble, reduced for closely spaced rings.
            float relief=sin(angle*13.0+radius*.028)*uRingsB.y*1.05
                        +sin(angle*29.0-radius*.019)*uRingsB.y*.55;
            float ringDist=abs(mod(radius+relief+pitch*.5,pitch)-pitch*.5);
            float stroke=1.0-smoothstep(width*.42,width*.42+uPatternScale*.72,ringDist);
            vec2 flakeCell=floor(vTexCoord*uSize/vec2(7.0,13.0));
            float scratch=hash(flakeCell+vec2(29.7,10.2));
            float fineDust=hash(floor(vTexCoord*uSize)+vec2(1.9,81.7));
            // Random tiny breaks, dents and scratches as in pressed records.
            float wear=1.0-uRingsB.y*(
                step(.84,scratch)*.48+step(.961,fineDust)*.34);
            float illuminated=stroke*clamp(wear,0.0,1.0);
            vec3 ringInk=vec3(illuminated*.94+fineDust*uRingsB.y*.018);
            c=mix(c,ringInk,uRingsA.x);
        }

        // 02 • Analog CRT: curved phosphor display / scan lines / black bezel.
        if (uCrtA.x > 0.001) {
            vec2 centered=vTexCoord*2.0-1.0;
            float curv=mix(.0,.25,uCrtA.w);
            vec2 barrel=centered*(1.0+curv*dot(centered,centered));
            vec2 tubeUv=barrel*.5+.5;
            float bezelDist=max(abs(barrel.x)/.89,abs(barrel.y)/.90);
            float feather=mix(.038,.105,uCrtA.z);
            float tubeMask=1.0-smoothstep(1.0-feather,1.0+feather,bezelDist);
            // Row spacing refers to image coordinates and avoids preview moire.
            float scanPitch=max(mix(2.8,12.0,uCrtA.y),uPatternScale*2.4);
            float scanPhase=fract(tubeUv.y*uSize.y/scanPitch);
            float line=1.0-smoothstep(.07,.07+mix(.15,.44,uCrtA.z),
                                      abs(scanPhase-.40));
            float luminanceInput=luminance(straight(texture2D(uImage,
                 clamp(tubeUv,vec2(.0),vec2(1.0)))));
            float signal=clamp(.51 + (luminanceInput-.5)*.77,0.0,1.0);
            float scan=signal*(.19+.93*line);
            float fineGrain=hash(floor(tubeUv*uSize/2.0)+vec2(31.2,78.1))-.5;
            scan=clamp(scan+fineGrain*.038,0.0,1.0);
            vec3 phosphor=mix(vec3(.72,.78,.85),vec3(.90,.82,.96),uCrtB.y);
            vec3 tube=phosphor*scan;
            float corners=pow(clamp(1.0-max(abs(barrel.x),abs(barrel.y))*.72,0.0,1.0),
                              mix(0.3,2.2,uCrtB.x));
            tube*=tubeUv.x>0.0&&tubeUv.x<1.0&&tubeUv.y>0.0&&tubeUv.y<1.0?
                  corners*tubeMask : 0.0;
            c=mix(c,clamp(tube,0.0,1.0),uCrtA.x);
        }

        // 03 • Distorted monochrome signal: band-limited analog tape tearing.
        if (uGlitchA.x > 0.001) {
            vec2 pixel=vTexCoord*uSize;
            float band=max(mix(2.0,27.0,uGlitchA.y),uPatternScale*2.0);
            vec2 stripe=floor(vec2(pixel.y/band,0.0));
            float scramble=hash(stripe+vec2(7.3,5.7));
            float wave=sin(pixel.y*.14+sin(pixel.y*.031)*6.0);
            float interruption=step(1.0-uGlitchA.w*.74,scramble);
            float offset=(wave*.24+interruption*(scramble-.45)*2.0)
                         *uGlitchA.z*uSize.x*.12;
            vec2 warped=clamp(vTexCoord+vec2(offset/uSize.x,0.0),
                               vec2(0.0),vec2(1.0));
            vec3 shifted=straight(texture2D(uImage,warped));
            float lumin=luminance(shifted);
            // Sobel-lite contrast creates high frequency contour stacks.
            vec2 dir=vec2(max(1.0,uPatternScale)*2.0/uSize.x,0.0);
            float edge=abs(luminance(straight(texture2D(uImage,
                           clamp(warped+dir,vec2(.0),vec2(1.0)))))
                     -luminance(straight(texture2D(uImage,
                           clamp(warped-dir,vec2(.0),vec2(1.0))))));
            float ripple=sin(pixel.y/max(1.5,uPatternScale*1.8)*3.14159
                             +wave*1.45)*.12;
            float contrast=clamp((lumin-.5)*2.7+.5+edge*1.55+ripple,0.0,1.0);
            float threshold=hash(floor(pixel/vec2(2.0,3.0)))*
                            uGlitchB.x*.24-.12*uGlitchB.x;
            float monochrome=smoothstep(.43+threshold,.57+threshold,contrast);
            float dropout=step(1.0-uGlitchA.w*.25,
                 hash(stripe+vec2(81.1,22.9)));
            monochrome=mix(monochrome,1.0-monochrome,dropout*.68);
            float staticNoise=hash(floor(pixel)+vec2(71.6,16.2))-.5;
            vec3 signalBW=vec3(clamp(monochrome+staticNoise*uGlitchB.x*.34,0.0,1.0));
            vec3 glitchColor=mix(shifted,signalBW,uGlitchB.y);
            c=mix(c,glitchColor,uGlitchA.x);
        }

        // Full character art, not a text file or an overlay of raw pixels:
        // each image-space cell chooses a real glyph from the GPU atlas using
        // a locally averaged luminance. Every fragment in the cell uses the
        // same character and therefore draws complete, stable letterforms.
        if (uAsciiEnabled) {
            float columns = mix(24.0, 110.0, uAsciiA.x);
            // Monospaced characters are taller than wide; preserve their
            // physical proportions regardless of the source image ratio.
            vec2 gridCount = vec2(columns,
                max(1.0, columns * uSize.y / max(1.0, uSize.x) / 1.36));
            vec2 cell = floor(vTexCoord * gridCount);
            vec2 cellCenter = (cell + 0.5) / gridCount;
            vec2 delta = 0.23 / gridCount;
            vec3 sampleColor = straight(texture2D(uImage,
                clamp(cellCenter, vec2(0.0), vec2(1.0))));
            float light = (
                luminance(straight(texture2D(uImage,clamp(cellCenter+vec2(-delta.x,-delta.y),vec2(0.0),vec2(1.0)))))
              + luminance(straight(texture2D(uImage,clamp(cellCenter+vec2( delta.x,-delta.y),vec2(0.0),vec2(1.0)))))
              + luminance(straight(texture2D(uImage,clamp(cellCenter+vec2(-delta.x, delta.y),vec2(0.0),vec2(1.0)))))
              + luminance(straight(texture2D(uImage,clamp(cellCenter+vec2( delta.x, delta.y),vec2(0.0),vec2(1.0)))))
            ) * 0.25;
            light = clamp((light - 0.5) * mix(0.65, 3.0, uAsciiA.y)
                + 0.5 + (uAsciiA.z-0.5)*1.1 - uAsciiA.w*0.38, 0.0, 1.0);
            float glyphIndex = floor(light * 15.999);
            vec2 local = fract(vTexCoord * gridCount);
            float glyphScale = mix(0.68, 1.20, uAsciiB.y)
                * mix(1.0, 0.58, uAsciiB.x);
            vec2 glyphUv = (local - 0.5) / glyphScale + 0.5;
            vec2 inBounds = step(vec2(0.0), glyphUv)
                          * step(glyphUv, vec2(1.0));
            float row = uAsciiSymbols ? 1.0 : 0.0;
            vec2 atlasUv = (vec2(glyphIndex, row) + clamp(glyphUv,0.0,1.0))
                / vec2(16.0, 2.0);
            float ink = texture2D(uGlyphs, atlasUv).a * inBounds.x * inBounds.y;
            // Black paper by default, white glyphs like the supplied reference.
            vec3 glyphColor = uAsciiColored ?
                clamp(sampleColor * 1.35 + 0.12, 0.0, 1.0) : vec3(1.0);
            c = glyphColor * ink;
        }

        if (uInvert) c = 1.0 - c;
    }

    // ASCII is a fully rasterized photo with an opaque black/white paper
    // background, including when the original input had transparent pixels.
    float outputAlpha = (uAsciiEnabled && !uOriginal) ? 1.0 : source.a;
    if (uExport) {
        gl_FragColor = vec4(outputAlpha > 0.0001 ? c : vec3(0.0), outputAlpha);
    } else {
        float grid = mod(floor(gl_FragCoord.x / 16.0) + floor(gl_FragCoord.y / 16.0), 2.0);
        vec3 background = mix(vec3(0.065, 0.065, 0.08), vec3(0.11, 0.11, 0.13), grid);
        gl_FragColor = vec4(mix(background, c, outputAlpha), 1.0);
    }
}

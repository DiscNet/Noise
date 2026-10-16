#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#ifndef MASS_COUNT
#define MASS_COUNT 10
#endif
varying vec2 vUv;
uniform sampler2D uNoise;
uniform vec4 uMass[MASS_COUNT];
uniform vec4 uShape[MASS_COUNT];
uniform vec4 uViewport; // width / short edge, height / short edge, seconds, detail
uniform vec4 uTouch;    // position and viscous drag in short-edge units
uniform vec4 uState;    // pressure, antialias width, unused, unused

// A seeded, stationary lattice. Quintic interpolation makes advected noise
// temporally coherent; the texture is never regenerated between frames.
vec2 noise2(vec2 p) {
    vec2 f = fract(p);
    vec2 u = f*f*f*(f*(f*6.0-15.0)+10.0);
    return texture2D(uNoise, (floor(p)+u+0.5)/64.0).rg * 2.0-1.0;
}
void main() {
    vec2 uv = vec2(vUv.x, 1.0-vUv.y);
    vec2 p = uv*uViewport.xy;
    float t = uViewport.z;
    vec2 slow = vec2(t*.019, -t*.013);
    vec2 macro = noise2(p*1.65 + slow);
    vec2 meso = noise2(p*4.1 + macro*.85 + vec2(-t*.027,t*.018));
    vec2 warp = .145*macro + .064*meso;
    if (uViewport.w > .001) {
        warp += .021*min(1.0,uViewport.w)*noise2(p*9.0+meso*.7+slow*.7);
    }
    if (uViewport.w > 1.001) {
        warp += .007*(uViewport.w-1.0)*noise2(p*21.0+meso+slow*.4);
    }
    vec2 delta = p-uTouch.xy;
    float influence = exp(-dot(delta,delta)*15.0);
    warp -= influence*(uTouch.zw + delta*uState.x*5.0);
    vec2 q = p+warp;
    float field = 0.0;
    vec3 pigment = vec3(0.0);
    for (int i=0; i<MASS_COUNT; i++) {
        if (uShape[i].z > .001) {
            vec2 d = q-uMass[i].xy;
            vec2 r = vec2(d.x*uShape[i].x+d.y*uShape[i].y,
                         -d.x*uShape[i].y+d.y*uShape[i].x)*uMass[i].zw;
            float support = max(0.0,1.0-dot(r,r));
            float density = support*support*support*uShape[i].z;
            field += density;
            // Each mass carries a pigment. The SAME scalar field weights the
            // mixing, so a growing geometric neck also mixes its two colors.
            float hue = .5+.5*sin(uShape[i].w*8.3+t*.023+meso.x*.4);
            vec3 color = mix(vec3(.035,.047,.087),vec3(.15,.075,.145),hue);
            pigment += color*density;
        }
    }
    float edge = uState.y;
    // Actual silhouette erosion: the ink/paper boundary is defined by a
    // locally varying isovalue of the SAME fluid density, not by a grain
    // overlay on top of an otherwise perfectly smooth outline.
    // Sampling in warped material coordinates keeps chips attached to ink
    // as masses deform, merge and separate (no frame-to-frame random flicker).
    float scallop = noise2(q*27.0 + vec2(3.7,11.9)).x;
    vec2 chips = noise2(q*83.0 + vec2(-5.2,17.7));
    float grit = noise2(q*(122.0 + 33.0*clamp(uViewport.w,0.0,2.0))
                       + vec2(22.0,-9.1)).x;
    // The positive bias eats into the dark shape; negative excursions form
    // tiny surviving ink protrusions. Isolated peaks carve deeper bites.
    float bite = smoothstep(.08,.73,chips.y);
    float erodedIsovalue = .38 + .019 + .027*scallop + .024*chips.x
                        + .016*grit + .043*bite*bite;
    float ink = smoothstep(erodedIsovalue-edge,erodedIsovalue+edge,field);
    vec3 paper = mix(vec3(.963,.956,.948),vec3(.875,.875,.902),uv.y*.65);
    paper += vec3(.013,-.002,.015)*macro.y;
    vec3 color = pigment/max(field,.001);
    color = mix(color,vec3(.015,.017,.024),smoothstep(.5,1.7,field)*.73);
    color += vec3(.065,.052,.085)*(1.0-smoothstep(.38,.9,field))*.38;
    // A faint translucent wash adds depth, without a blur pass or alpha-only fusion.
    float wash = smoothstep(.035,.22,field)*(1.0-ink)*.17;
    paper = mix(paper,vec3(.43,.37,.49),wash);
    // Protect the existing dark header type; the orb and cards are opaque foreground views.
    ink *= smoothstep(.16,.38,p.y);
    vec3 result = mix(paper,color,ink);
    float grain = noise2(q*130.0).x*.003;
    gl_FragColor = vec4(result+grain,1.0);
}

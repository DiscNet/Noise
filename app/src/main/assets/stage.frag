precision mediump float;
varying vec2 uv;
uniform vec2 uCanvas;

// Neutral workspace behind letterboxed images, without neon, particles,
// decorative gradients, grids, translucency or simulated glass.
void main(){
    gl_FragColor=vec4(0.059,0.063,0.071,1.0);
}

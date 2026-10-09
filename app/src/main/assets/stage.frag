precision mediump float;
varying vec2 uv;
uniform vec2 uCanvas;
float hash(vec2 p) {
    return fract(sin(dot(p,vec2(12.9898,78.233))) * 43758.5453);
}
void main(){
    vec2 p=uv*2.0-1.0;
    float aspect=uCanvas.x/max(1.0,uCanvas.y);
    vec3 base=mix(vec3(.041,.039,.071),vec3(.077,.049,.11),uv.y);
    float radial=length(p*vec2(aspect,1.0));
    base*=1.0-.26*smoothstep(.25,1.30,radial);
    float purple=exp(-dot((p-vec2(.68,.50))*vec2(.74,1.3),
                           (p-vec2(.68,.50))*vec2(.74,1.3))*3.2);
    float magenta=exp(-dot((p-vec2(-.95,-.71)),(p-vec2(-.95,-.71)))*2.6);
    base+=vec3(.090,.034,.15)*purple+vec3(.070,.017,.075)*magenta;
    vec2 gridP=uv*uCanvas/31.0;
    vec2 lineDist=abs(fract(gridP)-.5);
    float grid=(1.0-smoothstep(.47,.5,lineDist.x))+
               (1.0-smoothstep(.47,.5,lineDist.y));
    base+=grid*.004;
    float fleck=hash(floor(uv*uCanvas*0.84));
    base+=(fleck-.5)*.014;
    gl_FragColor=vec4(clamp(base,0.0,1.0),1.0);
}

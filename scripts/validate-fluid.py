#!/usr/bin/env python3
"""Render the production field with desktop EGL; Android instrumentation validates GLES2.

Requires: JDK17, numpy, scipy, Pillow, moderngl, ffmpeg (only with --video).
Output is a validation artifact, never a runtime dependency or shipped background video.
"""
import argparse, json, os, pathlib, shutil, subprocess, tempfile, time
import numpy as np
import moderngl
from PIL import Image, ImageDraw
from scipy.ndimage import label

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=pathlib.Path, default=ROOT/'validation')
parser.add_argument('--video', action='store_true')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
jdk = pathlib.Path(os.environ.get('JAVA_HOME', '/usr'))/'bin'
ctx = moderngl.create_standalone_context(backend='egl')
vertex = (ROOT/'app/src/main/assets/fluid.vert').read_text()
fragment = (ROOT/'app/src/main/assets/fluid.frag').read_text()
# Only syntax adaptation for desktop GLSL; all field/noise/color math stays verbatim.
vertex = '#version 330\n'+vertex.replace('attribute ', 'in ').replace('varying ', 'out ')
fragment = '#version 330\nout vec4 fragColor;\n'+fragment.replace('precision highp float;', '').replace('precision mediump float;', '')
fragment = fragment.replace('varying ', 'in ').replace('texture2D(', 'texture(').replace('gl_FragColor', 'fragColor')
program = ctx.program(vertex_shader=vertex, fragment_shader=fragment)
buffer = ctx.buffer(np.array([-1,-1,1,-1,-1,1,1,1], dtype='f4').tobytes())
quad = ctx.simple_vertex_array(program, buffer, 'aPosition')
seed = 0x4e4f4953; lattice = bytearray()
for i in range(64*64*4):
    seed ^= (seed << 13) & 0xffffffff; seed ^= seed >> 17; seed ^= (seed << 5) & 0xffffffff
    lattice.append((seed >> 24) & 255)
noise = ctx.texture((64,64), 4, bytes(lattice)); noise.filter=(moderngl.LINEAR, moderngl.LINEAR)
noise.repeat_x = noise.repeat_y = True; noise.use(0); program['uNoise']=0
program['uTouch'].value=(-10,-10,0,0)
report = {'renderer':ctx.info['GL_RENDERER'], 'notice':'Software/desktop EGL, not physical Android FPS.', 'profiles':{}}

with tempfile.TemporaryDirectory() as temp:
    src = ROOT/'app/src/main/java/com/discnet/noise'
    subprocess.run([str(jdk/'javac'), '-d', temp, *[str(src/f'{n}.java') for n in
        ['FluidField','OrganicMotionController','FluidQualityController']], str(ROOT/'scripts/FluidFieldProbe.java')], check=True)
    def fields(n, fps, width, height, detail):
        data = subprocess.check_output([str(jdk/'java'), '-cp', temp, 'com.discnet.noise.FluidFieldProbe',
                                       str(n), str(fps), str(width), str(height), str(detail)])
        return np.frombuffer(data, dtype='>f4').astype('f4').reshape(n,2,10,4)
    def render(data, seconds, detail, size, pair=False, touch=None):
        masses, shapes = data[0].copy(), data[1].copy()
        if pair:
            shapes[2:,2]=0
            # Center the production pair for a topology test free from screen clipping.
            masses[:2,:2] += np.array([.5,.85])-masses[:2,:2].mean(axis=0)
        program['uMass'].write(masses.tobytes()); program['uShape'].write(shapes.tobytes())
        unit=min(size); program['uViewport'].value=(size[0]/unit,size[1]/unit,seconds,detail)
        program['uTouch'].value=touch or (-10,-10,0,0)
        program['uState'].value=(.08 if touch else 0,max(.009,3/unit),0,0)
        quad.render(moderngl.TRIANGLE_STRIP)
        return np.frombuffer(fbo.read(components=3),np.uint8).reshape(size[1],size[0],3)[::-1].copy()
    sheets=[]
    for detail,name in enumerate(['economy','balanced','high']):
        size=(300,534); fbo=ctx.simple_framebuffer(size); fbo.use()
        samples=fields(241,4,1,size[1]/size[0],detail)
        components=[]; costs=[]
        for i,data in enumerate(samples):
            start=time.perf_counter(); pixels=render(data,i/4,detail,size,pair=True)
            costs.append((time.perf_counter()-start)*1000)
            labeled,count=label(pixels[:,:,0]<95)
            areas=np.bincount(labeled.ravel())[1:]
            components.append(int((areas>100).sum()))
        assert 1 in components and 2 in components, f'{name}: no geometric fusion/split: {set(components)}'
        transitions=sum(a!=b for a,b in zip(components,components[1:]))
        assert transitions>=2, f'{name}: must fuse AND split'
        full=fields(7,.1,1,size[1]/size[0],detail)
        tiles=[]
        for i,data in enumerate(full):
            pixels=render(data,i*10,detail,size)
            image=Image.fromarray(pixels); ImageDraw.Draw(image).text((12,12),f'{name} / {i*10}s',fill='black')
            tiles.append(image)
        sheet=Image.new('RGB',(size[0]*7,size[1]));
        for i,im in enumerate(tiles): sheet.paste(im,(i*size[0],0))
        sheet.save(args.output/f'{name}-sequence.jpg',quality=90)
        report['profiles'][name]={'component_counts':sorted(set(components)), 'topology_transitions':transitions,
            'readback_inclusive_p50_ms':float(np.percentile(costs[5:],50)),
            'readback_inclusive_p95_ms':float(np.percentile(costs[5:],95))}
        # Adjacent frames must be continuous, not a new random field.
        frames=fields(2,60,1,size[1]/size[0],detail)
        a=render(frames[0],0,detail,size); b=render(frames[1],1/60,detail,size)
        report['profiles'][name]['adjacent_frame_mean_delta']=float(np.abs(a.astype(float)-b).mean())
        assert report['profiles'][name]['adjacent_frame_mean_delta']<3
        # Same time is deterministic; a local gesture must change the actual contour.
        assert np.array_equal(a,render(frames[0],0,detail,size))
        touched=render(frames[0],0,detail,size,touch=(.25,.65,.06,.03))
        assert np.count_nonzero(np.abs(touched.astype(float)-a).max(axis=2)>10)>100
        fbo.release()
    # Different viewport ratios must retain negative space and finite silhouettes.
    for size in [(534,300),(360,360),(300,650)]:
        fbo=ctx.simple_framebuffer(size);fbo.use()
        frame=fields(1,1,size[0]/min(size),size[1]/min(size),1)[0]
        pixels=render(frame,0,1,size)
        assert (pixels[:,:,0]<95).sum()>100 and (pixels[:,:,0]>205).sum()>100
        Image.fromarray(pixels).save(args.output/f'aspect-{size[0]}x{size[1]}.png')
        fbo.release()
    if args.video:
        size=(360,640);fps=24;n=48*fps
        fbo=ctx.simple_framebuffer(size);fbo.use()
        samples=fields(n,fps,1,size[1]/size[0],1)
        process=subprocess.Popen(['ffmpeg','-loglevel','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s','360x640',
            '-r',str(fps),'-i','-','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p',
            '-movflags','+faststart',str(args.output/'fluid-field-48s.mp4')],stdin=subprocess.PIPE)
        for i,data in enumerate(samples): process.stdin.write(render(data,i/fps,1,size).tobytes())
        process.stdin.close(); assert process.wait()==0
report['passed']=True
(args.output/'fluid-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))

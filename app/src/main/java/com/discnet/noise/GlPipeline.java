package com.discnet.noise;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLUtils;
import java.io.*;
import java.nio.*;
import java.util.HashMap;
import static android.opengl.GLES20.*;

/** EGL-owned, reusable two-pass renderer. Artistic samples see the tonal result. */
final class GlPipeline {
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final HashMap<String,Integer> uniforms=new HashMap<>(),tones=new HashMap<>();
    private final int program,toneProgram,position,coordinate,tonePosition,toneCoordinate;
    private int texture,glyphTexture,toneTexture,toneFbo,width,height,toneKey;
    private boolean toneValid;
    private int sourceWidth,sourceHeight;
    private static final String[] NAMES={"uImage","uSize","uColor","uTone","uHue","uStyle","uFxA","uFxB","uDitherA","uDitherB","uRingsA","uRingsB","uCrtA","uCrtB","uGlitchA","uGlitchB","uAsciiA","uAsciiB","uAsciiEnabled","uAsciiColored","uAsciiDither","uAsciiSymbols","uGlyphs","uPatternScale","uInvert","uOriginal","uExport","uLight","uGrain","uGlow","uDetail","uDitherMethod","uDitherPalette","uNoiseControl","uHDR","uBlend","uWheelShadows","uWheelMidtones","uWheelHighlights","uCurveHH0","uCurveHH1","uCurveHS0","uCurveHS1","uCurveSS0","uCurveSS1","uFilm"};
    GlPipeline(Context context)throws IOException{
        program=link(read(context,"editor.vert"),read(context,"editor.frag"));
        toneProgram=link(read(context,"editor.vert"),read(context,"tone.frag"));
        position=glGetAttribLocation(program,"aPosition");coordinate=glGetAttribLocation(program,"aTexCoord");
        tonePosition=glGetAttribLocation(toneProgram,"aPosition");toneCoordinate=glGetAttribLocation(toneProgram,"aTexCoord");
        for(String name:NAMES){uniforms.put(name,glGetUniformLocation(program,name));tones.put(name,glGetUniformLocation(toneProgram,name));}
        glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_DITHER);glyphTexture=AsciiAtlas.upload();
    }
    static int maximumTextureSize(){int[] n=new int[1];glGetIntegerv(GL_MAX_TEXTURE_SIZE,n,0);return n[0];}
    void sourceDimensions(int w,int h){sourceWidth=w;sourceHeight=h;}
    void upload(Bitmap bitmap){
        if(bitmap.getWidth()>maximumTextureSize()||bitmap.getHeight()>maximumTextureSize())throw new IllegalArgumentException("Imagem maior que o limite da GPU");
        if(texture==0)texture=newTexture();
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
        if(width!=bitmap.getWidth()||height!=bitmap.getHeight())releaseTone();
        width=bitmap.getWidth();height=bitmap.getHeight();toneValid=false;check("upload");
    }
    private static int newTexture(){int[] id=new int[1];glGenTextures(1,id,0);glBindTexture(GL_TEXTURE_2D,id[0]);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id[0];}
    private void prepareTone(EditState s){
        if(toneValid&&toneKey==s.toneKey())return;
        int[] target=new int[1];glGetIntegerv(GL_FRAMEBUFFER_BINDING,target,0);
        if(toneFbo==0){toneTexture=newTexture();glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
            int[] f=new int[1];glGenFramebuffers(1,f,0);toneFbo=f[0];glBindFramebuffer(GL_FRAMEBUFFER,toneFbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,toneTexture,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Buffer tonal indisponível");}
        glBindFramebuffer(GL_FRAMEBUFFER,toneFbo);glViewport(0,0,width,height);glUseProgram(toneProgram);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glUniform1i(tones.get("uImage"),0);
        glUniform2f(tones.get("uSize"),width,height);
        glUniform4fv(tones.get("uColor"),1,s.color,0);glUniform4fv(tones.get("uTone"),1,s.tone,0);
        glUniform2fv(tones.get("uHue"),1,s.hue,0);glUniform4fv(tones.get("uLight"),1,s.light,0);
        glUniform2fv(tones.get("uNoiseControl"),1,s.noiseControls,0);
        glUniform2fv(tones.get("uHDR"),1,s.hdrControls,0);
        glUniform4fv(tones.get("uFilm"),1,s.film,0);
        glUniform2fv(tones.get("uBlend"),1,s.blend,0);
        for(int i=0;i<3;i++){
            String[] wheelNames={"uWheelShadows","uWheelMidtones","uWheelHighlights"};
            glUniform2fv(tones.get(wheelNames[i]),1,s.colorWheels,i*2);
        }
        String[] curveNames={"uCurveHH0","uCurveHH1","uCurveHS0","uCurveHS1","uCurveSS0","uCurveSS1"};
        for(int i=0;i<6;i++)glUniform3fv(tones.get(curveNames[i]),1,s.colorCurves,i*3);
        // FBO row zero holds source row zero, like Android's uploaded texture.
        vertices.position(0);vertices.put(new float[]{-1,-1,0,0,1,-1,1,0,-1,1,0,1,1,1,1,1});
        drawQuad(tonePosition,toneCoordinate);glBindFramebuffer(GL_FRAMEBUFFER,target[0]);toneKey=s.toneKey();toneValid=true;
    }
    void draw(int w,int h,EditState s,boolean export){draw(w,h,s,export,1,0,0);}
    void draw(int w,int h,EditState s,boolean export,float zoom,float panX,float panY){
        if(texture!=0&&!s.original)prepareTone(s);
        glViewport(0,0,w,h);glClearColor(.055f,.059f,.067f,1);glClear(GL_COLOR_BUFFER_BIT);
        if(texture==0)return;
        float sw=sourceWidth>0?sourceWidth:width,sh=sourceHeight>0?sourceHeight:height;
        float cw=sw*(s.right-s.left),ch=sh*(s.bottom-s.top);if(s.rotation%2==1){float t=cw;cw=ch;ch=t;}
        float sx=1,sy=1;
        if(!export){float ir=cw/ch,sr=(float)w/h;if(ir>sr)sy=sr/ir;else sx=ir/sr;sx*=zoom;sy*=zoom;}
        float px=export?0:Math.max(-Math.max(0,sx-1),Math.min(Math.max(0,sx-1),panX));
        float py=export?0:Math.max(-Math.max(0,sy-1),Math.min(Math.max(0,sy-1),panY));
        vertices.position(0);vertex(-sx+px,-sy+py,0,1,s);vertex(sx+px,-sy+py,1,1,s);vertex(-sx+px,sy+py,0,0,s);vertex(sx+px,sy+py,1,0,s);
        glUseProgram(program);glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,glyphTexture);glUniform1i(uniforms.get("uGlyphs"),1);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,s.original?texture:toneTexture);glUniform1i(uniforms.get("uImage"),0);
        // One canonical image-space grid at a 1600px long edge, independent of viewport/zoom/export.
        float factor=Math.min(1,1600f/Math.max(sw,sh));glUniform2f(uniforms.get("uSize"),sw*factor,sh*factor);
        glUniform4fv(uniforms.get("uColor"),1,s.color,0);glUniform4fv(uniforms.get("uTone"),1,s.tone,0);glUniform2fv(uniforms.get("uHue"),1,s.hue,0);
        glUniform3fv(uniforms.get("uStyle"),1,s.style,0);glUniform4fv(uniforms.get("uFxA"),1,s.fxA,0);glUniform4fv(uniforms.get("uFxB"),1,s.fxB,0);
        glUniform4fv(uniforms.get("uDitherA"),1,s.ditherA,0);glUniform2fv(uniforms.get("uDitherB"),1,s.ditherB,0);
        glUniform4fv(uniforms.get("uRingsA"),1,s.ringsA,0);glUniform2fv(uniforms.get("uRingsB"),1,s.ringsB,0);
        glUniform4fv(uniforms.get("uCrtA"),1,s.crtA,0);glUniform2fv(uniforms.get("uCrtB"),1,s.crtB,0);
        glUniform4fv(uniforms.get("uGlitchA"),1,s.glitchA,0);glUniform2fv(uniforms.get("uGlitchB"),1,s.glitchB,0);
        glUniform4fv(uniforms.get("uAsciiA"),1,s.asciiA,0);glUniform2fv(uniforms.get("uAsciiB"),1,s.asciiB,0);
        glUniform2fv(uniforms.get("uHDR"),1,s.hdrControls,0);
        glUniform4fv(uniforms.get("uFilm"),1,s.film,0);
        glUniform4fv(uniforms.get("uGrain"),1,s.grain,0);glUniform2fv(uniforms.get("uGlow"),1,s.glow,0);glUniform2fv(uniforms.get("uDetail"),1,s.detail,0);
        glUniform1i(uniforms.get("uDitherMethod"),s.ditherMethod);glUniform1i(uniforms.get("uDitherPalette"),s.ditherPalette);
        flag("uAsciiEnabled",s.asciiEnabled);flag("uAsciiColored",s.asciiColored);flag("uAsciiDither",s.asciiDither);flag("uAsciiSymbols",s.asciiSymbols);
        flag("uInvert",s.invert);flag("uOriginal",s.original);flag("uExport",export);glUniform1f(uniforms.get("uPatternScale"),1);
        drawQuad(position,coordinate);check("draw");
    }
    private void flag(String n,boolean v){glUniform1i(uniforms.get(n),v?1:0);}
    private void vertex(float px,float py,float x,float y,EditState s){
        if(s.flipX)x=1-x;if(s.flipY)y=1-y;
        float u=x,v=y;
        if(s.rotation==1){u=y;v=1-x;}else if(s.rotation==2){u=1-x;v=1-y;}else if(s.rotation==3){u=1-y;v=x;}
        vertices.put(px).put(py).put(s.left+u*(s.right-s.left)).put(s.top+v*(s.bottom-s.top));
    }
    private void drawQuad(int p,int t){vertices.position(0);glVertexAttribPointer(p,2,GL_FLOAT,false,16,vertices);glEnableVertexAttribArray(p);
        vertices.position(2);glVertexAttribPointer(t,2,GL_FLOAT,false,16,vertices);glEnableVertexAttribArray(t);glDrawArrays(GL_TRIANGLE_STRIP,0,4);glDisableVertexAttribArray(p);glDisableVertexAttribArray(t);}
    private void releaseTone(){if(toneTexture!=0)glDeleteTextures(1,new int[]{toneTexture},0);if(toneFbo!=0)glDeleteFramebuffers(1,new int[]{toneFbo},0);toneTexture=toneFbo=0;toneValid=false;}
    void release(){releaseTone();if(texture!=0)glDeleteTextures(1,new int[]{texture},0);if(glyphTexture!=0)glDeleteTextures(1,new int[]{glyphTexture},0);glDeleteProgram(program);glDeleteProgram(toneProgram);}
    static void check(String op){int e=glGetError();if(e!=GL_NO_ERROR)throw new IllegalStateException(op+": GL "+e);}
    private static int link(String v,String f){int vs=compile(GL_VERTEX_SHADER,v),fs=compile(GL_FRAGMENT_SHADER,f),p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);glDeleteShader(vs);glDeleteShader(fs);int[] ok=new int[1];glGetProgramiv(p,GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(glGetProgramInfoLog(p));return p;}
    private static int compile(int type,String text){int s=glCreateShader(type);glShaderSource(s,text);glCompileShader(s);int[] ok=new int[1];glGetShaderiv(s,GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String log=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    private static String read(Context c,String name)throws IOException{try(InputStream in=c.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
}

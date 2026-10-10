package com.discnet.noise;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import java.io.*;
import java.nio.*;
import java.util.HashMap;
import static android.opengl.GLES20.*;

/** Owned by its current EGL context. Shared shader for live preview and PNG export. */
final class GlPipeline {
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final HashMap<String, Integer> uniforms = new HashMap<>();
    private int program, texture, glyphTexture, position, coordinate;
    private int stageProgram, stagePosition, stageCanvas;
    private int width, height;
    GlPipeline(Context context) throws IOException {
        int vs = compile(GL_VERTEX_SHADER, read(context, "editor.vert"));
        int fs = compile(GL_FRAGMENT_SHADER, read(context, "editor.frag"));
        program = glCreateProgram();
        glAttachShader(program, vs); glAttachShader(program, fs); glLinkProgram(program);
        int[] success = new int[1]; glGetProgramiv(program, GL_LINK_STATUS, success, 0);
        glDeleteShader(vs); glDeleteShader(fs);
        if (success[0] == 0) throw new IllegalStateException("Shader link: " + glGetProgramInfoLog(program));
        // A separate inexpensive shader fills unused space around the photo
        // with a textured luminous studio backdrop; export remains transparent.
        int sv=compile(GL_VERTEX_SHADER,read(context,"stage.vert"));
        int sf=compile(GL_FRAGMENT_SHADER,read(context,"stage.frag"));
        stageProgram=glCreateProgram();
        glAttachShader(stageProgram,sv);glAttachShader(stageProgram,sf);
        glLinkProgram(stageProgram);
        int[] stageLinked=new int[1];
        glGetProgramiv(stageProgram,GL_LINK_STATUS,stageLinked,0);
        glDeleteShader(sv);glDeleteShader(sf);
        if(stageLinked[0]==0)throw new IllegalStateException("Stage shader: "+glGetProgramInfoLog(stageProgram));
        stagePosition=glGetAttribLocation(stageProgram,"aPosition");
        stageCanvas=glGetUniformLocation(stageProgram,"uCanvas");
        position = glGetAttribLocation(program, "aPosition");
        coordinate = glGetAttribLocation(program, "aTexCoord");
        for (String name : new String[]{"uImage", "uSize", "uColor", "uTone", "uHue", "uStyle", "uFxA", "uFxB", "uDitherA", "uDitherB", "uRingsA", "uRingsB", "uCrtA", "uCrtB", "uGlitchA", "uGlitchB", "uAsciiA", "uAsciiB", "uAsciiEnabled", "uAsciiColored", "uAsciiSymbols", "uGlyphs", "uPatternScale", "uInvert", "uOriginal", "uExport"})
            uniforms.put(name, glGetUniformLocation(program, name));
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND); glDisable(GL_DITHER);
        glyphTexture = AsciiAtlas.upload();
    }
    static int maximumTextureSize() { int[] limit = new int[1]; glGetIntegerv(GL_MAX_TEXTURE_SIZE, limit, 0); return limit[0]; }
    void upload(Bitmap bitmap) {
        if (bitmap.getWidth() > maximumTextureSize() || bitmap.getHeight() > maximumTextureSize())
            throw new IllegalArgumentException("Imagem maior que o limite desta GPU");
        if (texture != 0) glDeleteTextures(1, new int[]{texture}, 0);
        int[] id = new int[1]; glGenTextures(1, id, 0); texture = id[0];
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0);
        width = bitmap.getWidth(); height = bitmap.getHeight();
        check("upload");
    }
    void draw(int outputWidth, int outputHeight, EditState state, boolean export) {
        glViewport(0, 0, outputWidth, outputHeight);
        glClearColor(0.050f, 0.046f, 0.073f, 1); glClear(GL_COLOR_BUFFER_BIT);
        if(!export) {
            glUseProgram(stageProgram);
            vertices.position(0);
            vertices.put(new float[]{-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1}).position(0);
            glUniform2f(stageCanvas,outputWidth,outputHeight);
            glVertexAttribPointer(stagePosition,2,GL_FLOAT,false,16,vertices);
            glEnableVertexAttribArray(stagePosition);
            glDrawArrays(GL_TRIANGLE_STRIP,0,4);
            glDisableVertexAttribArray(stagePosition);
        }
        if (texture == 0) return;
        float sx = 1, sy = 1;
        if (!export) {
            float imageRatio = (float)width / height, surfaceRatio = (float)outputWidth / outputHeight;
            // Crop to fill: no letterboxing/black bars in portrait or landscape.
            if (imageRatio > surfaceRatio) sy = surfaceRatio / imageRatio; else sx = imageRatio / surfaceRatio;
        }
        // Android texture top is v=0; OpenGL framebuffer top is positive y.
        vertices.position(0);
        // Preserve complete image and original aspect ratio (fit-center, no crop).
        vertices.put(new float[]{-sx,-sy,0,1, sx,-sy,1,1,
                                  -sx,sy,0,0, sx,sy,1,0}).position(0);
        glUseProgram(program);
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, glyphTexture);
        glUniform1i(uniforms.get("uGlyphs"), 1);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(uniforms.get("uImage"), 0); glUniform2f(uniforms.get("uSize"), width, height);
        glUniform4fv(uniforms.get("uColor"), 1, state.color, 0);
        glUniform4fv(uniforms.get("uTone"), 1, state.tone, 0);
        glUniform2fv(uniforms.get("uHue"), 1, state.hue, 0);
        glUniform3fv(uniforms.get("uStyle"), 1, state.style, 0);
        glUniform4fv(uniforms.get("uFxA"), 1, state.fxA, 0);
        glUniform4fv(uniforms.get("uFxB"), 1, state.fxB, 0);
        glUniform4fv(uniforms.get("uDitherA"),1,state.ditherA,0);
        glUniform2fv(uniforms.get("uDitherB"),1,state.ditherB,0);
        glUniform4fv(uniforms.get("uRingsA"),1,state.ringsA,0);
        glUniform2fv(uniforms.get("uRingsB"),1,state.ringsB,0);
        glUniform4fv(uniforms.get("uCrtA"),1,state.crtA,0);
        glUniform2fv(uniforms.get("uCrtB"),1,state.crtB,0);
        glUniform4fv(uniforms.get("uGlitchA"),1,state.glitchA,0);
        glUniform2fv(uniforms.get("uGlitchB"),1,state.glitchB,0);
        glUniform4fv(uniforms.get("uAsciiA"),1,state.asciiA,0);
        glUniform2fv(uniforms.get("uAsciiB"),1,state.asciiB,0);
        glUniform1i(uniforms.get("uAsciiEnabled"),state.asciiEnabled?1:0);
        glUniform1i(uniforms.get("uAsciiColored"),state.asciiColored?1:0);
        glUniform1i(uniforms.get("uAsciiSymbols"),state.asciiSymbols?1:0);
        // Band-limited procedural traces: one wave stays several screen pixels
        // wide while the export keeps original-resolution detail.
        float patternScale = export ? 1f : Math.max(1f, Math.max(
            width / Math.max(1f, outputWidth*sx),
            height / Math.max(1f, outputHeight*sy)));
        glUniform1f(uniforms.get("uPatternScale"), patternScale);
        glUniform1i(uniforms.get("uInvert"), state.invert ? 1 : 0);
        glUniform1i(uniforms.get("uOriginal"), state.original ? 1 : 0);
        glUniform1i(uniforms.get("uExport"), export ? 1 : 0);
        vertices.position(0); glVertexAttribPointer(position, 2, GL_FLOAT, false, 16, vertices); glEnableVertexAttribArray(position);
        vertices.position(2); glVertexAttribPointer(coordinate, 2, GL_FLOAT, false, 16, vertices); glEnableVertexAttribArray(coordinate);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
    void release() {
        if (texture != 0) glDeleteTextures(1, new int[]{texture}, 0);
        if (glyphTexture != 0) glDeleteTextures(1, new int[]{glyphTexture}, 0);
        if (program != 0) glDeleteProgram(program);
        if (stageProgram != 0) glDeleteProgram(stageProgram);
    }
    static void check(String operation) { int error = glGetError(); if (error != GL_NO_ERROR) throw new IllegalStateException(operation + ": GL " + error); }
    private static int compile(int kind, String source) {
        int shader = glCreateShader(kind); glShaderSource(shader, source); glCompileShader(shader);
        int[] success = new int[1]; glGetShaderiv(shader, GL_COMPILE_STATUS, success, 0);
        if (success[0] == 0) { String log = glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalStateException("Shader: " + log); }
        return shader;
    }
    private static String read(Context context, String asset) throws IOException {
        try (InputStream stream = context.getAssets().open(asset); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int size;
            while ((size = stream.read(buffer)) != -1) out.write(buffer, 0, size);
            return out.toString("UTF-8");
        }
    }
}

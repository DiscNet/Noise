package com.discnet.noise;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLUtils;
import static android.opengl.GLES20.*;

/** Small generated glyph texture. It is reused for every frame, never rendered on the UI thread. */
final class AsciiAtlas {
    static final int GLYPHS = 16;
    private static final int CELL_WIDTH = 24, CELL_HEIGHT = 32;
    private static final String[] SETS = {" .,:;i!lI/)(?%#@", " .`'^*+=xX$&8B#@"};

    static int upload() {
        Bitmap atlas = Bitmap.createBitmap(GLYPHS * CELL_WIDTH,
            SETS.length * CELL_HEIGHT, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(atlas);
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            paint.setTextSize(25f);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = (CELL_HEIGHT - metrics.ascent - metrics.descent) * .5f;
            for (int row=0; row<SETS.length; row++) {
                for (int col=0; col<GLYPHS; col++) {
                    canvas.drawText(SETS[row].substring(col,col+1),
                        (col+.5f)*CELL_WIDTH, row*CELL_HEIGHT+baseline, paint);
                }
            }
            int[] id = new int[1];
            glGenTextures(1,id,0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D,id[0]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GL_TEXTURE_2D,0,atlas,0);
            glBindTexture(GL_TEXTURE_2D,0);
            glActiveTexture(GL_TEXTURE0);
            GlPipeline.check("ASCII glyph atlas");
            return id[0];
        } finally {
            atlas.recycle();
        }
    }
    private AsciiAtlas() {}
}

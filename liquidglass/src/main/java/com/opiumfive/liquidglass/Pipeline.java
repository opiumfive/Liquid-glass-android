package com.opiumfive.liquidglass;

import android.graphics.Canvas;

public interface Pipeline {
    void onSizeChanged(int w, int h);

    void onPreDraw();

    void draw(Canvas c);

    void dispose();
}

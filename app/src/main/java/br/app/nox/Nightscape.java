package br.app.nox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Decorative landscape. Drawn once per layout size; all coordinates are proportions. */
public final class Nightscape extends View {
    private final boolean dawn;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path far = new Path();
    private final Path middle = new Path();
    private final Path near = new Path();
    private final Path moon = new Path();
    private LinearGradient sky;
    private LinearGradient ridge;
    private LinearGradient foreground;
    private RadialGradient light;
    private float centerX;
    private float centerY;
    private float radius;
    private float starUnit;

    public Nightscape(Context context, boolean dawn) {
        super(context);
        this.dawn = dawn;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        if (w <= 0 || h <= 0) return;
        centerX = 0.77f * w;
        centerY = (dawn ? 0.66f : 0.36f) * h;
        radius = Math.min(0.089f * w, 0.19f * h);
        starUnit = Math.max(1f, Math.min(0.012f * w, 0.029f * h));
        sky = new LinearGradient(0f, 0f, 0.82f * w, h, dawn
                ? new int[]{0xff383057, 0xff7a6188, 0xffd39495}
                : new int[]{0xff101630, 0xff222659, 0xff4c4381},
                null, Shader.TileMode.CLAMP);
        light = new RadialGradient(centerX, centerY, Math.max(radius * 3.7f, 1f), dawn
                ? new int[]{0xefffbd95, 0x6deaa38c, 0x00eaa38c}
                : new int[]{0x7dafa9fc, 0x328e7de0, 0x008e7de0},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        ridge = new LinearGradient(0f, 0f, w, h, dawn
                ? new int[]{0xff514465, 0xff8d6786, 0xff68557f}
                : new int[]{0xff252957, 0xff6261b9, 0xff615192},
                null, Shader.TileMode.CLAMP);
        foreground = new LinearGradient(0f, 0.72f * h, 0.25f * w, h, dawn
                ? 0xff3c3554 : 0xff292e60, dawn ? 0xff232039 : 0xff151934,
                Shader.TileMode.CLAMP);

        far.reset();
        far.moveTo(0, 0.68f * h);
        far.cubicTo(0.15f * w, 0.48f * h, 0.22f * w, 0.59f * h, 0.43f * w, 0.70f * h);
        far.cubicTo(0.64f * w, 0.85f * h, 0.84f * w, 0.50f * h, w, 0.49f * h);
        closeBottom(far, w, h);
        middle.reset();
        middle.moveTo(0, 0.72f * h);
        middle.cubicTo(0.17f * w, 0.77f * h, 0.25f * w, 0.54f * h, 0.43f * w, 0.67f * h);
        middle.cubicTo(0.68f * w, 0.91f * h, 0.81f * w, 0.67f * h, w, 0.71f * h);
        closeBottom(middle, w, h);
        near.reset();
        near.moveTo(0, 0.80f * h);
        near.cubicTo(0.20f * w, 0.69f * h, 0.37f * w, 0.90f * h, 0.58f * w, 0.86f * h);
        near.cubicTo(0.76f * w, 0.84f * h, 0.90f * w, 0.72f * h, w, 0.78f * h);
        closeBottom(near, w, h);
        float x = centerX;
        float y = centerY;
        float r = radius;
        moon.reset();
        moon.moveTo(x + 0.26f*r, y-r);
        moon.cubicTo(x-0.47f*r,y-0.93f*r,x-1.11f*r,y-0.36f*r,x-r,y+0.25f*r);
        moon.cubicTo(x-0.8f*r,y+1.17f*r,x+0.49f*r,y+1.35f*r,x+0.97f*r,y+0.32f*r);
        moon.cubicTo(x+0.23f*r,y+0.66f*r,x-0.54f*r,y-0.09f*r,x+0.26f*r,y-r);
        moon.close();
    }

    private static void closeBottom(Path path, int width, int height) {
        path.lineTo(width, height);
        path.lineTo(0, height);
        path.close();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sky == null) return;
        int w = getWidth(), h = getHeight();
        paint.setColor(Color.WHITE);
        paint.setShader(sky);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(light);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        paint.setColor(dawn ? 0xffffd5aa : 0xffe9e8ff);
        if (dawn) {
            canvas.drawCircle(centerX, centerY, radius, paint);
        } else {
            canvas.drawPath(moon, paint);
            paint.setColor(0xffbdb8df);
            canvas.drawCircle(0.83f*w, 0.19f*h, starUnit*0.38f, paint);
            canvas.drawCircle(0.60f*w, 0.33f*h, starUnit*0.25f, paint);
            canvas.drawCircle(0.90f*w, 0.38f*h, starUnit*0.27f, paint);
        }
        paint.setColor(Color.WHITE);
        paint.setShader(ridge);
        canvas.drawPath(far, paint);
        paint.setShader(null);
        paint.setColor(dawn ? 0xff534461 : 0xff353768);
        canvas.drawPath(middle, paint);
        paint.setColor(Color.WHITE);
        paint.setShader(foreground);
        canvas.drawPath(near, paint);
        paint.setShader(null);
    }
}

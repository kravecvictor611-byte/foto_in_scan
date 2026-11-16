package com.vladimir.photopdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class CropView extends View {
    private final Bitmap bitmap;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();
    float left, top, right, bottom;
    int filter;
    private int drag = -1;
    private float lastX, lastY;

    CropView(Context context, Bitmap bitmap, PhotoPage page) {
        super(context);
        this.bitmap = bitmap;
        left = page.left; top = page.top; right = page.right; bottom = page.bottom;
        filter = page.filter;
        shade.setColor(0x99000000);
        border.setColor(Color.WHITE);
        border.setStrokeWidth(dp(2));
        border.setStyle(Paint.Style.STROKE);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF202430);
        float scale = Math.min((getWidth() - dp(20)) / bitmap.getWidth(),
                (getHeight() - dp(20)) / bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        imageBounds.set((getWidth() - w) / 2, (getHeight() - h) / 2,
                (getWidth() + w) / 2, (getHeight() + h) / 2);
        imagePaint.setColorFilter(ImageTools.paint(filter).getColorFilter());
        canvas.drawBitmap(bitmap, null, imageBounds, imagePaint);
        float l = px(left), t = py(top), r = px(right), b = py(bottom);
        canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, t, shade);
        canvas.drawRect(imageBounds.left, b, imageBounds.right, imageBounds.bottom, shade);
        canvas.drawRect(imageBounds.left, t, l, b, shade);
        canvas.drawRect(r, t, imageBounds.right, b, shade);
        canvas.drawRect(l, t, r, b, border);
        border.setStyle(Paint.Style.FILL);
        float handle = dp(6);
        canvas.drawCircle(l, t, handle, border);
        canvas.drawCircle(r, t, handle, border);
        canvas.drawCircle(l, b, handle, border);
        canvas.drawCircle(r, b, handle, border);
        border.setStyle(Paint.Style.STROKE);
    }

    private float px(float n) { return imageBounds.left + n * imageBounds.width(); }
    private float py(float n) { return imageBounds.top + n * imageBounds.height(); }
    private float nx(float x) { return clamp((x - imageBounds.left) / imageBounds.width(), 0, 1); }
    private float ny(float y) { return clamp((y - imageBounds.top) / imageBounds.height(), 0, 1); }
    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float radius = dp(42), best = radius * radius;
            float[] xs = {px(left), px(right), px(left), px(right)};
            float[] ys = {py(top), py(top), py(bottom), py(bottom)};
            drag = -1;
            for (int i = 0; i < 4; i++) {
                float d = (x - xs[i]) * (x - xs[i]) + (y - ys[i]) * (y - ys[i]);
                if (d < best) { best = d; drag = i; }
            }
            if (drag < 0 && x >= px(left) && x <= px(right) && y >= py(top) && y <= py(bottom)) drag = 4;
            lastX = nx(x); lastY = ny(y);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && drag >= 0) {
            float xx = nx(x), yy = ny(y), minW = .08f, minH = .08f;
            if (drag == 0 || drag == 2) left = clamp(xx, 0, right - minW);
            if (drag == 1 || drag == 3) right = clamp(xx, left + minW, 1);
            if (drag == 0 || drag == 1) top = clamp(yy, 0, bottom - minH);
            if (drag == 2 || drag == 3) bottom = clamp(yy, top + minH, 1);
            if (drag == 4) {
                float dx = clamp(xx - lastX, -left, 1 - right);
                float dy = clamp(yy - lastY, -top, 1 - bottom);
                left += dx; right += dx; top += dy; bottom += dy;
            }
            lastX = xx; lastY = yy;
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            drag = -1; performClick(); return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}

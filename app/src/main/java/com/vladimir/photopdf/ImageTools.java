package com.vladimir.photopdf;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.ImageDecoder;
import android.net.Uri;

import java.io.IOException;

final class ImageTools {
    private ImageTools() {}

    static Bitmap decode(ContentResolver resolver, Uri uri, int maxSide) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
        try {
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                float scale = Math.min(1f, maxSide / (float) Math.max(w, h));
                if (scale < 1f) decoder.setTargetSize(Math.max(1, Math.round(w * scale)),
                        Math.max(1, Math.round(h * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        } catch (android.graphics.ImageDecoder.DecodeException e) {
            throw new IOException("Не удалось открыть изображение", e);
        }
    }

    static Bitmap edited(Bitmap source, PhotoPage page) {
        int x = Math.min(source.getWidth() - 1, Math.max(0, Math.round(page.left * source.getWidth())));
        int y = Math.min(source.getHeight() - 1, Math.max(0, Math.round(page.top * source.getHeight())));
        int r = Math.min(source.getWidth(), Math.max(x + 1, Math.round(page.right * source.getWidth())));
        int b = Math.min(source.getHeight(), Math.max(y + 1, Math.round(page.bottom * source.getHeight())));
        Bitmap crop = Bitmap.createBitmap(source, x, y, r - x, b - y);
        if (page.rotation == 0) return crop;
        Matrix matrix = new Matrix();
        matrix.postRotate(page.rotation);
        Bitmap rotated = Bitmap.createBitmap(crop, 0, 0, crop.getWidth(), crop.getHeight(), matrix, true);
        if (crop != source && crop != rotated) crop.recycle();
        return rotated;
    }

    static Paint paint(int filter) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (filter == 1) {
            ColorMatrix gray = new ColorMatrix();
            gray.setSaturation(0);
            p.setColorFilter(new ColorMatrixColorFilter(gray));
        } else if (filter == 2) {
            // Strong monochrome document contrast.
            ColorMatrix gray = new ColorMatrix();
            gray.setSaturation(0);
            ColorMatrix contrast = new ColorMatrix(new float[]{
                    1.65f,0,0,0,-82, 0,1.65f,0,0,-82,
                    0,0,1.65f,0,-82, 0,0,0,1,0});
            gray.postConcat(contrast);
            p.setColorFilter(new ColorMatrixColorFilter(gray));
        } else if (filter == 3) {
            // Soft color scan: brighten paper while keeping color detail.
            ColorMatrix scan = new ColorMatrix();
            scan.setSaturation(0.88f);
            ColorMatrix contrast = new ColorMatrix(new float[]{
                    1.12f,0,0,0,-11, 0,1.12f,0,0,-11,
                    0,0,1.12f,0,-11, 0,0,0,1,0});
            scan.postConcat(contrast);
            p.setColorFilter(new ColorMatrixColorFilter(scan));
        } else if (filter == 4) {
            // Softer than "Document": white paper, black ink and retained
            // midtones for fine print. No thresholding or extra resizing.
            ColorMatrix scan = new ColorMatrix();
            scan.setSaturation(0);
            ColorMatrix contrast = new ColorMatrix(new float[]{
                    1.31f,0,0,0,-30, 0,1.31f,0,0,-30,
                    0,0,1.31f,0,-30, 0,0,0,1,0});
            scan.postConcat(contrast);
            p.setColorFilter(new ColorMatrixColorFilter(scan));
        }
        return p;
    }

    static Bitmap preview(Bitmap source, PhotoPage page, int maxSide) {
        Bitmap edited = edited(source, page);
        float scale = Math.min(1f, maxSide / (float) Math.max(edited.getWidth(), edited.getHeight()));
        int w = Math.max(1, Math.round(edited.getWidth() * scale));
        int h = Math.max(1, Math.round(edited.getHeight() * scale));
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(android.graphics.Color.WHITE);
        canvas.drawBitmap(edited, null, new RectF(0, 0, w, h), paint(page.filter));
        if (edited != source) edited.recycle();
        return output;
    }
}

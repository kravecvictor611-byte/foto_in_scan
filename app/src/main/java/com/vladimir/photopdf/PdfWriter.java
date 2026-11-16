package com.vladimir.photopdf;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

final class PdfWriter {
    private PdfWriter() {}

    // pageSize: A4 = 0, US Letter = 1, photo proportion = 2.
    static void write(ContentResolver resolver, List<PhotoPage> pages, int pageSize,
                      int margin, File output) throws IOException {
        PdfDocument document = new PdfDocument();
        try {
            for (int i = 0; i < pages.size(); i++) {
                PhotoPage item = pages.get(i);
                Bitmap decoded = ImageTools.decode(resolver, item.uri, 3508);
                Bitmap bitmap = null;
                try {
                    bitmap = ImageTools.edited(decoded, item);
                    boolean landscape = bitmap.getWidth() > bitmap.getHeight();
                    int width, height;
                    if (pageSize == 2) {
                        width = 595;
                        height = Math.max(1, Math.round(width * bitmap.getHeight() / (float) bitmap.getWidth()));
                    } else {
                        width = pageSize == 0 ? 595 : 612;
                        height = pageSize == 0 ? 842 : 792;
                        if (landscape) { int temp = width; width = height; height = temp; }
                    }
                    int inset = Math.min(margin, Math.min(width, height) / 4);
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(width, height, i + 1).create();
                    PdfDocument.Page pdfPage = document.startPage(info);
                    Canvas canvas = pdfPage.getCanvas();
                    canvas.drawColor(Color.WHITE);
                    float availableW = width - inset * 2f;
                    float availableH = height - inset * 2f;
                    float scale = Math.min(availableW / bitmap.getWidth(), availableH / bitmap.getHeight());
                    float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
                    float left = (width - w) / 2f, top = (height - h) / 2f;
                    canvas.drawBitmap(bitmap, null, new RectF(left, top, left + w, top + h),
                            ImageTools.paint(item.filter));
                    document.finishPage(pdfPage);
                } finally {
                    if (bitmap != null && bitmap != decoded) bitmap.recycle();
                    decoded.recycle();
                }
            }
            try (FileOutputStream stream = new FileOutputStream(output)) {
                document.writeTo(stream);
            }
        } finally {
            document.close();
        }
    }
}

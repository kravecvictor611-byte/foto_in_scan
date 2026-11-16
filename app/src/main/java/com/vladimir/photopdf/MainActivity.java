package com.vladimir.photopdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.DashPathEffect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import androidx.core.content.FileProvider;
import android.graphics.pdf.PdfRenderer;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.WindowInsets;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK = 1, SAVE = 2, CAPTURE = 3;
    private final ArrayList<PhotoPage> pages = new ArrayList<>();
    private LinearLayout rows;
    private TextView count;
    private Button saveButton, previewButton;
    private Spinner sizeChoice, marginChoice;
    private File preparedPdf;
    private Uri pendingCaptureUri;
    private File pendingCaptureFile;
    private boolean busy;
    private final int ink = 0xFF17223A;
    private final int blue = 0xFF255DCE;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            ArrayList<Bundle> savedPages = state.getParcelableArrayList("photoPages");
            if (savedPages != null) for (Bundle saved : savedPages) {
                String source = saved.getString("uri");
                if (source == null) continue;
                Uri uri = Uri.parse(source);
                if (!"content".equals(uri.getScheme())) continue;
                PhotoPage page = new PhotoPage(uri);
                page.left = saved.getFloat("left", 0f);
                page.top = saved.getFloat("top", 0f);
                page.right = saved.getFloat("right", 1f);
                page.bottom = saved.getFloat("bottom", 1f);
                page.rotation = saved.getInt("rotation", 0);
                page.filter = saved.getInt("filter", 0);
                if (pages.size() < 50) pages.add(page);
            }
            String pdfName = state.getString("preparedPdfName");
            if (pdfName != null && pdfName.equals(new File(pdfName).getName())
                    && pdfName.startsWith("prepared-") && pdfName.endsWith(".pdf"))
                preparedPdf = new File(getCacheDir(), pdfName);
            String name = state.getString("pendingCaptureName");
            String uri = state.getString("pendingCaptureUri");
            if (name != null && name.equals(new File(name).getName())
                    && name.startsWith("photo-") && name.endsWith(".jpg") && uri != null) {
                pendingCaptureFile = new File(cameraDirectory(), name);
                pendingCaptureUri = Uri.parse(uri);
            }
        }
        clearStalePdfCache();
        clearStaleCameraCache();
        buildUi();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        ArrayList<Bundle> savedPages = new ArrayList<>();
        for (PhotoPage page : pages) {
            Bundle saved = new Bundle();
            saved.putString("uri", page.uri.toString());
            saved.putFloat("left", page.left);
            saved.putFloat("top", page.top);
            saved.putFloat("right", page.right);
            saved.putFloat("bottom", page.bottom);
            saved.putInt("rotation", page.rotation);
            saved.putInt("filter", page.filter);
            savedPages.add(saved);
        }
        state.putParcelableArrayList("photoPages", savedPages);
        if (preparedPdf != null) state.putString("preparedPdfName", preparedPdf.getName());
        if (pendingCaptureFile != null && pendingCaptureUri != null) {
            state.putString("pendingCaptureName", pendingCaptureFile.getName());
            state.putString("pendingCaptureUri", pendingCaptureUri.toString());
        }
    }

    @Override protected void onDestroy() {
        if (isFinishing()) {
            File[] photos = cameraDirectory().listFiles();
            if (photos != null) for (File photo : photos) photo.delete();
        }
        super.onDestroy();
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable background(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView text(String label, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(label); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : blue);
        b.setBackground(background(primary ? blue : 0xFFE8EEFC, 14));
        b.setMinHeight(dp(48));
        return b;
    }
    private void addSpace(LinearLayout l, int height) {
        View v = new View(this);
        l.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void buildUi() {
        LinearLayout root = vertical();
        root.setBackgroundColor(0xFFF5F7FC);
        setContentView(root);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = vertical();
        body.setPadding(dp(20), dp(48), dp(20), dp(18));
        scroll.addView(body);

        TextView title = text("Фото в PDF", 30, ink);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setLetterSpacing(0.015f);
        title.setGravity(Gravity.CENTER);
        SpannableString heading = new SpannableString("Фото в PDF");
        heading.setSpan(new TypefaceSpan("sans-serif-medium"), 7, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(heading);
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addSpace(body, 6);
        TextView subtitle = text("Файлы остаются на телефоне", 13, 0xFF68758E);
        subtitle.setGravity(Gravity.CENTER);
        body.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        addSpace(body, 15);

        LinearLayout photoActions = new LinearLayout(this);
        photoActions.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(photoActions, new LinearLayout.LayoutParams(-1, dp(56)));
        Button add = button("＋ Добавить", true);
        add.setTextSize(14);
        add.setSingleLine(true);
        add.setOnClickListener(v -> choosePhotos());
        photoActions.addView(add, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button camera = button("📷 Камера", false);
        camera.setTextSize(14);
        camera.setSingleLine(true);
        camera.setOnClickListener(v -> takePhoto());
        LinearLayout.LayoutParams cameraParams = new LinearLayout.LayoutParams(0, dp(56), 1);
        cameraParams.leftMargin = dp(8);
        photoActions.addView(camera, cameraParams);
        previewButton = button("Предпросмотр PDF", false);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(48));
        previewParams.topMargin = dp(10);
        body.addView(previewButton, previewParams);
        previewButton.setOnClickListener(v -> preparePreview());
        addSpace(body, 14);

        LinearLayout card = vertical();
        card.setBackground(background(Color.WHITE, 18));
        card.setPadding(dp(16), dp(17), dp(16), dp(17));
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        count = text("Фотографии (0)", 18, ink);
        count.setTypeface(null, 1);
        card.addView(count);
        addSpace(card, 6);
        card.addView(text("Нажми на фото, чтобы изменить его. Перетаскивай за значок ⋮⋮ или меняй порядок стрелками.",
                13, 0xFF68758E));
        addSpace(card, 10);
        rows = vertical();
        card.addView(rows);
        refreshRows();
        addSpace(body, 12);

        LinearLayout options = vertical();
        options.setPadding(dp(16), dp(18), dp(16), dp(18));
        options.setBackground(background(Color.WHITE, 18));
        body.addView(options, new LinearLayout.LayoutParams(-1, -2));
        TextView optionsTitle = text("Настройки PDF", 18, ink);
        optionsTitle.setTypeface(null, 1);
        options.addView(optionsTitle);
        addSpace(options, 12);
        options.addView(text("Размер страницы", 14, ink));
        sizeChoice = spinner(new String[]{"A4 · 210 × 297 мм", "Letter · 216 × 279 мм", "По пропорциям фото"});
        options.addView(sizeChoice, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(options, 6);
        options.addView(text("Поля", 14, ink));
        marginChoice = spinner(new String[]{"Без полей · 0 мм", "Узкие · около 5 мм", "Обычные · около 11 мм"});
        marginChoice.setSelection(1);
        options.addView(marginChoice, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(options, 6);
        options.addView(text("Одна фотография на страницу. Ориентация листа подстраивается автоматически.",
                13, 0xFF68758E));
        LinearLayout footer = vertical();
        footer.setBackgroundColor(Color.WHITE);
        footer.setPadding(dp(20), dp(12), dp(20), dp(18));
        if (Build.VERSION.SDK_INT >= 35) {
            footer.setOnApplyWindowInsetsListener((view, insets) -> {
                int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                view.setPadding(dp(20), dp(12), dp(20), dp(18) + bottom);
                return insets;
            });
        }
        root.addView(footer);
        saveButton = button("Сохранить PDF", true);
        saveButton.setOnClickListener(v -> preparePdf());
        footer.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(54)));
        refreshButton();
    }

    private Spinner spinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void choosePhotos() {
        if (busy) return;
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,
                    Math.min(50, MediaStore.getPickImagesMaxLimit()));
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try { startActivityForResult(intent, PICK); }
        catch (Exception e) { Toast.makeText(this, "Не удалось открыть выбор фото", Toast.LENGTH_LONG).show(); }
    }

    private File cameraDirectory() { return new File(getCacheDir(), "camera"); }

    private void clearStaleCameraCache() {
        File[] photos = cameraDirectory().listFiles();
        if (photos == null) return;
        for (File photo : photos) {
            if (photo.equals(pendingCaptureFile)) continue;
            boolean inUse = false;
            for (PhotoPage page : pages) {
                if ((getPackageName() + ".camera").equals(page.uri.getAuthority())
                        && photo.getName().equals(page.uri.getLastPathSegment())) {
                    inUse = true;
                    break;
                }
            }
            if (!inUse) photo.delete();
        }
    }

    private void takePhoto() {
        if (busy) return;
        if (pages.size() >= 50) {
            Toast.makeText(this, "В документе уже 50 фотографий", Toast.LENGTH_LONG).show();
            return;
        }
        File directory = cameraDirectory();
        if (!directory.exists() && !directory.mkdirs()) {
            error("Не удалось подготовить место для фотографии");
            return;
        }
        try {
            File file = File.createTempFile("photo-", ".jpg", directory);
            pendingCaptureFile = file;
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".camera", file);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.setClipData(ClipData.newRawUri("photo", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            pendingCaptureUri = uri;
            startActivityForResult(intent, CAPTURE);
        } catch (Exception e) {
            if (pendingCaptureFile != null) pendingCaptureFile.delete();
            pendingCaptureFile = null;
            pendingCaptureUri = null;
            error("Не удалось открыть камеру: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAPTURE) {
            File captured = pendingCaptureFile;
            Uri uri = pendingCaptureUri;
            pendingCaptureFile = null;
            pendingCaptureUri = null;
            if (resultCode == RESULT_OK && captured != null && captured.length() > 0 && uri != null) {
                addPhoto(uri);
                refreshRows();
            } else {
                if (captured != null) captured.delete();
                if (resultCode == RESULT_OK) error("Камера не сохранила фотографию. Попробуйте ещё раз.");
            }
            return;
        }
        if (requestCode == PICK && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int available = Math.max(0, 50 - pages.size());
                int selected = data.getClipData().getItemCount();
                for (int i = 0; i < Math.min(selected, available); i++)
                    addPhoto(data.getClipData().getItemAt(i).getUri());
                if (selected > available)
                    Toast.makeText(this, "Добавлено не более 50 фотографий", Toast.LENGTH_LONG).show();
            } else if (data.getData() != null) addPhoto(data.getData());
            refreshRows();
        } else if (requestCode == SAVE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && preparedPdf != null) {
                Uri destination = data.getData();
                File source = preparedPdf;
                preparedPdf = null;
                setBusy(true, "Сохраняем…");
                new Thread(() -> {
                    try (FileInputStream input = new FileInputStream(source);
                         OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                        if (output == null) throw new Exception("Нет доступа к файлу");
                        byte[] bytes = new byte[65536];
                        int n;
                        while ((n = input.read(bytes)) != -1) output.write(bytes, 0, n);
                        runOnUiThread(() -> new AlertDialog.Builder(this)
                                .setTitle("PDF готов")
                                .setMessage("Файл сохранён.")
                                .setPositiveButton("Открыть", (d, w) -> openPdf(destination))
                                .setNegativeButton("Готово", null).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> error("Не удалось сохранить PDF: " + e.getMessage()));
                    } finally {
                        source.delete();
                        runOnUiThread(() -> setBusy(false, null));
                    }
                }).start();
            } else {
                if (preparedPdf != null) preparedPdf.delete();
                preparedPdf = null;
                setBusy(false, null);
            }
        }
    }

    private void removePhoto(int index) {
        PhotoPage removed = pages.remove(index);
        if ((getPackageName() + ".camera").equals(removed.uri.getAuthority())) {
            String name = removed.uri.getLastPathSegment();
            if (name != null && name.equals(new File(name).getName())
                    && name.startsWith("photo-") && name.endsWith(".jpg"))
                new File(cameraDirectory(), name).delete();
        }
        refreshRows();
    }

    private void addPhoto(Uri uri) {
        if (pages.size() >= 50) {
            Toast.makeText(this, "За раз можно добавить не более 50 фотографий", Toast.LENGTH_LONG).show();
            return;
        }
        pages.add(new PhotoPage(uri));
    }

    private void clearStalePdfCache() {
        File[] stale = getCacheDir().listFiles((dir, name) ->
                name.startsWith("prepared-") || name.startsWith("preview-"));
        if (stale != null) for (File file : stale)
            if (!file.equals(preparedPdf)) file.delete();
    }

    private void refreshRows() {
        if (rows == null) return;
        rows.removeAllViews();
        count.setText("Фотографии (" + pages.size() + ")");
        if (pages.isEmpty()) {
            TextView empty = text("Здесь появятся выбранные фотографии", 14, 0xFF68758E);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            rows.addView(empty);
        }
        for (int i = 0; i < pages.size(); i++) {
            final int index = i;
            PhotoPage page = pages.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setMinimumHeight(dp(82));
            rows.addView(row);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(background(0xFFE8EEFC, 9));
            row.addView(image, new LinearLayout.LayoutParams(dp(66), dp(72)));
            // Small thumbnail decode keeps the first screen responsive for most photo sets.
            try {
                Bitmap decoded = ImageTools.decode(getContentResolver(), page.uri, 320);
                Bitmap preview = ImageTools.preview(decoded, page, 240);
                image.setImageBitmap(preview);
                decoded.recycle();
            } catch (Exception ignored) { image.setImageResource(android.R.drawable.ic_menu_report_image); }
            LinearLayout label = vertical();
            label.setPadding(dp(12), 0, dp(2), 0);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView name = text("Страница " + (i + 1), 15, ink);
            label.addView(name);
            label.addView(text(new String[]{"Цветная", "Чёрно-белая", "Документ", "Скан · цветной", "Скан · ч/б"}[page.filter],
                    12, 0xFF68758E));
            image.setOnClickListener(v -> edit(index));
            label.setOnClickListener(v -> edit(index));
            TextView handle = text("⋮⋮", 22, blue);
            handle.setGravity(Gravity.CENTER);
            handle.setContentDescription("Перетащить страницу " + (i + 1));
            row.addView(handle, new LinearLayout.LayoutParams(dp(32), dp(72)));
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && !busy) {
                    return startPageDrag(row, page, index);
                }
                return true;
            });
            image.setOnLongClickListener(v -> startPageDrag(row, page, index));
            label.setOnLongClickListener(v -> startPageDrag(row, page, index));
            row.setOnDragListener((v, event) -> onPageDrag(row, index, scrollForRows(), event));
            Button up = smallButton("↑", i > 0);
            row.addView(up, new LinearLayout.LayoutParams(dp(39), dp(43)));
            up.setOnClickListener(v -> move(index, index - 1));
            Button down = smallButton("↓", i < pages.size() - 1);
            row.addView(down, new LinearLayout.LayoutParams(dp(39), dp(43)));
            down.setOnClickListener(v -> move(index, index + 1));
            Button remove = smallButton("×", true);
            row.addView(remove, new LinearLayout.LayoutParams(dp(39), dp(43)));
            remove.setOnClickListener(v -> removePhoto(index));
        }
        refreshButton();
    }

    private ScrollView scrollForRows() {
        return (ScrollView) ((View) rows.getParent().getParent()).getParent();
    }

    private boolean startPageDrag(View row, PhotoPage page, int index) {
        if (busy || pages.size() < 2) return false;
        ClipData data = ClipData.newPlainText("photo-page", Integer.toString(index));
        return row.startDragAndDrop(data, new View.DragShadowBuilder(row), page, 0);
    }

    private boolean onPageDrag(View row, int target, ScrollView scroll, DragEvent event) {
        if (!(event.getLocalState() instanceof PhotoPage)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                row.setBackground(background(0xFFE8EEFC, 10));
                if (pages.indexOf(event.getLocalState()) != target)
                    row.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                int[] rowPos = new int[2];
                int[] scrollPos = new int[2];
                row.getLocationOnScreen(rowPos);
                scroll.getLocationOnScreen(scrollPos);
                int y = rowPos[1] + (int) event.getY();
                if (y < scrollPos[1] + dp(64)) scroll.smoothScrollBy(0, -dp(32));
                else if (y > scrollPos[1] + scroll.getHeight() - dp(64))
                    scroll.smoothScrollBy(0, dp(32));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                row.setBackgroundColor(Color.TRANSPARENT);
                return true;
            case DragEvent.ACTION_DROP:
                row.setBackgroundColor(Color.TRANSPARENT);
                int from = pages.indexOf((PhotoPage) event.getLocalState());
                if (from < 0) return false;
                // Releasing anywhere over the other photo selects it: no midpoint
                // threshold and no extra downward/upward movement required.
                if (from != target) {
                    PhotoPage moved = pages.remove(from);
                    pages.add(target, moved);
                    refreshRows();
                }
                return true;
            default:
                return true;
        }
    }

    private Button smallButton(String label, boolean enabled) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(19);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setTextColor(enabled ? blue : 0xFFB7C1D2);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setEnabled(enabled);
        return b;
    }

    private void move(int from, int to) {
        if (to < 0 || to >= pages.size()) return;
        PhotoPage page = pages.remove(from);
        pages.add(to, page);
        refreshRows();
    }

    private void edit(int index) {
        PhotoPage original = pages.get(index);
        Bitmap bitmap;
        try { bitmap = ImageTools.decode(getContentResolver(), original.uri, 1600); }
        catch (Exception e) { error("Не удалось открыть фото"); return; }
        PhotoPage draft = new PhotoPage(original.uri);
        draft.left = original.left; draft.top = original.top;
        draft.right = original.right; draft.bottom = original.bottom;
        draft.rotation = original.rotation; draft.filter = original.filter;
        LinearLayout content = vertical();
        content.setPadding(dp(14), dp(10), dp(14), dp(4));
        CropView crop = new CropView(this, bitmap, draft);
        content.addView(crop, new LinearLayout.LayoutParams(-1,
                Math.min(dp(300), getResources().getDisplayMetrics().heightPixels / 3)));
        TextView hint = text("Перетащи углы рамки или всю рамку", 13, 0xFF68758E);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(10);
        content.addView(hint, hintParams);
        Button reset = button("Сбросить обрезку", false);
        reset.setTextSize(14);
        reset.setPadding(dp(16), 0, dp(16), 0);
        reset.setBackground(background(0xFFE8EEFC, 12));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-2, dp(48));
        resetParams.gravity = Gravity.CENTER_HORIZONTAL;
        resetParams.topMargin = dp(12);
        content.addView(reset, resetParams);
        reset.setOnClickListener(v -> {
            crop.left = 0; crop.top = 0; crop.right = 1; crop.bottom = 1; crop.invalidate();
        });
        Button rotate = button("Повернуть на 90°", false);
        rotate.setTextSize(14);
        rotate.setPadding(dp(16), 0, dp(16), 0);
        rotate.setBackground(background(0xFFE8EEFC, 12));
        LinearLayout.LayoutParams rotateParams = new LinearLayout.LayoutParams(-2, dp(48));
        rotateParams.gravity = Gravity.CENTER_HORIZONTAL;
        rotateParams.topMargin = dp(10);
        content.addView(rotate, rotateParams);
        TextView rotationInfo = text("Поворот: " + draft.rotation + "°", 12, 0xFF68758E);
        rotationInfo.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rotationParams = new LinearLayout.LayoutParams(-1, -2);
        rotationParams.topMargin = dp(4);
        content.addView(rotationInfo, rotationParams);
        rotate.setOnClickListener(v -> {
            crop.rotateClockwise();
            draft.rotation = (draft.rotation + 90) % 360;
            rotationInfo.setText("Поворот: " + draft.rotation + "°");
        });
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, -2);
        filterParams.topMargin = dp(12);
        content.addView(text("Фильтр", 14, ink), filterParams);
        Spinner filters = spinner(new String[]{"Цветная", "Чёрно-белая", "Документ · контраст", "Эффект скана · цветной", "Эффект скана · ч/б"});
        filters.setSelection(draft.filter);
        filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                crop.filter = position;
                crop.invalidate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        content.addView(filters);
        CheckBox applyToAll = null;
        if (pages.size() > 1) {
            applyToAll = new CheckBox(this);
            applyToAll.setText("Применить выбранный фильтр ко всем фото (" + pages.size() + ")");
            applyToAll.setTextColor(ink);
            applyToAll.setTextSize(14);
            applyToAll.setButtonTintList(android.content.res.ColorStateList.valueOf(blue));
            applyToAll.setMinHeight(dp(48));
            LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(-1, -2);
            applyParams.topMargin = dp(8);
            content.addView(applyToAll, applyParams);
        }
        final CheckBox allPhotos = applyToAll;
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(false);
        dialogScroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Страница " + (index + 1))
                .setView(dialogScroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Готово", (d, w) -> {
                    float[] originalRect = crop.originalCrop();
                    original.left = originalRect[0]; original.top = originalRect[1];
                    original.right = originalRect[2]; original.bottom = originalRect[3];
                    original.rotation = draft.rotation;
                    int selectedFilter = filters.getSelectedItemPosition();
                    if (allPhotos != null && allPhotos.isChecked()) {
                        for (PhotoPage page : pages) page.filter = selectedFilter;
                    } else {
                        original.filter = selectedFilter;
                    }
                    refreshRows();
                }).create();
        dialog.setOnDismissListener(d -> bitmap.recycle());
        dialog.show();
    }

    private void refreshButton() {
        if (saveButton != null) saveButton.setEnabled(!busy && !pages.isEmpty());
        if (previewButton != null) previewButton.setEnabled(!busy && !pages.isEmpty());
    }
    private void setBusy(boolean value, String label) {
        busy = value;
        saveButton.setText(value ? label : "Сохранить PDF");
        refreshButton();
    }
    private void error(String message) { new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Понятно", null).show(); }

    private void preparePreview() {
        if (pages.isEmpty() || busy) return;
        int selectedSize = sizeChoice.getSelectedItemPosition();
        int margin = new int[]{0, 14, 32}[marginChoice.getSelectedItemPosition()];
        List<PhotoPage> snapshot = new ArrayList<>(pages);
        setBusy(true, "Создаём предпросмотр…");
        new Thread(() -> {
            File file = new File(getCacheDir(), "preview-" + System.currentTimeMillis() + ".pdf");
            try {
                PdfWriter.write(getContentResolver(), snapshot, selectedSize, margin, file);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    showPreview(file, selectedSize, margin);
                });
            } catch (Exception e) {
                file.delete();
                runOnUiThread(() -> {
                    setBusy(false, null);
                    error("Не удалось создать предпросмотр: " + e.getMessage());
                });
            }
        }).start();
    }

    private Bitmap renderPreviewPage(PdfRenderer renderer, int index, int marginPt) {
        try (PdfRenderer.Page page = renderer.openPage(index)) {
            float scale = Math.min(
                    Math.min((getResources().getDisplayMetrics().widthPixels - dp(88))
                            / (float) page.getWidth(), 900f / page.getWidth()),
                    1200f / page.getHeight());
            int width = Math.max(1, Math.round(page.getWidth() * scale));
            int height = Math.max(1, Math.round(page.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                if (marginPt > 0) {
                    Canvas guide = new Canvas(bitmap);
                    float insetX = marginPt * width / (float) page.getWidth();
                    float insetY = marginPt * height / (float) page.getHeight();
                    Paint shade = new Paint();
                    shade.setColor(0x28345FD0);
                    guide.drawRect(0, 0, width, insetY, shade);
                    guide.drawRect(0, height - insetY, width, height, shade);
                    guide.drawRect(0, insetY, insetX, height - insetY, shade);
                    guide.drawRect(width - insetX, insetY, width, height - insetY, shade);
                    Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
                    outline.setColor(0xFF255DCE);
                    outline.setStyle(Paint.Style.STROKE);
                    outline.setStrokeWidth(Math.max(2f, width / 320f));
                    outline.setPathEffect(new DashPathEffect(new float[]{9f, 6f}, 0));
                    guide.drawRect(insetX, insetY, width - insetX, height - insetY, outline);
                }
                return bitmap;
            } catch (RuntimeException e) {
                bitmap.recycle();
                throw e;
            }
        }
    }

    private void showPreview(File file, int selectedSize, int marginPt) {
        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) throw new IllegalStateException("В PDF нет страниц");
        } catch (Exception e) {
            if (renderer != null) renderer.close();
            if (descriptor != null) {
                try { descriptor.close(); } catch (Exception ignored) { }
            }
            file.delete();
            error("Не удалось открыть предпросмотр: " + e.getMessage());
            return;
        }
        final PdfRenderer pdf = renderer;
        final ParcelFileDescriptor fd = descriptor;
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(12), dp(16), dp(8));
        String pageSizeName = new String[]{"A4", "Letter", "По пропорциям фото"}[selectedSize];
        String marginName = marginPt == 0 ? "0 мм" : marginPt == 14 ? "≈5 мм" : "≈11 мм";
        TextView details = text(pageSizeName + " · Поля: " + marginName, 14, ink);
        details.setGravity(Gravity.CENTER);
        content.addView(details);
        addSpace(content, 6);
        TextView guideHint = text("Синим показаны поля. В PDF этой разметки нет.", 12, 0xFF68758E);
        guideHint.setGravity(Gravity.CENTER);
        content.addView(guideHint);
        addSpace(content, 10);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(0xFFE8EEFC);
        image.setPadding(dp(8), dp(8), dp(8), dp(8));
        content.addView(image, new LinearLayout.LayoutParams(-1,
                Math.min(dp(460), getResources().getDisplayMetrics().heightPixels / 2)));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(54));
        navParams.topMargin = dp(8);
        content.addView(navigation, navParams);
        Button previous = button("←", false);
        navigation.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(44)));
        TextView pageLabel = text("", 14, ink);
        pageLabel.setGravity(Gravity.CENTER);
        navigation.addView(pageLabel, new LinearLayout.LayoutParams(0, -1, 1));
        Button next = button("→", false);
        navigation.addView(next, new LinearLayout.LayoutParams(dp(48), dp(44)));
        int[] pageIndex = {0};
        Bitmap[] shown = {null};
        Runnable update = () -> {
            try {
                Bitmap bitmap = renderPreviewPage(pdf, pageIndex[0], marginPt);
                Bitmap old = shown[0];
                shown[0] = bitmap;
                image.setImageBitmap(bitmap);
                if (old != null) old.recycle();
                pageLabel.setText("Страница " + (pageIndex[0] + 1) + " из " + pdf.getPageCount());
                previous.setEnabled(pageIndex[0] > 0);
                next.setEnabled(pageIndex[0] < pdf.getPageCount() - 1);
            } catch (Exception e) {
                error("Не удалось показать страницу: " + e.getMessage());
            }
        };
        previous.setOnClickListener(v -> { pageIndex[0]--; update.run(); });
        next.setOnClickListener(v -> { pageIndex[0]++; update.run(); });
        try {
            update.run();
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Предпросмотр PDF")
                    .setView(content)
                    .setPositiveButton("Закрыть", null).create();
            dialog.setOnDismissListener(d -> {
                image.setImageDrawable(null);
                if (shown[0] != null) shown[0].recycle();
                pdf.close();
                try { fd.close(); } catch (Exception ignored) { }
                file.delete();
            });
            dialog.show();
        } catch (Exception e) {
            if (shown[0] != null) shown[0].recycle();
            pdf.close();
            try { fd.close(); } catch (Exception ignored) { }
            file.delete();
            error("Не удалось показать предпросмотр: " + e.getMessage());
        }
    }

    private void preparePdf() {
        if (pages.isEmpty() || busy) return;
        int selectedSize = sizeChoice.getSelectedItemPosition();
        int margin = new int[]{0, 14, 32}[marginChoice.getSelectedItemPosition()];
        List<PhotoPage> snapshot = new ArrayList<>(pages);
        setBusy(true, "Создаём PDF…");
        new Thread(() -> {
            File file = new File(getCacheDir(), "prepared-" + System.currentTimeMillis() + ".pdf");
            try {
                PdfWriter.write(getContentResolver(), snapshot, selectedSize, margin, file);
                runOnUiThread(() -> {
                    preparedPdf = file;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/pdf");
                    intent.putExtra(Intent.EXTRA_TITLE, "Фото.pdf");
                    try { startActivityForResult(intent, SAVE); }
                    catch (Exception e) {
                        file.delete(); preparedPdf = null;
                        setBusy(false, null); error("Не удалось открыть сохранение файла");
                    }
                });
            } catch (Exception e) {
                file.delete();
                runOnUiThread(() -> { setBusy(false, null); error("Не удалось создать PDF: " + e.getMessage()); });
            }
        }).start();
    }

    private void openPdf(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(intent); }
        catch (Exception e) { Toast.makeText(this, "PDF сохранён. Откройте его из файлов.", Toast.LENGTH_LONG).show(); }
    }
}

package com.vladimir.photopdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
    private static final int PICK = 1, SAVE = 2;
    private final ArrayList<PhotoPage> pages = new ArrayList<>();
    private LinearLayout rows;
    private TextView count;
    private Button saveButton;
    private Spinner sizeChoice, marginChoice;
    private File preparedPdf;
    private boolean busy;
    private final int ink = 0xFF17223A;
    private final int blue = 0xFF255DCE;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
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
        body.setPadding(dp(20), dp(25), dp(20), dp(18));
        scroll.addView(body);

        TextView title = text("Фото в PDF", 29, ink);
        title.setTypeface(null, 1);
        body.addView(title);
        addSpace(body, 5);
        body.addView(text("Без водяного знака · Файлы остаются на телефоне", 13, 0xFF68758E));
        addSpace(body, 22);

        Button add = button("＋  Добавить фотографии", true);
        add.setOnClickListener(v -> choosePhotos());
        body.addView(add, new LinearLayout.LayoutParams(-1, dp(56)));
        addSpace(body, 21);

        LinearLayout card = vertical();
        card.setBackground(background(Color.WHITE, 18));
        card.setPadding(dp(16), dp(17), dp(16), dp(17));
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        count = text("Фотографии (0)", 18, ink);
        count.setTypeface(null, 1);
        card.addView(count);
        addSpace(card, 6);
        card.addView(text("Нажми на фото, чтобы обрезать, повернуть или выбрать фильтр. Порядок можно изменить стрелками.",
                13, 0xFF68758E));
        addSpace(card, 12);
        rows = vertical();
        card.addView(rows);
        refreshRows();
        addSpace(body, 17);

        LinearLayout options = vertical();
        options.setPadding(dp(16), dp(18), dp(16), dp(18));
        options.setBackground(background(Color.WHITE, 18));
        body.addView(options, new LinearLayout.LayoutParams(-1, -2));
        TextView optionsTitle = text("Настройки PDF", 18, ink);
        optionsTitle.setTypeface(null, 1);
        options.addView(optionsTitle);
        addSpace(options, 15);
        options.addView(text("Размер страницы", 14, ink));
        sizeChoice = spinner(new String[]{"A4 · под фото", "Letter · под фото", "По пропорциям фото"});
        options.addView(sizeChoice, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(options, 10);
        options.addView(text("Поля", 14, ink));
        marginChoice = spinner(new String[]{"Без полей", "Узкие", "Обычные"});
        marginChoice.setSelection(1);
        options.addView(marginChoice, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(options, 9);
        options.addView(text("Одна фотография на страницу. Ориентация листа подстраивается автоматически.",
                13, 0xFF68758E));

        LinearLayout footer = vertical();
        footer.setBackgroundColor(Color.WHITE);
        footer.setPadding(dp(20), dp(12), dp(20), dp(18));
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    addPhoto(data.getClipData().getItemAt(i).getUri(), data);
            } else if (data.getData() != null) addPhoto(data.getData(), data);
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
                                .setMessage("Файл сохранён без водяного знака.")
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

    private void addPhoto(Uri uri, Intent data) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { /* Photo Picker grants access without a persistent document permission. */ }
        pages.add(new PhotoPage(uri));
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
            label.addView(text(new String[]{"Цветная", "Чёрно-белая", "Документ"}[page.filter],
                    12, 0xFF68758E));
            image.setOnClickListener(v -> edit(index));
            label.setOnClickListener(v -> edit(index));
            Button up = smallButton("↑", i > 0);
            row.addView(up, new LinearLayout.LayoutParams(dp(39), dp(43)));
            up.setOnClickListener(v -> move(index, index - 1));
            Button down = smallButton("↓", i < pages.size() - 1);
            row.addView(down, new LinearLayout.LayoutParams(dp(39), dp(43)));
            down.setOnClickListener(v -> move(index, index + 1));
            Button remove = smallButton("×", true);
            row.addView(remove, new LinearLayout.LayoutParams(dp(39), dp(43)));
            remove.setOnClickListener(v -> { pages.remove(index); refreshRows(); });
        }
        refreshButton();
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
        content.addView(crop, new LinearLayout.LayoutParams(-1, dp(340)));
        TextView hint = text("Перетащи углы рамки или всю рамку", 13, 0xFF68758E);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint);
        Button reset = button("Сбросить обрезку", false);
        content.addView(reset);
        reset.setOnClickListener(v -> {
            crop.left = 0; crop.top = 0; crop.right = 1; crop.bottom = 1; crop.invalidate();
        });
        Button rotate = button("Повернуть на 90°", false);
        content.addView(rotate);
        rotate.setOnClickListener(v -> {
            draft.rotation = (draft.rotation + 90) % 360;
            rotate.setText("Поворот: " + draft.rotation + "° (ещё 90°)");
        });
        content.addView(text("Фильтр", 14, ink));
        Spinner filters = spinner(new String[]{"Цветная", "Чёрно-белая", "Документ · контраст"});
        filters.setSelection(draft.filter);
        filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                crop.filter = position;
                crop.invalidate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        content.addView(filters);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Страница " + (index + 1))
                .setView(content)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Готово", (d, w) -> {
                    original.left = crop.left; original.top = crop.top;
                    original.right = crop.right; original.bottom = crop.bottom;
                    original.rotation = draft.rotation;
                    original.filter = filters.getSelectedItemPosition();
                    refreshRows();
                }).create();
        dialog.setOnDismissListener(d -> bitmap.recycle());
        dialog.show();
    }

    private void refreshButton() {
        if (saveButton != null) saveButton.setEnabled(!busy && !pages.isEmpty());
    }
    private void setBusy(boolean value, String label) {
        busy = value;
        saveButton.setText(value ? label : "Сохранить PDF");
        refreshButton();
    }
    private void error(String message) { new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Понятно", null).show(); }

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

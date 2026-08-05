package de.spahr.ausgaben.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.receipt.ReceiptEdit;

/**
 * Nachbearbeitung eines Belegfotos: rechteckiger Zuschnitt, Trapezkorrektur für schräg fotografierte
 * Rechnungen sowie Helligkeit und Kontrast.
 *
 * <p>Bearbeitet wird immer das <b>Original</b> ({@link #EXTRA_ORIGINAL}, falls vorhanden), geschrieben wird
 * nach {@link #EXTRA_PATH}. So lässt sich ein einmal enger gezogener Zuschnitt später wieder aufziehen.
 * Abbrechen oder Zurück lässt beide Dateien unberührt.</p>
 */
public class ReceiptEditActivity extends LocalizedActivity {

    /** Zieldatei – hierhin wird das Ergebnis geschrieben. */
    public static final String EXTRA_PATH = "path";
    /** Quelle für die Bearbeitung; fehlt sie oder gibt es sie nicht, dient die Zieldatei als Quelle. */
    public static final String EXTRA_ORIGINAL = "original";

    /** Arbeitsauflösung der Vorschau – groß genug zum Zielen, klein genug fürs Bildgedächtnis. */
    private static final int PREVIEW_MAX_EDGE = 1600;
    private static final int JPEG_QUALITY = 85;

    private ReceiptCropView cropView;
    private Slider brightness;
    private Slider contrast;
    private File target;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_edit);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        String original = getIntent().getStringExtra(EXTRA_ORIGINAL);
        if (path == null || path.isEmpty()) {
            finish();
            return;
        }
        target = new File(path);
        File source = original == null || original.isEmpty() ? target : new File(original);
        if (!source.exists()) {
            source = target;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cropView = findViewById(R.id.cropView);
        cropView.setAccentColor(androidx.core.content.ContextCompat.getColor(this, R.color.green_primary));

        MaterialButtonToggleGroup mode = findViewById(R.id.receiptEditMode);
        mode.check(R.id.btnModeRect);
        mode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                cropView.setRectMode(checkedId == R.id.btnModeRect);
            }
        });

        brightness = findViewById(R.id.sliderBrightness);
        contrast = findViewById(R.id.sliderContrast);
        Slider.OnChangeListener adjust = (slider, value, fromUser) ->
                cropView.setAdjust(brightness.getValue(), contrast.getValue());
        brightness.addOnChangeListener(adjust);
        contrast.addOnChangeListener(adjust);

        MaterialButton reset = findViewById(R.id.btnReceiptEditReset);
        reset.setOnClickListener(v -> {
            cropView.reset();
            brightness.setValue(0);
            contrast.setValue(0);
        });
        findViewById(R.id.btnReceiptEditApply).setOnClickListener(v -> apply());

        load(source);
    }

    /** Lädt die Vorlage verkleinert in die Vorschau (Vollauflösung erst beim Übernehmen). */
    private void load(File source) {
        final File src = source;
        new Thread(() -> {
            Bitmap bmp = decode(src, PREVIEW_MAX_EDGE);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (bmp == null) {
                    Toast.makeText(this, R.string.receipt_error, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                cropView.setBitmap(bmp);
                cropView.setAdjust(0, 0);
            });
        }).start();
    }

    /** Rechnet den Rahmen auf das Vorschaubild an und schreibt das Ergebnis in die Zieldatei. */
    private void apply() {
        Bitmap src = cropView.bitmap();
        float[] quad = cropView.quad();
        if (src == null || quad == null) {
            return;
        }
        final float[] copy = quad.clone();
        final boolean rect = cropView.isRectMode();
        final float b = brightness.getValue();
        final float c = contrast.getValue();
        new Thread(() -> {
            boolean ok;
            try {
                Bitmap out = ReceiptEdit.apply(src, copy, rect, b, c);
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
                }
                out.recycle();
                ok = target.length() > 0;
            } catch (Exception e) {
                ok = false;
            }
            final boolean done = ok;
            runOnUiThread(() -> {
                if (done) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, R.string.receipt_error, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /** Wie {@code ReceiptImage}: erst die Maße lesen, dann passend unterabgetastet dekodieren. */
    private static Bitmap decode(File file, int maxEdge) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longEdge / (sample * 2) >= maxEdge) {
                sample *= 2;
            }
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
        } catch (Exception e) {
            return null;
        }
    }
}

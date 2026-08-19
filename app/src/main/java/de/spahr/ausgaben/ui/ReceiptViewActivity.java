package de.spahr.ausgaben.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.receipt.ReceiptEdit;
import de.spahr.ausgaben.receipt.ReceiptSync;

/**
 * Zeigt die Belegseiten einer Buchung an – mit Wischen zwischen den Seiten und Zoom je Seite.
 *
 * <p>Bewusst <b>kein</b> {@code ACTION_VIEW} an eine fremde Foto-App: deren Cache hängt am Dateinamen, und
 * da ein bearbeiteter Beleg unter demselben Namen weiterlebt, bekam man dort das alte Bild zu sehen. Hier
 * wird jedes Mal frisch von der Platte gelesen.</p>
 */
public class ReceiptViewActivity extends LocalizedActivity {

    /** Die Namen der Belegseiten in Reihenfolge. */
    public static final String EXTRA_FILES = "files";
    /** Jahresordner auf dem Server, falls eine Seite erst geholt werden muss. */
    public static final String EXTRA_YEAR = "year";
    /** Seite, die zuerst gezeigt wird (0-basiert). */
    public static final String EXTRA_INDEX = "index";

    /** Auflösung, in der eine Seite dekodiert wird – reicht zum Lesen auch beim Hineinzoomen. */
    private static final int MAX_EDGE = 2400;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String[] files;
    private int year;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_view);

        files = getIntent().getStringArrayExtra(EXTRA_FILES);
        year = getIntent().getIntExtra(EXTRA_YEAR, -1);
        if (files == null || files.length == 0) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ViewPager2 pager = findViewById(R.id.receiptPager);
        pager.setAdapter(new PageAdapter());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPageNumber(position);
            }
        });
        int start = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_INDEX, 0), files.length - 1));
        pager.setCurrentItem(start, false);
        showPageNumber(start);
    }

    /** „Seite 2 von 3" in der Unterzeile; bei einer einzelnen Seite bleibt sie leer. */
    private void showPageNumber(int position) {
        toolbar.setSubtitle(files.length < 2
                ? null
                : getString(R.string.receipt_view_page, position + 1, files.length));
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_receipt_view_page, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.bind(files[position]);
        }

        @Override
        public void onViewRecycled(@NonNull PageHolder holder) {
            holder.release();
        }

        @Override
        public int getItemCount() {
            return files.length;
        }
    }

    private final class PageHolder extends RecyclerView.ViewHolder {

        private final ZoomImageView image;
        private final TextView message;
        /** Wofür gerade geladen wird – nach dem Recyceln darf ein spätes Ergebnis nicht mehr landen. */
        private String wanted;

        PageHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.receiptImage);
            message = itemView.findViewById(R.id.receiptPageMessage);
        }

        void bind(String file) {
            wanted = file;
            release();
            message.setText(R.string.receipt_loading_wait);
            message.setVisibility(View.VISIBLE);
            io.execute(() -> {
                // Wartet bei bestehender Verbindung, bis der Beleg vorliegt; recycelt (weggewischt) → Abbruch.
                ReceiptSync.Loaded loaded = ReceiptSync.ensureLocalWaiting(
                        ReceiptViewActivity.this, file, year, () -> !file.equals(wanted));
                File local = loaded.file;
                final boolean offline = loaded.offline;
                final Bitmap bmp = local == null || !local.exists() ? null : ReceiptEdit.decode(local, MAX_EDGE);
                image.post(() -> {
                    if (!file.equals(wanted)) {
                        if (bmp != null) {
                            bmp.recycle();
                        }
                        return;
                    }
                    if (bmp == null) {
                        // Nur ohne Verbindung eine Fehlermeldung; sonst bleibt es beim „Wird geladen …".
                        message.setText(offline ? R.string.receipt_offline : R.string.receipt_loading_wait);
                        message.setVisibility(View.VISIBLE);
                        return;
                    }
                    message.setVisibility(View.GONE);
                    image.setBitmap(bmp);
                });
            });
        }

        /** Gibt das Bitmap frei – bei mehrseitigen Belegen sonst schnell mehrere Dutzend Megabyte. */
        void release() {
            Bitmap old = image.bitmap();
            image.setBitmap(null);
            if (old != null) {
                old.recycle();
            }
        }
    }
}

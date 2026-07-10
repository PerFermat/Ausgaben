package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.PayeeCorrection;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/** Formular zum Anlegen/Ändern/Löschen eines Alias mit allen Feldern (deckt alle Buchungsarten ab). */
public class AliasEditActivity extends LocalizedActivity {

    /** Zu bearbeitende Alias-ID; fehlt/−1 = neuer Alias. */
    public static final String EXTRA_ALIAS_ID = "alias_id";

    private Repository repository;
    private MaterialToolbar toolbar;
    private TextInputEditText editSpoken;
    private MaterialAutoCompleteTextView editCorrected;
    private MaterialAutoCompleteTextView editType;
    private MaterialAutoCompleteTextView editAccount;
    private MaterialAutoCompleteTextView editCatExpense1;
    private MaterialAutoCompleteTextView editCatExpense2;
    private MaterialAutoCompleteTextView editCatIncome1;
    private MaterialAutoCompleteTextView editCatIncome2;
    private MaterialAutoCompleteTextView editFrom;
    private MaterialAutoCompleteTextView editTo;
    private MaterialAutoCompleteTextView editPlace;
    private MaterialAutoCompleteTextView editFromPlace;
    private MaterialAutoCompleteTextView editToPlace;
    private View placeLayout;
    private View fromPlaceLayout;
    private View toPlaceLayout;
    private PlacesStore placesStore;
    private com.google.android.material.materialswitch.MaterialSwitch switchPreferred;
    private MaterialButton btnDelete;
    private View gpsSection;
    private android.widget.LinearLayout gpsContainer;
    private ActivityResultLauncher<Intent> mapLauncher;
    /** Die auf das Karten-Ergebnis wartenden Felder (Zeile, deren Karten-Knopf gedrückt wurde). */
    private TextInputEditText pendingMapLat;
    private TextInputEditText pendingMapLon;

    /** Beim Bearbeiten geladener Alias (behält id/createdAt); null = neu. */
    private PayeeCorrection loaded;

    /** Reihenfolge passend zu {@link #typeLabels}. */
    private static final String[] TYPE_VALUES = {
            Repository.VOICE_TYPE_EXPENSE, Repository.VOICE_TYPE_INCOME, Repository.VOICE_TYPE_TRANSFER};
    private String[] typeLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alias_edit);
        repository = new Repository(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        editSpoken = findViewById(R.id.editAliasSpoken);
        editCorrected = findViewById(R.id.editAliasCorrected);
        editType = findViewById(R.id.editAliasType);
        editAccount = findViewById(R.id.editAliasAccount);
        editCatExpense1 = findViewById(R.id.editAliasCatExpense1);
        editCatExpense2 = findViewById(R.id.editAliasCatExpense2);
        editCatIncome1 = findViewById(R.id.editAliasCatIncome1);
        editCatIncome2 = findViewById(R.id.editAliasCatIncome2);
        editFrom = findViewById(R.id.editAliasFrom);
        editTo = findViewById(R.id.editAliasTo);
        editPlace = findViewById(R.id.editAliasPlace);
        editFromPlace = findViewById(R.id.editAliasFromPlace);
        editToPlace = findViewById(R.id.editAliasToPlace);
        placeLayout = findViewById(R.id.aliasPlaceLayout);
        fromPlaceLayout = findViewById(R.id.aliasFromPlaceLayout);
        toPlaceLayout = findViewById(R.id.aliasToPlaceLayout);
        placesStore = new PlacesStore(this);
        switchPreferred = findViewById(R.id.switchAliasPreferred);
        btnDelete = findViewById(R.id.btnDeleteAlias);

        // Ort-Felder folgen dem jeweils getippten Konto und erscheinen nur, wenn das Konto Orte hat.
        editAccount.addTextChangedListener(afterText(() ->
                setupPlaceField(editPlace, placeLayout, text(editAccount))));
        editFrom.addTextChangedListener(afterText(() ->
                setupPlaceField(editFromPlace, fromPlaceLayout, text(editFrom))));
        editTo.addTextChangedListener(afterText(() ->
                setupPlaceField(editToPlace, toPlaceLayout, text(editTo))));

        // Standort-Bereich nur bei aktiviertem GPS zeigen.
        gpsSection = findViewById(R.id.aliasGpsSection);
        gpsContainer = findViewById(R.id.aliasGpsContainer);
        boolean gps = new SettingsStore(this).isGpsEnabled();
        gpsSection.setVisibility(gps ? View.VISIBLE : View.GONE);

        mapLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && pendingMapLat != null && pendingMapLon != null) {
                        double lat = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0);
                        double lon = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LON, 0);
                        pendingMapLat.setText(formatCoord(lat));
                        pendingMapLon.setText(formatCoord(lon));
                    }
                    pendingMapLat = null;
                    pendingMapLon = null;
                });
        ((MaterialButton) findViewById(R.id.btnAliasGpsAdd)).setOnClickListener(v -> addGpsRow(null, null));

        ((MaterialButton) findViewById(R.id.btnSaveAlias)).setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        typeLabels = new String[]{
                getString(R.string.alias_type_expense),
                getString(R.string.alias_type_income),
                getString(R.string.alias_type_transfer)};
        editType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, typeLabels));
        selectType(Repository.VOICE_TYPE_EXPENSE);

        repository.getPayeeNames(names -> editCorrected.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getAccountNames(names -> {
            ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
            editAccount.setAdapter(a);
            editFrom.setAdapter(a);
            editTo.setAdapter(a);
        });
        // Kategorie-Felder wie im Buchungseditor: gruppierte Baum-Anzeige (Überschrift + Einrückung).
        repository.getCategoriesGrouped(g -> {
            CategoryFilterAdapter expenseAdapter = new CategoryFilterAdapter(this, null,
                    getString(R.string.category_group_expense), g.expense, null, new ArrayList<>());
            CategoryFilterAdapter incomeAdapter = new CategoryFilterAdapter(this, null,
                    null, new ArrayList<>(), getString(R.string.category_group_income), g.income);
            editCatExpense1.setAdapter(expenseAdapter);
            editCatExpense2.setAdapter(expenseAdapter);
            editCatIncome1.setAdapter(incomeAdapter);
            editCatIncome2.setAdapter(incomeAdapter);
        });

        long id = getIntent().getLongExtra(EXTRA_ALIAS_ID, -1);
        if (id >= 0) {
            repository.getAlias(id, this::bind);
        } else {
            toolbar.setTitle(R.string.alias_new_title);
            btnDelete.setVisibility(android.view.View.GONE);
        }
    }

    private void bind(PayeeCorrection a) {
        if (a == null) {
            finish();
            return;
        }
        loaded = a;
        toolbar.setTitle(R.string.alias_edit_title);
        btnDelete.setVisibility(android.view.View.VISIBLE);
        editSpoken.setText(a.spoken);
        editCorrected.setText(a.corrected, false);
        selectType(a.type);
        editAccount.setText(a.account, false);
        editCatExpense1.setText(a.catExpense1, false);
        editCatExpense2.setText(a.catExpense2, false);
        editCatIncome1.setText(a.catIncome1, false);
        editCatIncome2.setText(a.catIncome2, false);
        editFrom.setText(a.fromAccount, false);
        editTo.setText(a.toAccount, false);
        // Ort-Dropdowns für die geladenen Konten aufbauen, dann die Alias-Orte setzen.
        setupPlaceField(editPlace, placeLayout, a.account);
        setupPlaceField(editFromPlace, fromPlaceLayout, a.fromAccount);
        setupPlaceField(editToPlace, toPlaceLayout, a.toAccount);
        editPlace.setText(a.place, false);
        editFromPlace.setText(a.fromPlace, false);
        editToPlace.setText(a.toPlace, false);
        switchPreferred.setChecked(a.preferred);
        gpsContainer.removeAllViews();
        for (double[] p : a.gpsPoints()) {
            addGpsRow(p[0], p[1]);
        }
    }

    private void save() {
        String spoken = text(editSpoken);
        String corrected = text(editCorrected);
        if (spoken.isEmpty() || corrected.isEmpty()) {
            Toast.makeText(this, R.string.alias_error_required, Toast.LENGTH_SHORT).show();
            return;
        }
        PayeeCorrection a = loaded != null ? loaded : new PayeeCorrection();
        a.spoken = spoken;
        a.corrected = corrected;
        a.type = currentType();
        a.account = text(editAccount);
        a.catExpense1 = text(editCatExpense1);
        a.catExpense2 = text(editCatExpense2);
        a.catIncome1 = text(editCatIncome1);
        a.catIncome2 = text(editCatIncome2);
        a.fromAccount = text(editFrom);
        a.toAccount = text(editTo);
        a.place = text(editPlace);
        a.fromPlace = text(editFromPlace);
        a.toPlace = text(editToPlace);
        a.preferred = switchPreferred.isChecked();
        // Standorte aus den Zeilen übernehmen (nur Zeilen mit gültiger Breite UND Länge).
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < gpsContainer.getChildCount(); i++) {
            View row = gpsContainer.getChildAt(i);
            Double lat = parseCoord(text((TextInputEditText) row.findViewById(R.id.gpsRowLat)));
            Double lon = parseCoord(text((TextInputEditText) row.findViewById(R.id.gpsRowLon)));
            if (lat != null && lon != null) {
                points.add(new double[]{lat, lon});
            }
        }
        a.setGpsPoints(points);
        repository.saveAlias(a);
        Toast.makeText(this, R.string.alias_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        if (loaded == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.alias_delete_confirm_title)
                .setPositiveButton(R.string.delete, (d, w) -> repository.deleteAlias(loaded.id, () -> {
                    Toast.makeText(this, R.string.alias_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Fügt eine Koordinaten-Zeile (Breite/Länge + Karte + Minus) hinzu; Werte optional vorbelegt. */
    private void addGpsRow(Double lat, Double lon) {
        View row = getLayoutInflater().inflate(R.layout.item_alias_gps_row, gpsContainer, false);
        TextInputEditText latField = row.findViewById(R.id.gpsRowLat);
        TextInputEditText lonField = row.findViewById(R.id.gpsRowLon);
        if (lat != null && lon != null) {
            latField.setText(formatCoord(lat));
            lonField.setText(formatCoord(lon));
        }
        row.findViewById(R.id.btnGpsRowMap).setOnClickListener(v -> openMap(latField, lonField));
        row.findViewById(R.id.btnGpsRowRemove).setOnClickListener(v -> gpsContainer.removeView(row));
        gpsContainer.addView(row);
    }

    /** Öffnet die Karten-Auswahl für eine Zeile, zentriert auf deren aktuelle Koordinaten (falls vorhanden). */
    private void openMap(TextInputEditText latField, TextInputEditText lonField) {
        pendingMapLat = latField;
        pendingMapLon = lonField;
        Intent i = new Intent(this, MapPickerActivity.class);
        Double lat = parseCoord(text(latField));
        Double lon = parseCoord(text(lonField));
        if (lat != null && lon != null) {
            i.putExtra(MapPickerActivity.EXTRA_LAT, (double) lat);
            i.putExtra(MapPickerActivity.EXTRA_LON, (double) lon);
        }
        mapLauncher.launch(i);
    }

    private static String formatCoord(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private static Double parseCoord(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(android.widget.EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    /** Befüllt ein Ort-Dropdown mit den Orten des Kontos und zeigt es nur, wenn das Konto Orte hat. */
    private void setupPlaceField(MaterialAutoCompleteTextView field, View layout, String account) {
        List<String> places = placesStore.getPlaces(account == null ? "" : account.trim());
        field.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, places));
        layout.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Kleiner TextWatcher, der nur auf Textänderungen reagiert. */
    private android.text.TextWatcher afterText(Runnable action) {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) { action.run(); }
        };
    }

    private void selectType(String type) {
        int idx = 0; // Standard: Ausgabe
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            if (TYPE_VALUES[i].equals(type)) {
                idx = i;
                break;
            }
        }
        editType.setText(typeLabels[idx], false);
    }

    private String currentType() {
        String label = text(editType);
        for (int i = 0; i < typeLabels.length; i++) {
            if (typeLabels[i].equals(label)) {
                return TYPE_VALUES[i];
            }
        }
        return Repository.VOICE_TYPE_EXPENSE;
    }
}

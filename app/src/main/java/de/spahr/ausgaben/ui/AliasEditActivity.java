package de.spahr.ausgaben.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.PayeeCorrection;
import de.spahr.ausgaben.db.Repository;

/** Formular zum Anlegen/Ändern/Löschen eines Alias mit allen Feldern (deckt alle Buchungsarten ab). */
public class AliasEditActivity extends AppCompatActivity {

    /** Zu bearbeitende Alias-ID; fehlt/−1 = neuer Alias. */
    public static final String EXTRA_ALIAS_ID = "alias_id";

    private Repository repository;
    private MaterialToolbar toolbar;
    private TextInputEditText editSpoken;
    private TextInputEditText editCorrected;
    private MaterialAutoCompleteTextView editType;
    private MaterialAutoCompleteTextView editAccount;
    private MaterialAutoCompleteTextView editCatExpense1;
    private MaterialAutoCompleteTextView editCatExpense2;
    private MaterialAutoCompleteTextView editCatIncome1;
    private MaterialAutoCompleteTextView editCatIncome2;
    private MaterialAutoCompleteTextView editFrom;
    private MaterialAutoCompleteTextView editTo;
    private com.google.android.material.materialswitch.MaterialSwitch switchPreferred;
    private MaterialButton btnDelete;

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
        switchPreferred = findViewById(R.id.switchAliasPreferred);
        btnDelete = findViewById(R.id.btnDeleteAlias);

        ((MaterialButton) findViewById(R.id.btnSaveAlias)).setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        typeLabels = new String[]{
                getString(R.string.alias_type_expense),
                getString(R.string.alias_type_income),
                getString(R.string.alias_type_transfer)};
        editType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, typeLabels));
        selectType(Repository.VOICE_TYPE_EXPENSE);

        repository.getAccountNames(names -> {
            ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
            editAccount.setAdapter(a);
            editFrom.setAdapter(a);
            editTo.setAdapter(a);
        });
        repository.getCategoryNames(names -> {
            editCatExpense1.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            editCatExpense2.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            editCatIncome1.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            editCatIncome2.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
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
        editCorrected.setText(a.corrected);
        selectType(a.type);
        editAccount.setText(a.account, false);
        editCatExpense1.setText(a.catExpense1, false);
        editCatExpense2.setText(a.catExpense2, false);
        editCatIncome1.setText(a.catIncome1, false);
        editCatIncome2.setText(a.catIncome2, false);
        editFrom.setText(a.fromAccount, false);
        editTo.setText(a.toAccount, false);
        switchPreferred.setChecked(a.preferred);
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
        a.preferred = switchPreferred.isChecked();
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

    private String text(android.widget.EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
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

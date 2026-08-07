package de.spahr.ausgaben.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Account;
import de.spahr.ausgaben.db.AccountGroup;
import de.spahr.ausgaben.db.AccountKind;
import de.spahr.ausgaben.db.AccountOrder;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * Konten sortieren und verwalten. Zeigt – anders als die Schublade – auch geschlossene Konten, diese in
 * grauer Schrift.
 *
 * <p>Gezogen wird an jeder Zeile: Konten nur innerhalb ihrer Kontenart, Kontenarten als Ganzes. Wird eine
 * Kontenart gezogen, klappen ihre Konten für die Dauer des Ziehens ein und es steht nur noch die Anzahl
 * darunter – sonst wäre ein Block mit vielen Konten kaum über den Bildschirm zu bewegen.</p>
 */
public class AccountManageActivity extends LocalizedActivity {

    private Repository repository;
    private RecyclerView list;
    private ManageAdapter adapter;

    /** Alle Konten (auch geschlossene), Saldo je Konto und die Reihenfolge der Kontenarten. */
    private final List<Account> accounts = new ArrayList<>();
    private final Map<String, Long> balances = new HashMap<>();
    private int[] kindOrder = AccountKind.ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_manage);
        repository = new Repository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        list = findViewById(R.id.accountManageList);
        list.setLayoutManager(new LinearLayoutManager(this));
        // Ohne Animationen: beim Einklappen während des Ziehens muss die Liste sofort stimmen, sonst
        // rutscht die angefasste Zeile unter dem Finger weg.
        list.setItemAnimator(null);
        adapter = new ManageAdapter();
        list.setAdapter(adapter);
        new ItemTouchHelper(new DragCallback()).attachToRecyclerView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        repository.getAccountKindOrder(order -> {
            kindOrder = order;
            repository.getAllAccountsWithStatus(all -> {
                accounts.clear();
                accounts.addAll(all);
                repository.getAllAccountBalances(map -> {
                    balances.clear();
                    balances.putAll(map);
                    loadDepotBalances();
                });
            });
        });
    }

    /** Depots haben keine Buchungen – ihr Saldo ist der Wert der gehaltenen Wertpapiere. */
    private void loadDepotBalances() {
        List<String> depots = new ArrayList<>();
        for (Account a : accounts) {
            if (a.isDepot()) {
                depots.add(a.name);
            }
        }
        if (depots.isEmpty()) {
            adapter.rebuild();
            return;
        }
        final int[] pending = {depots.size()};
        for (String depot : depots) {
            repository.getDepotHoldings(depot, holdings -> {
                long sum = 0;
                for (Repository.DepotHolding h : holdings) {
                    sum += h.valueCents;
                }
                balances.put(depot, sum);
                if (--pending[0] == 0) {
                    adapter.rebuild();
                }
            });
        }
    }

    private long balanceOf(Account account) {
        Long value = balances.get(account.name);
        return value == null ? 0L : value;
    }

    // ---- Drei-Punkte-Menü ----

    private void showRowMenu(Account account) {
        repository.getAccountGroups(groups -> {
            final List<AccountGroup> custom = new ArrayList<>();
            for (AccountGroup g : groups) {
                if (!g.auto) { // Bankgruppen spiegeln nur die .kmy-Datei und sind nicht wählbar
                    custom.add(g);
                }
            }
            repository.getAccountGroupIds(account.name, member -> showRowMenu(account, custom, member));
        });
    }

    private void showRowMenu(Account account, List<AccountGroup> custom, Set<Long> member) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        for (AccountGroup g : custom) {
            final boolean isMember = member.contains(g.id);
            labels.add(getString(isMember ? R.string.accounts_group_remove : R.string.accounts_group_add,
                    g.name));
            actions.add(() -> repository.setAccountGroupMembership(account.name, g.id, !isMember,
                    () -> toast(getString(isMember ? R.string.accounts_group_removed
                            : R.string.accounts_group_added, g.name))));
        }
        labels.add(getString(R.string.accounts_group_new));
        actions.add(() -> askNewGroup(account));
        if (account.closed) {
            labels.add(getString(R.string.accounts_manage_reopen));
            actions.add(() -> repository.setAccountClosed(account.name, false, () -> {
                toast(getString(R.string.account_reopened_done, account.name));
                refresh();
            }));
        } else if (balanceOf(account) == 0) {
            // Schließen nur bei Saldo 0 – wie in der Kontenverwaltung der Einstellungen.
            labels.add(getString(R.string.accounts_manage_close));
            actions.add(() -> repository.setAccountClosed(account.name, true, () -> {
                toast(getString(R.string.account_closed_done, account.name));
                refresh();
            }));
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(account.name)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void askNewGroup(Account account) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.accounts_group_new)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    final String name = input.getText().toString().trim();
                    repository.createAccountGroup(name, account.name, id -> toast(id > 0
                            ? getString(R.string.accounts_group_added, name)
                            : getString(R.string.accounts_group_empty)));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---- Liste ----

    /** Eine Zeile: entweder eine Kontenart-Überschrift oder ein Konto. */
    private static final class Row {
        final int kind;
        final Account account; // null = Überschrift
        Row(int kind, Account account) {
            this.kind = kind;
            this.account = account;
        }
        boolean isHeader() {
            return account == null;
        }
    }

    private class ManageAdapter extends RecyclerView.Adapter<VH> {

        private final List<Row> rows = new ArrayList<>();
        /** true, solange eine Kontenart gezogen wird – dann sind nur die Überschriften sichtbar. */
        private boolean collapsed;

        void rebuild() {
            rows.clear();
            for (int kind : kindOrder) {
                rows.add(new Row(kind, null));
                if (collapsed) {
                    continue;
                }
                for (Account a : accountsOf(kind)) {
                    rows.add(new Row(kind, a));
                }
            }
            notifyDataSetChanged();
        }

        List<Account> accountsOf(int kind) {
            List<Account> out = new ArrayList<>();
            for (Account a : accounts) {
                if (AccountKind.of(a.kmyType) == kind) {
                    out.add(a);
                }
            }
            AccountOrder.sortWithinKind(out);
            return out;
        }

        /**
         * Klappt für die Dauer eines Kontenart-Ziehens alle Konten ein. Bewusst über gezielte
         * Entfern-Meldungen statt über einen Komplett-Neuaufbau: so behält die gerade angefasste Zeile
         * ihren ViewHolder, und das Ziehen läuft ohne Unterbrechung weiter.
         */
        void collapseForDrag() {
            if (collapsed) {
                return;
            }
            collapsed = true;
            for (int i = rows.size() - 1; i >= 0; i--) {
                if (rows.get(i).isHeader()) {
                    continue;
                }
                int last = i;
                while (i > 0 && !rows.get(i - 1).isHeader()) {
                    i--;
                }
                int count = last - i + 1;
                rows.subList(i, last + 1).clear();
                notifyItemRangeRemoved(i, count);
            }
            for (int i = 0; i < rows.size(); i++) {
                notifyItemChanged(i); // Überschriften tragen jetzt die Anzahl ihrer Konten
            }
        }

        /** Klappt nach dem Ziehen wieder auf. */
        void expandAfterDrag() {
            collapsed = false;
            rebuild();
        }

        int positionOfKind(int kind) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).isHeader() && rows.get(i).kind == kind) {
                    return i;
                }
            }
            return 0;
        }

        Row row(int position) {
            return rows.get(position);
        }

        /** Verschiebt eine Zeile in der Anzeige; gespeichert wird erst beim Loslassen. */
        void move(int from, int to) {
            Row moved = rows.remove(from);
            rows.add(to, moved);
            notifyItemMoved(from, to);
        }

        /** Die aktuelle Reihenfolge der Kontenarten, wie sie in der Liste steht. */
        int[] currentKindOrder() {
            List<Integer> kinds = new ArrayList<>();
            for (Row r : rows) {
                if (r.isHeader()) {
                    kinds.add(r.kind);
                }
            }
            int[] out = new int[kinds.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = kinds.get(i);
            }
            return out;
        }

        /** Die Konten einer Kontenart in der aktuellen Anzeigereihenfolge. */
        List<Account> currentAccounts(int kind) {
            List<Account> out = new ArrayList<>();
            for (Row r : rows) {
                if (!r.isHeader() && r.kind == kind) {
                    out.add(r.account);
                }
            }
            return out;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_account_manage, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = rows.get(position);
            if (r.isHeader()) {
                bindHeader(h, r.kind);
            } else {
                bindAccount(h, r.account);
            }
        }

        private void bindHeader(VH h, int kind) {
            int resId;
            int lightBg;
            int darkBg;
            if (kind == AccountKind.LIABILITY) {
                resId = R.string.accounts_liability;
                lightBg = CategoryColors.LIGHT_LIABILITY;
                darkBg = CategoryColors.DARK_LIABILITY;
            } else if (kind == AccountKind.DEPOT) {
                resId = R.string.accounts_depot;
                lightBg = CategoryColors.LIGHT_DEPOT;
                darkBg = CategoryColors.DARK_DEPOT;
            } else {
                resId = R.string.accounts_asset;
                lightBg = CategoryColors.LIGHT_ASSET;
                darkBg = CategoryColors.DARK_ASSET;
            }
            boolean night = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            String label = getString(resId);
            if (collapsed) {
                // Eingeklappt: nur die Kontenart und wie viele Konten mitwandern.
                label = label + "  " + getString(R.string.accounts_manage_count, accountsOf(kind).size());
            }
            h.name.setText(label);
            h.name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            h.itemView.setBackgroundColor(night ? darkBg : lightBg);
            int fg = night ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;
            h.name.setTextColor(fg);
            h.handle.setColorFilter(fg);
            h.balance.setText("");
            h.menu.setVisibility(View.GONE);
        }

        private void bindAccount(VH h, Account account) {
            h.name.setText(account.name);
            h.name.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            h.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            long cents = balanceOf(account);
            h.balance.setText(MoneyFormat.display(cents, account.currency));
            if (account.closed) {
                // Geschlossene Konten bleiben sichtbar, treten aber zurück.
                int grey = getColor(R.color.closed_account_text);
                h.name.setTextColor(grey);
                h.balance.setTextColor(grey);
            } else {
                h.name.setTextColor(primaryText());
                h.balance.setTextColor(getColor(cents < 0 ? R.color.expense_red : R.color.income_green));
            }
            // Beide Symbole sind weiß gezeichnet und brauchen die Vordergrundfarbe der Zeile.
            h.handle.setColorFilter(getColor(R.color.grey_text));
            h.menu.setColorFilter(primaryText());
            h.menu.setVisibility(View.VISIBLE);
            h.menu.setOnClickListener(v -> showRowMenu(account));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private int primaryText() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        return getColor(tv.resourceId != 0 ? tv.resourceId : android.R.color.black);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView handle;
        final TextView name;
        final TextView balance;
        final ImageView menu;
        VH(View v) {
            super(v);
            handle = v.findViewById(R.id.dragHandle);
            name = v.findViewById(R.id.rowName);
            balance = v.findViewById(R.id.rowBalance);
            menu = v.findViewById(R.id.rowMenu);
        }
    }

    /**
     * Ziehen zum Sortieren. Eine Kontenart nimmt beim Anfassen ihre Konten mit, indem alle Blöcke
     * einklappen; ein Konto bleibt in seinem Block gefangen.
     */
    private class DragCallback extends ItemTouchHelper.Callback {

        /** Kontenart der angefassten Zeile, solange gezogen wird; -1 = kein Ziehen. */
        private int draggedKind = -1;
        private boolean draggedHeader;

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = vh.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return false;
            }
            Row moved = adapter.row(from);
            Row over = adapter.row(to);
            if (moved.isHeader() != over.isHeader()) {
                return false; // Überschrift und Konto tauschen nicht die Plätze
            }
            if (!moved.isHeader() && moved.kind != over.kind) {
                return false; // Konten bleiben in ihrer Kontenart
            }
            adapter.move(from, to);
            return true;
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
            super.onSelectedChanged(vh, actionState);
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || vh == null) {
                return;
            }
            int pos = vh.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            Row r = adapter.row(pos);
            draggedKind = r.kind;
            draggedHeader = r.isHeader();
            if (draggedHeader) {
                collapseKeepingRowUnderFinger(vh);
            }
        }

        /**
         * Klappt die Konten ein und schiebt die Liste anschließend so weit nach unten, dass die
         * angefasste Kontenart genau dort bleibt, wo der Finger sie ergriffen hat. Ohne das rutschte
         * sie beim Einklappen nach oben weg und ließe sich nicht mehr ziehen.
         */
        private void collapseKeepingRowUnderFinger(RecyclerView.ViewHolder vh) {
            final int grabbedAt = vh.itemView.getTop();
            adapter.collapseForDrag();
            list.post(() -> {
                int nowAt = vh.itemView.getTop();
                int shift = grabbedAt - nowAt;
                if (shift > 0) {
                    // Der Freiraum entsteht als oberer Innenabstand; die eingeklappte Liste ist zu kurz,
                    // um so weit gescrollt werden zu können.
                    list.setClipToPadding(false);
                    list.setPadding(list.getPaddingLeft(), shift,
                            list.getPaddingRight(), list.getPaddingBottom());
                }
            });
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            if (draggedHeader) {
                kindOrder = adapter.currentKindOrder();
                repository.saveAccountKindOrder(kindOrder, null);
                rv.setPadding(rv.getPaddingLeft(), 0, rv.getPaddingRight(), rv.getPaddingBottom());
                rv.setClipToPadding(true);
                adapter.expandAfterDrag();
            } else if (draggedKind >= 0) {
                repository.saveAccountOrder(adapter.currentAccounts(draggedKind), null);
            }
            draggedKind = -1;
            draggedHeader = false;
        }

        @Override
        public int interpolateOutOfBoundsScroll(@NonNull RecyclerView rv, int viewSize,
                                                int viewSizeOutOfBounds, int totalSize, long msSinceStartScroll) {
            if (draggedHeader) {
                // Eingeklappt stehen nur drei Zeilen in der Liste – automatisches Scrollen am Rand würde
                // die angefasste Kontenart nur unter dem Finger wegziehen.
                return 0;
            }
            return super.interpolateOutOfBoundsScroll(rv, viewSize, viewSizeOutOfBounds, totalSize,
                    msSinceStartScroll);
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        }
    }
}

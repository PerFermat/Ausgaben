package de.spahr.ausgaben.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.BookingSplit;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.VH> {

    public interface Listener {
        /** Wird bei langem Druck auf eine Buchung ausgelöst (Bearbeiten). */
        void onLongClick(Booking b);
    }

    private final List<Booking> items = new ArrayList<>();
    private Map<Long, List<BookingSplit>> splitsByBooking = new HashMap<>();
    /** Angezeigter (vorzeichenbehafteter) Betrag je Buchung – überschreibt den Gesamtbetrag (Kategorie-Filter). */
    private Map<Long, Long> amountOverride = new HashMap<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Kategorie-Teile je Buchung, um Splitbuchungen in der Liste zu markieren. */
    public void setSplits(Map<Long, List<BookingSplit>> splits) {
        splitsByBooking = splits == null ? new HashMap<>() : splits;
        notifyDataSetChanged();
    }

    /** Setzt Betrags-Overrides (z. B. Teilbetrag der gefilterten Kategorie); leer = Gesamtbeträge. */
    public void setAmountOverride(Map<Long, Long> override) {
        amountOverride = override == null ? new HashMap<>() : override;
    }

    public void setItems(List<Booking> bookings) {
        items.clear();
        if (bookings != null) {
            items.addAll(bookings);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_booking, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Booking b = items.get(position);
        if (b.isTransfer) {
            // Umbuchung: Richtung + Zahlungsempfänger als Titel (→ Ausgang, ← Eingang).
            // Ohne Empfänger auf das Gegenkonto zurückfallen.
            String label = !b.payee.isEmpty() ? b.payee
                    : (b.transferAccount == null || b.transferAccount.isEmpty()
                        ? h.payee.getContext().getString(R.string.type_transfer) : b.transferAccount);
            h.payee.setText((b.isIncome ? "← " : "→ ") + label);
        } else {
            h.payee.setText(b.payee.isEmpty() ? "—" : b.payee);
        }
        // Konto und Datum in einer kompakten Zeile (kMyMoney-Stil): „Konto · TT.MM.JJJJ HH:mm".
        String date = dateFormat.format(new Date(b.createdAt));
        String line = b.account.isEmpty() ? date : b.account + " · " + date;
        List<BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts != null && parts.size() >= 2) {
            line = line + "  ·  " + h.account.getContext().getString(R.string.split_marker);
        }
        h.account.setText(line);
        h.date.setVisibility(View.GONE);

        if (b.note == null || b.note.isEmpty()) {
            h.note.setVisibility(View.GONE);
        } else {
            h.note.setVisibility(View.VISIBLE);
            h.note.setText(b.note);
        }

        long signed = b.isIncome ? b.amountCents : -b.amountCents;
        // Bei Kategorie-Filter zeigt eine Splitbuchung nur den Teilbetrag der gewählten Kategorie.
        Long override = amountOverride.get(b.id);
        if (override != null) {
            signed = override;
        }
        String euros = formatEuro(signed);
        h.amount.setText(euros);
        // Farbe nach angezeigtem Vorzeichen: negativ (auch negativer Teilbetrag) = rot, sonst grün.
        int color = signed < 0
                ? h.amount.getResources().getColor(R.color.expense_red, null)
                : h.amount.getResources().getColor(R.color.income_green, null);
        h.amount.setTextColor(color);

        h.exported.setVisibility(b.exported ? View.VISIBLE : View.GONE);

        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(b);
                return true;
            }
            return false;
        });
    }

    private String formatEuro(long signedCents) {
        long euros = signedCents / 100;
        long cents = Math.abs(signedCents % 100);
        String sign = (signedCents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", cents) + " €";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView payee;
        final TextView account;
        final TextView date;
        final TextView note;
        final TextView amount;
        final TextView exported;

        VH(@NonNull View itemView) {
            super(itemView);
            payee = itemView.findViewById(R.id.textPayee);
            account = itemView.findViewById(R.id.textAccount);
            date = itemView.findViewById(R.id.textDate);
            note = itemView.findViewById(R.id.textNote);
            amount = itemView.findViewById(R.id.textAmount);
            exported = itemView.findViewById(R.id.textExported);
        }
    }
}

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
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.VH> {

    public interface Listener {
        void onClick(Booking b);

        void onLongClick(Booking b);
    }

    private final List<Booking> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
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
        h.payee.setText(b.payee.isEmpty() ? "—" : b.payee);
        h.account.setText(b.account);
        h.date.setText(dateFormat.format(new Date(b.createdAt)));

        if (b.note == null || b.note.isEmpty()) {
            h.note.setVisibility(View.GONE);
        } else {
            h.note.setVisibility(View.VISIBLE);
            h.note.setText(b.note);
        }

        long signed = b.isIncome ? b.amountCents : -b.amountCents;
        String euros = formatEuro(signed);
        h.amount.setText(euros);
        int color = b.isIncome
                ? h.amount.getResources().getColor(R.color.income_green, null)
                : h.amount.getResources().getColor(R.color.expense_red, null);
        h.amount.setTextColor(color);

        h.exported.setVisibility(b.exported ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(b);
            }
        });
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

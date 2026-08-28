package de.spahr.ausgaben.ui;

import android.content.Context;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.statement.StatementTemplate.Field;

/**
 * Wie die Felder einer Abrechnung heißen — auf der Regelseite und in der Rückmeldung nach dem Merken.
 *
 * <p>Die Namen hängen an der Art: was beim Kauf die <i>Gebühr</i> ist, ist bei einer Dividende die
 * <i>Steuer</i>, und der <i>Gesamtbetrag</i> heißt dort <i>Netto</i>. Beide Seiten sprechen vom selben
 * Feld und müssen es gleich nennen; deshalb steht die Zuordnung hier und nicht zweimal.</p>
 */
final class StatementFieldNames {

    private StatementFieldNames() {
    }

    static String of(Context context, Field field, String action) {
        boolean dividend = "dividend".equals(action);
        switch (field) {
            case NET:
                return context.getString(
                        dividend ? R.string.security_tx_net : R.string.security_tx_total);
            case FEE:
                return context.getString(
                        dividend ? R.string.security_tx_tax : R.string.security_tx_fee);
            case GROSS:
                return context.getString(R.string.security_tx_gross);
            case SHARES:
                return context.getString(R.string.security_tx_shares);
            case PRICE:
                return context.getString(dividend
                        ? R.string.security_tx_price_dividend : R.string.security_tx_price);
            default:
                return context.getString(R.string.date_hint);
        }
    }
}

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
 *
 * <p>Der Verkauf steht dabei zwischen beiden: er zieht wie ein Kauf Gebühren ab, dazu aber die
 * Kapitalertragsteuer — deshalb <i>Gebühren/Steuern</i>. Und was unter dem Strich bleibt, wird beim
 * Verkauf nicht abgebucht, sondern überwiesen: es ist die <i>Gutschrift</i>.</p>
 */
final class StatementFieldNames {

    private StatementFieldNames() {
    }

    static String of(Context context, Field field, String action) {
        boolean dividend = "dividend".equals(action);
        boolean verkauf = "sell".equals(action);
        switch (field) {
            case NET:
                return context.getString(dividend ? R.string.security_tx_net
                        : verkauf ? R.string.security_tx_total_sell : R.string.security_tx_total);
            case FEE:
                return context.getString(dividend ? R.string.security_tx_tax
                        : verkauf ? R.string.security_tx_fee_sell : R.string.security_tx_fee);
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

    /**
     * Die Überschrift über den Kategoriezeilen der Gebühr bzw. Steuer.
     *
     * <p>Sie folgt derselben Unterscheidung wie das Betragsfeld darüber — stünde dort „Gebühren/Steuern"
     * und darunter „Gebührenkategorien", sähe es nach zwei verschiedenen Dingen aus.</p>
     */
    static String feeCategoryHeading(Context context, String action) {
        if ("dividend".equals(action)) {
            return context.getString(R.string.security_tx_tax_category);
        }
        return context.getString("sell".equals(action)
                ? R.string.security_tx_fee_category_sell : R.string.security_tx_fee_category);
    }
}

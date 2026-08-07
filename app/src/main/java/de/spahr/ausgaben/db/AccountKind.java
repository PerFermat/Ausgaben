package de.spahr.ausgaben.db;

/**
 * Die drei Kontenarten, nach denen jede Kontenliste gegliedert ist: Anlage, Verbindlichkeit, Depot.
 *
 * <p>Die Kontenart ergibt sich aus dem KMyMoney-Kontotyp und ist nicht wählbar – im Unterschied zur
 * {@link AccountGroup Kontengruppe}, die der Nutzer selbst vergibt und die mehrfach zutreffen kann.</p>
 *
 * <p>Reine Rechenregeln ohne Android-Bezug, damit sie im JVM-Test festgehalten werden können.</p>
 */
public final class AccountKind {

    public static final int ASSET = 0;
    public static final int LIABILITY = 1;
    public static final int DEPOT = 2;

    /** Alle Kontenarten in ihrer ursprünglichen Reihenfolge (Vorgabe, bevor der Nutzer sortiert). */
    public static final int[] ALL = {ASSET, LIABILITY, DEPOT};

    private AccountKind() {
    }

    /** Kontenart eines KMyMoney-Kontotyps. */
    public static int of(int kmyType) {
        if (kmyType == Account.KMY_TYPE_DEPOT) {
            return DEPOT;
        }
        return Account.isLiabilityType(kmyType) ? LIABILITY : ASSET;
    }
}

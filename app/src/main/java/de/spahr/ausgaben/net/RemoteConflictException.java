package de.spahr.ausgaben.net;

import java.io.IOException;

/**
 * Die entfernte Datei wurde seit dem Herunterladen von jemand anderem geändert (z. B. von KMyMoney am
 * Rechner). Wird beim Rückschreiben geworfen, <b>bevor</b> etwas überschrieben wird – die fremden
 * Änderungen bleiben damit erhalten.
 */
public class RemoteConflictException extends IOException {

    public RemoteConflictException(String message) {
        super(message);
    }
}

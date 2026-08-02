package de.spahr.ausgaben.net.smb;

import android.content.Context;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import de.spahr.ausgaben.R;

/**
 * Übersetzt die Ausnahmen der SMB-Schicht in Meldungen, die dem Benutzer sagen, was zu tun ist –
 * statt der rohen smbj-Texte („Authentication failed for …", „STATUS_BAD_NETWORK_NAME").
 */
public final class SmbErrors {

    /** In welchem Schritt der Fehler auftrat; entscheidet über die Deutung von „Zugriff verweigert". */
    public enum Step { CONNECT, LOGIN, SHARE, FOLDER }

    private SmbErrors() {
    }

    public static String messageFor(Context context, Step step, Throwable error) {
        String raw = textOf(error);
        String upper = raw.toUpperCase(java.util.Locale.ROOT);
        if (hasCause(error, UnknownHostException.class, SocketTimeoutException.class,
                ConnectException.class, NoRouteToHostException.class)
                || upper.contains("STATUS_IO_TIMEOUT")
                || upper.contains("STATUS_BAD_NETWORK_PATH")) {
            return context.getString(R.string.smb_err_unreachable);
        }
        if (upper.contains("STATUS_LOGON_FAILURE") || upper.contains("STATUS_ACCOUNT_")
                || upper.contains("STATUS_PASSWORD_") || upper.contains("STATUS_WRONG_PASSWORD")
                || upper.contains("STATUS_NO_SUCH_USER") || upper.contains("AUTHENTICATION")) {
            return context.getString(R.string.smb_err_credentials);
        }
        if (upper.contains("STATUS_BAD_NETWORK_NAME") || upper.contains("STATUS_OBJECT_PATH_NOT_FOUND")
                || upper.contains("STATUS_NETWORK_NAME_DELETED")) {
            return context.getString(R.string.smb_err_share);
        }
        if (upper.contains("STATUS_ACCESS_DENIED") || upper.contains("STATUS_SHARING_VIOLATION")) {
            return context.getString(step == Step.FOLDER || step == Step.SHARE
                    ? R.string.smb_err_folder_denied : R.string.smb_err_credentials);
        }
        switch (step) {
            case CONNECT:
                return context.getString(R.string.smb_err_unreachable);
            case LOGIN:
                return context.getString(R.string.smb_err_credentials);
            case SHARE:
                return context.getString(R.string.smb_err_share);
            default:
                return context.getString(R.string.conn_failed, raw);
        }
    }

    private static String textOf(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = error; t != null && sb.length() < 500; t = t.getCause()) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            } else {
                sb.append(t.getClass().getSimpleName()).append(' ');
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return sb.toString().trim();
    }

    @SafeVarargs
    private static boolean hasCause(Throwable error, Class<? extends IOException>... types) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            for (Class<?> type : types) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}

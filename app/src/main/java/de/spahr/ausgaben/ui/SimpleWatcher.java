package de.spahr.ausgaben.ui;

import android.text.Editable;
import android.text.TextWatcher;

/** TextWatcher, der bei jeder Änderung eine Aktion ausführt. */
final class SimpleWatcher implements TextWatcher {

    private final Runnable action;

    SimpleWatcher(Runnable action) {
        this.action = action;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        action.run();
    }
}

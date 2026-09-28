package com.alfread.floatspace.overlay;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional helper. FloatSpace does not use accessibility to read content.
 * When enabled by the user, Android can report window-state changes to this
 * process, which is useful on OEMs that expose freeform tasks differently.
 */
public final class WindowAccessibilityService extends AccessibilityService {
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally no content inspection.
    }

    @Override
    public void onInterrupt() {
    }
}

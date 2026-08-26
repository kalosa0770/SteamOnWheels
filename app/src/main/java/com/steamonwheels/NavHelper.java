package com.steamonwheels;

import android.app.Activity;
import android.widget.TextView;

/**
 * Highlights the active tab in the shared bottom_nav.xml include. All four
 * pupil-facing screens (Home, All Lessons, Progress, Profile) include the
 * same static nav bar, so each screen calls this once in onCreate to mark
 * itself as the active tab.
 */
public class NavHelper {
    private static final int ACTIVE_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_COLOR = 0xFFB7C4C8;

    public static void highlightTab(Activity activity, int activeTabId) {
        int[] tabIds = { R.id.navHome, R.id.navLessons, R.id.navProgress, R.id.navProfile };
        for (int id : tabIds) {
            TextView tab = activity.findViewById(id);
            if (tab != null) {
                tab.setTextColor(id == activeTabId ? ACTIVE_COLOR : INACTIVE_COLOR);
            }
        }
    }
}

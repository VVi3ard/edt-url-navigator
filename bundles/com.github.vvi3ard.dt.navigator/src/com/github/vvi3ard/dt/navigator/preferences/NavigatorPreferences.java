package com.github.vvi3ard.dt.navigator.preferences;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import com.github.vvi3ard.dt.navigator.NavigatorPlugin;

public final class NavigatorPreferences {
    public static final String PREF_PORT = "server_port";
    public static final int DEFAULT_PORT = 18321;

    private NavigatorPreferences() {}

    public static IEclipsePreferences getPreferences() {
        return InstanceScope.INSTANCE.getNode(NavigatorPlugin.PLUGIN_ID);
    }

    public static int getPort() {
        return getPreferences().getInt(PREF_PORT, DEFAULT_PORT);
    }

    public static void setPort(int port) {
        IEclipsePreferences prefs = getPreferences();
        prefs.putInt(PREF_PORT, port);
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            NavigatorPlugin.logError("Failed to save preferences", e);
        }
    }
}

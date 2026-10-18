package com.github.vvi3ard.dt.navigator.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import com.github.vvi3ard.dt.navigator.NavigatorPlugin;

public class NavigatorPreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences node = DefaultScope.INSTANCE.getNode(NavigatorPlugin.PLUGIN_ID);
        node.putInt(NavigatorPreferences.PREF_PORT, NavigatorPreferences.DEFAULT_PORT);
    }
}

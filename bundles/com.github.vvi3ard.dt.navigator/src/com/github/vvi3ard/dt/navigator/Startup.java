package com.github.vvi3ard.dt.navigator;

import org.eclipse.ui.IStartup;

public class Startup implements IStartup {
    @Override
    public void earlyStartup() {
        NavigatorPlugin plugin = NavigatorPlugin.getDefault();
        if (plugin != null) {
            plugin.startServerIfNeeded();
        }
    }
}

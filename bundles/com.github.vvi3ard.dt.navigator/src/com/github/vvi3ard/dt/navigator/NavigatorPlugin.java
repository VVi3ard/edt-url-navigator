package com.github.vvi3ard.dt.navigator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.github.vvi3ard.dt.navigator.preferences.NavigatorPreferences;

public class NavigatorPlugin extends AbstractUIPlugin {
    public static final String PLUGIN_ID = "com.github.vvi3ard.dt.navigator";

    private static NavigatorPlugin plugin;
    private NavigationServer server;

    public NavigatorPlugin() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        startServerIfNeeded();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (server != null) {
            server.stop();
            server = null;
        }
        plugin = null;
        super.stop(context);
    }

    public static NavigatorPlugin getDefault() {
        return plugin;
    }

    public synchronized void startServerIfNeeded() {
        if (server == null || !server.isRunning()) {
            server = new NavigationServer();
            int port = NavigatorPreferences.getPort();
            try {
                server.start(port);
            } catch (IOException e) {
                logError("Failed to start NavigationServer on port " + port, e);
            }
        }
    }

    public synchronized void restartServer(int newPort) throws IOException {
        if (server != null) {
            server.stop();
        }
        server = new NavigationServer();
        server.start(newPort);
    }

    public synchronized NavigationServer getServer() {
        return server;
    }

    public static Path getConfigDir() {
        Path dir = Path.of(System.getProperty("user.home"), ".edt-navigator");
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            logError("Failed to create config dir: " + dir, e);
        }
        return dir;
    }

    public static void logInfo(String message) {
        if (plugin != null) {
            ILog log = plugin.getLog();
            log.log(new Status(IStatus.INFO, PLUGIN_ID, message));
        } else {
            System.out.println("[EDT-Navigator] " + message);
        }
    }

    public static void logError(String message, Throwable t) {
        if (plugin != null) {
            ILog log = plugin.getLog();
            log.log(new Status(IStatus.ERROR, PLUGIN_ID, message, t));
        } else {
            System.err.println("[EDT-Navigator] ERROR: " + message);
            if (t != null) {
                t.printStackTrace();
            }
        }
    }
}

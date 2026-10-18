package com.github.vvi3ard.dt.navigator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class NavigationServer {
    private HttpServer server;
    private int port;
    private ThreadPoolExecutor executor;

    public synchronized void start(int port) throws IOException {
        if (server != null) {
            stop();
        }

        this.port = port;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/open", new OpenHandler());
        server.createContext("/health", new HealthHandler());

        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.start();

        NavigatorPlugin.logInfo("NavigationServer started listening on 127.0.0.1:" + port);
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception e) {
                NavigatorPlugin.logError("Error stopping server", e);
            }
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        NavigatorPlugin.logInfo("NavigationServer stopped");
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return port;
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String response = "{\"status\":\"ok\",\"service\":\"1C:EDT URL Navigator\",\"port\":" + port + "}\n";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class OpenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String accept = exchange.getRequestHeaders().getFirst("Accept");
            boolean isHtml = "GET".equalsIgnoreCase(method) && accept != null && accept.contains("text/html");

            String rawQuery = exchange.getRequestURI().getRawQuery();
            String body = readBody(exchange);

            ModuleNavigator.NavigationTarget target = parseTarget(rawQuery, body);

            if (target == null || (isEmpty(target.module) && isEmpty(target.file) && isEmpty(target.path) && isEmpty(target.md))) {
                String errorMsg = "Missing required parameters (module, md, file or path)";
                if (isHtml) {
                    sendHtmlResponse(exchange, 400, buildHtmlPage("Ошибка 400: Не указаны параметры", errorMsg, false));
                } else {
                    sendJsonResponse(exchange, 400, "{\"status\":\"error\",\"code\":400,\"message\":\"" + escapeJson(errorMsg) + "\"}\n");
                }
                return;
            }

            // Synchronously resolve file before returning response
            java.io.File resolvedFile = ModuleNavigator.resolveFile(target);
            if (resolvedFile == null || !resolvedFile.isFile()) {
                String errorMsg = "Object not found for target: " + target;
                NavigatorPlugin.logError("Navigation failed: " + errorMsg, null);
                if (isHtml) {
                    sendHtmlResponse(exchange, 404, buildHtmlPage("Объект не найден (404)", errorMsg, false));
                } else {
                    sendJsonResponse(exchange, 404, "{\"status\":\"error\",\"code\":404,\"message\":\"" + escapeJson(errorMsg) + "\"}\n");
                }
                return;
            }

            Display display = null;
            if (PlatformUI.isWorkbenchRunning()) {
                display = PlatformUI.getWorkbench().getDisplay();
            } else {
                display = Display.getDefault();
            }

            if (display != null && !display.isDisposed()) {
                display.asyncExec(() -> {
                    ModuleNavigator.openResolved(resolvedFile, target);
                });
            }

            if (isHtml) {
                String targetDesc = target.module != null ? target.module : (target.md != null ? target.md : resolvedFile.getName());
                sendHtmlResponse(exchange, 200, buildHtmlPage("Открыто в 1C:EDT", "Объект <b>" + escapeHtml(targetDesc) + "</b> открыт в EDT.", true));
            } else {
                String okJson = "{\"status\":\"ok\",\"code\":200,\"message\":\"Navigation dispatched\",\"target\":\"" + escapeJson(target.toString()) + "\",\"file\":\"" + escapeJson(resolvedFile.getAbsolutePath()) + "\"}\n";
                sendJsonResponse(exchange, 200, okJson);
            }
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendHtmlResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String buildHtmlPage(String title, String message, boolean autoClose) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>")
          .append(escapeHtml(title))
          .append("</title>\n");
        if (autoClose) {
            sb.append("<script>\n")
              .append("window.open('', '_self', '');\n")
              .append("window.close();\n")
              .append("setTimeout(function(){ window.close(); }, 350);\n")
              .append("</script>\n");
        }
        sb.append("<style>\n")
          .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f8f9fa; color: #212529; text-align: center; padding-top: 15vh; margin: 0; }\n")
          .append(".card { display: inline-block; background: #fff; padding: 30px 45px; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); max-width: 500px; text-align: center; }\n")
          .append("h2 { margin: 0 0 12px; font-size: 20px; color: ").append(autoClose ? "#2e7d32" : "#c62828").append("; }\n")
          .append("p { margin: 0; font-size: 14px; color: #555; line-height: 1.5; }\n")
          .append("small { display: block; margin-top: 15px; color: #888; font-size: 12px; }\n")
          .append("</style>\n</head>\n<body>\n<div class=\"card\">\n")
          .append("<h2>").append(autoClose ? "✓ " : "✗ ").append(escapeHtml(title)).append("</h2>\n")
          .append("<p>").append(message).append("</p>\n");
        if (autoClose) {
            sb.append("<small>Вкладка закроется автоматически. Если нет — закройте её вручную.</small>\n");
        }
        sb.append("</div>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    public static ModuleNavigator.NavigationTarget parseTarget(String query, String body) {
        ModuleNavigator.NavigationTarget target = new ModuleNavigator.NavigationTarget();
        Map<String, String> params = new HashMap<>();

        // 1. Parse query string if present
        if (query != null && !query.isBlank()) {
            parseParamsInto(query, params);
        }

        // 2. Parse body
        if (body != null && !body.isBlank()) {
            target.rawUrl = body;
            if (body.startsWith("{") && body.endsWith("}")) {
                // JSON payload
                parseJsonInto(body, params);
            } else {
                // May be raw edt:// URL or form-urlencoded
                parseParamsInto(body, params);
            }
        }

        // Map params to target
        if (params.containsKey("module")) target.module = params.get("module");
        if (params.containsKey("file")) target.file = params.get("file");
        if (params.containsKey("path")) target.path = params.get("path");
        if (params.containsKey("project")) target.project = params.get("project");
        if (params.containsKey("md")) target.md = params.get("md");
        if (params.containsKey("method")) target.method = params.get("method");
        if (params.containsKey("attr")) target.attr = params.get("attr");
        if (params.containsKey("search")) target.search = params.get("search");

        if (params.containsKey("line")) {
            try {
                target.line = Integer.parseInt(params.get("line"));
            } catch (NumberFormatException ignored) {}
        }

        return target;
    }

    private static void parseParamsInto(String text, Map<String, String> out) {
        String clean = text.trim();
        // Check for edt://open?... or edt:open?...
        if (clean.startsWith("edt://") || clean.startsWith("edt:")) {
            int qIndex = clean.indexOf('?');
            if (qIndex >= 0) {
                clean = clean.substring(qIndex + 1);
            }
        } else if (clean.startsWith("open?")) {
            clean = clean.substring(5);
        } else if (clean.startsWith("/open?")) {
            clean = clean.substring(6);
        }

        String[] pairs = clean.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) continue;
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = urlDecode(pair.substring(0, eq).trim());
                String val = urlDecode(pair.substring(eq + 1).trim());
                out.put(key.toLowerCase(), val);
            } else {
                out.put(urlDecode(pair.trim()).toLowerCase(), "");
            }
        }
    }

    private static void parseJsonInto(String json, Map<String, String> out) {
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9]+))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase();
            String val = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            out.put(key, val);
        }
    }

    private static String urlDecode(String val) {
        try {
            return URLDecoder.decode(val, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return val;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

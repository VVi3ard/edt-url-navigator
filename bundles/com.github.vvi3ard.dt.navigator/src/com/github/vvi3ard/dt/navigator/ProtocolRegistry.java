package com.github.vvi3ard.dt.navigator;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class ProtocolRegistry {
    private static final String REG_KEY_ROOT = "HKCU\\Software\\Classes\\edt";
    private static final String REG_KEY_COMMAND = "HKCU\\Software\\Classes\\edt\\shell\\open\\command";
    private static final String SCRIPT_FILENAME = "open-url.vbs";

    private ProtocolRegistry() {}

    public static Path getScriptPath() {
        return NavigatorPlugin.getConfigDir().resolve(SCRIPT_FILENAME);
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    public static boolean isRegistered() {
        if (!isWindows()) {
            return false;
        }
        try {
            ProcessResult result = runProcess("reg", "query", REG_KEY_COMMAND, "/ve");
            if (result.exitCode == 0 && result.output.contains(SCRIPT_FILENAME)) {
                return Files.isRegularFile(getScriptPath());
            }
        } catch (Exception e) {
            NavigatorPlugin.logError("Error checking protocol registration", e);
        }
        return false;
    }

    public static boolean register(int port) {
        if (!isWindows()) {
            NavigatorPlugin.logInfo("Protocol registration is only supported on Windows");
            return false;
        }

        try {
            Path scriptFile = getScriptPath();
            Files.createDirectories(scriptFile.getParent());
            String scriptContent = generateScriptContent(port);
            Files.writeString(scriptFile, scriptContent, StandardCharsets.ISO_8859_1);

            String scriptAbs = scriptFile.toAbsolutePath().toString();
            String fullWscript = "C:\\Windows\\System32\\wscript.exe";
            if (!new File(fullWscript).isFile()) {
                fullWscript = "wscript.exe";
            }
            String commandValue = "\\\"" + fullWscript + "\\\" \\\"" + scriptAbs + "\\\" \\\"%1\\\"";

            ProcessResult res1 = runProcess("reg", "add", REG_KEY_ROOT, "/ve", "/d", "URL:1C:EDT Protocol", "/f");
            if (res1.exitCode != 0) {
                NavigatorPlugin.logError("Failed to set root reg key: " + res1.output, null);
                return false;
            }

            ProcessResult res2 = runProcess("reg", "add", REG_KEY_ROOT, "/v", "URL Protocol", "/d", "", "/f");
            if (res2.exitCode != 0) {
                NavigatorPlugin.logError("Failed to set URL Protocol reg value: " + res2.output, null);
                return false;
            }

            ProcessResult res3 = runProcess("cmd.exe", "/c", "reg", "add", REG_KEY_COMMAND, "/ve", "/d", "\"" + commandValue + "\"", "/f");
            if (res3.exitCode != 0) {
                NavigatorPlugin.logError("Failed to set command reg value: " + res3.output, null);
                return false;
            }

            NavigatorPlugin.logInfo("Registered edt:// protocol in Windows registry (HKCU) with script: " + scriptFile);
            return true;
        } catch (Exception e) {
            NavigatorPlugin.logError("Failed to register protocol", e);
            return false;
        }
    }

    public static boolean unregister() {
        if (!isWindows()) {
            return false;
        }

        try {
            ProcessResult res = runProcess("reg", "delete", REG_KEY_ROOT, "/f");
            NavigatorPlugin.logInfo("Unregistered edt:// protocol: " + res.output);
            return res.exitCode == 0;
        } catch (Exception e) {
            NavigatorPlugin.logError("Failed to unregister protocol", e);
            return false;
        }
    }

    public static String generateScriptContent(int port) {
        return "' 1C:EDT URL Navigator proxy script\r\n"
             + "On Error Resume Next\r\n"
             + "If WScript.Arguments.Count = 0 Then\r\n"
             + "    WScript.Quit\r\n"
             + "End If\r\n"
             + "\r\n"
             + "Dim url, http\r\n"
             + "url = WScript.Arguments(0)\r\n"
             + "\r\n"
             + "Set http = CreateObject(\"MSXML2.ServerXMLHTTP.6.0\")\r\n"
             + "If http Is Nothing Then\r\n"
             + "    Set http = CreateObject(\"MSXML2.ServerXMLHTTP\")\r\n"
             + "End If\r\n"
             + "\r\n"
             + "http.Open \"POST\", \"http://127.0.0.1:" + port + "/open\", False\r\n"
             + "http.setRequestHeader \"Content-Type\", \"text/plain; charset=utf-8\"\r\n"
             + "http.Send url\r\n";
    }

    private static ProcessResult runProcess(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "CP866"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, "Process timed out");
        }

        return new ProcessResult(process.exitValue(), sb.toString());
    }

    private static class ProcessResult {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}

package com.github.vvi3ard.dt.navigator.preferences;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.github.vvi3ard.dt.navigator.NavigationServer;
import com.github.vvi3ard.dt.navigator.NavigatorPlugin;
import com.github.vvi3ard.dt.navigator.ProtocolRegistry;

public class NavigatorPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    private Label lblProtocolStatus;
    private Text txtPort;
    private Label lblServerStatus;
    private Label lblScriptPath;

    public NavigatorPreferencePage() {
        setTitle("1C:EDT URL Navigator");
        setDescription("Настройка мгновенной навигации в модули 1C:EDT по внешним ссылкам вида edt://open?module=...");
    }

    @Override
    public void init(IWorkbench workbench) {
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(GridData.FILL_BOTH));

        createProtocolGroup(container);
        createScriptGroup(container);
        createServerGroup(container);
        createLlmPromptGroup(container);

        updateStatusDisplay();

        return container;
    }

    private void createProtocolGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Регистрация протокола edt:// в системе");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        lblProtocolStatus = new Label(group, SWT.NONE);
        lblProtocolStatus.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Composite btnBar = new Composite(group, SWT.NONE);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.spacing = 10;
        btnBar.setLayout(rl);

        Button btnRegister = new Button(btnBar, SWT.PUSH);
        btnRegister.setText("Зарегистрировать протокол edt://");
        btnRegister.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int port = getPortValue();
                boolean ok = ProtocolRegistry.register(port);
                updateStatusDisplay();
                if (ok) {
                    MessageDialog.openInformation(getShell(), "Регистрация протокола",
                            "Протокол edt:// успешно зарегистрирован в реестре Windows (HKCU)!\n" +
                            "Теперь клик по любой ссылке edt:// автоматически откроет модуль в EDT.");
                } else {
                    MessageDialog.openError(getShell(), "Ошибка регистрации",
                            "Не удалось зарегистрировать протокол edt:// в реестре Windows.\n" +
                            "Подробности смотрите в Журнале ошибок Eclipse (Error Log).");
                }
            }
        });

        Button btnUnregister = new Button(btnBar, SWT.PUSH);
        btnUnregister.setText("Отключить регистрацию");
        btnUnregister.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean ok = ProtocolRegistry.unregister();
                updateStatusDisplay();
                if (ok) {
                    MessageDialog.openInformation(getShell(), "Регистрация протокола",
                            "Регистрация протокола edt:// удалена из системы.");
                } else {
                    MessageDialog.openError(getShell(), "Ошибка",
                            "Не удалось удалить ветку реестра HKCU\\Software\\Classes\\edt.");
                }
            }
        });
    }

    private void createScriptGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Скрипт-прокси (VBScript)");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Label lblPathTitle = new Label(group, SWT.NONE);
        lblPathTitle.setText("Путь к скрипту:");

        lblScriptPath = new Label(group, SWT.NONE);
        lblScriptPath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        lblScriptPath.setText(ProtocolRegistry.getScriptPath().toString());

        Button btnOpenDir = new Button(group, SWT.PUSH);
        btnOpenDir.setText("Открыть папку со скриптом");
        GridData gd = new GridData();
        gd.horizontalSpan = 2;
        btnOpenDir.setLayoutData(gd);
        btnOpenDir.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Path dir = NavigatorPlugin.getConfigDir();
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(dir.toFile());
                    }
                } catch (Exception ex) {
                    MessageDialog.openError(getShell(), "Ошибка", "Не удалось открыть каталог: " + ex.getMessage());
                }
            }
        });
    }

    private void createServerGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Сетевой сервер навигации");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Label lblPort = new Label(group, SWT.NONE);
        lblPort.setText("Порт сервера (127.0.0.1):");

        txtPort = new Text(group, SWT.BORDER);
        txtPort.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        txtPort.setText(String.valueOf(NavigatorPreferences.getPort()));

        lblServerStatus = new Label(group, SWT.NONE);
        GridData gdStatus = new GridData(GridData.FILL_HORIZONTAL);
        gdStatus.horizontalSpan = 2;
        lblServerStatus.setLayoutData(gdStatus);

        Composite btnBar = new Composite(group, SWT.NONE);
        GridData gdBar = new GridData(GridData.FILL_HORIZONTAL);
        gdBar.horizontalSpan = 2;
        btnBar.setLayoutData(gdBar);
        btnBar.setLayout(new RowLayout(SWT.HORIZONTAL));

        Button btnTest = new Button(btnBar, SWT.PUSH);
        btnTest.setText("Проверить соединение с сервером");
        btnTest.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                testServerConnection();
            }
        });
    }

    private void updateStatusDisplay() {
        Display display = getShell().getDisplay();

        boolean registered = ProtocolRegistry.isRegistered();
        if (registered) {
            lblProtocolStatus.setText("● Протокол edt:// зарегистрирован в Windows (HKCU)");
            lblProtocolStatus.setForeground(display.getSystemColor(SWT.COLOR_DARK_GREEN));
        } else {
            lblProtocolStatus.setText("○ Протокол edt:// не зарегистрирован в системе");
            lblProtocolStatus.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
        }

        NavigationServer server = NavigatorPlugin.getDefault() != null ? NavigatorPlugin.getDefault().getServer() : null;
        if (server != null && server.isRunning()) {
            lblServerStatus.setText("Сервер активен на 127.0.0.1:" + server.getPort());
            lblServerStatus.setForeground(display.getSystemColor(SWT.COLOR_DARK_GREEN));
        } else {
            lblServerStatus.setText("Сервер остановлен");
            lblServerStatus.setForeground(display.getSystemColor(SWT.COLOR_RED));
        }

        lblScriptPath.setText(ProtocolRegistry.getScriptPath().toString());
    }

    private int getPortValue() {
        try {
            int p = Integer.parseInt(txtPort.getText().trim());
            if (p > 1024 && p <= 65535) {
                return p;
            }
        } catch (Exception ignored) {}
        return NavigatorPreferences.DEFAULT_PORT;
    }

    private void testServerConnection() {
        int port = getPortValue();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                MessageDialog.openInformation(getShell(), "Тест сервера",
                        "Соединение успешно установлено!\nОтвет сервера: " + response.body().trim());
            } else {
                MessageDialog.openWarning(getShell(), "Тест сервера",
                        "Сервер вернул код ошибки: " + response.statusCode());
            }
        } catch (Exception e) {
            MessageDialog.openError(getShell(), "Тест сервера",
                    "Не удалось подключиться к серверу на порту " + port + ": " + e.getMessage());
        }
    }

    @Override
    public boolean performOk() {
        int newPort = getPortValue();
        int oldPort = NavigatorPreferences.getPort();

        NavigatorPreferences.setPort(newPort);

        if (newPort != oldPort) {
            try {
                if (NavigatorPlugin.getDefault() != null) {
                    NavigatorPlugin.getDefault().restartServer(newPort);
                }
                if (ProtocolRegistry.isRegistered()) {
                    ProtocolRegistry.register(newPort);
                }
            } catch (IOException e) {
                MessageDialog.openError(getShell(), "Ошибка перезапуска сервера", e.getMessage());
                return false;
            }
        }

        updateStatusDisplay();
        return super.performOk();
    }

    @Override
    protected void performDefaults() {
        txtPort.setText(String.valueOf(NavigatorPreferences.DEFAULT_PORT));
        super.performDefaults();
    }

    private void createLlmPromptGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Инструкция для ИИ-ассистентов (LLM)");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Label lblDesc = new Label(group, SWT.WRAP);
        lblDesc.setText("Скопируйте промпт и отправьте его в чат вашего ИИ-ассистента (Claude Code, Codex, Cursor, Gemini),\n"
                + "чтобы он автоматически добавлял ссылки на открытие объектов в 1C:EDT.");
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.widthHint = 450;
        lblDesc.setLayoutData(gd);

        Button btnCopy = new Button(group, SWT.PUSH);
        btnCopy.setText("Скопировать промпт для LLM");
        btnCopy.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        btnCopy.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int port = getPortValue();
                String promptText = generateLlmPrompt(port);
                copyToClipboard(promptText);
                MessageDialog.openInformation(getShell(), "Промпт для ИИ-ассистента",
                        "Промпт успешно скопирован в буфер обмена!\n\n"
                        + "Вставьте его в чат вашего ИИ-ассистента (Claude, Codex, Cursor и др.).");
            }
        });
    }

    private void copyToClipboard(String text) {
        Display display = Display.getCurrent();
        if (display == null) {
            display = Display.getDefault();
        }
        org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(display);
        try {
            org.eclipse.swt.dnd.TextTransfer transfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
            clipboard.setContents(new Object[] { text }, new org.eclipse.swt.dnd.Transfer[] { transfer });
        } finally {
            clipboard.dispose();
        }
    }

    public static String generateLlmPrompt(int port) {
        return "Add the section below to your persistent instruction file so you follow it in every session.\r\n\r\n"
             + "1. Pick the file:\r\n"
             + "   - If I name a file or say \"global\", use that (global: `~/.codex/AGENTS.md`, `~/.claude/CLAUDE.md`, or your equivalent).\r\n"
             + "   - Otherwise, use the instruction file in the project root that your tool reads: `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.github/copilot-instructions.md`, or a file in `.cursor/rules/`. If none exists, create `AGENTS.md`. If you read `CLAUDE.md` and only `AGENTS.md` exists, put the section in `AGENTS.md` and add the line `@AGENTS.md` to `CLAUDE.md`.\r\n"
             + "2. If the file already has a block between `<!-- edt-url-navigator:begin -->` and `<!-- edt-url-navigator:end -->`, replace that block. Otherwise append it at the end. Change nothing else in the file.\r\n"
             + "3. Copy the section exactly: keep the Cyrillic as is and do not percent-encode it.\r\n"
             + "4. Reply with the file path and one example link built for a module from this project.\r\n\r\n"
             + "Section to add:\r\n\r\n"
             + "<!-- edt-url-navigator:begin -->\r\n"
             + "## 1C code links (EDT URL Navigator)\r\n\r\n"
             + "When you link a 1C module, method, line, or metadata object, keep your usual link as is and append an EDT link right after it: ` ([EDT](http://127.0.0.1:" + port + "/open?<params>))`.\r\n\r\n"
             + "- Raw UTF-8 Cyrillic in the URL, never percent-encode.\r\n"
             + "- Never use the `edt://` scheme: many clients do not make it clickable.\r\n"
             + "- Prefer `method=` when the method is known (survives edits); use `line=` for exact spots (errors, diagnostics).\r\n\r\n"
             + "Params:\r\n"
             + "- `module=` 1C module name: `ОбщийМодуль.X`, `Документ.X.МодульОбъекта`, `.МодульМенеджера`, `.МодульНабораЗаписей`, `Документ.X.Форма.F`, `МодульПриложения`, `МодульСеанса`\r\n"
             + "- `line=N` · `method=Name`\r\n"
             + "- `md=Type.Name` metadata object, e.g. `Справочник.X`, `Конфигурация`; `attr=Name` with `md` (attribute, dimension, resource, tabular section)\r\n"
             + "- `project=<EDT project>` only if the name exists in several projects (e.g. base config vs extension)\r\n\r\n"
             + "Examples (EDT part):\r\n"
             + "- `([EDT](http://127.0.0.1:" + port + "/open?module=ОбщийМодуль.ОбщегоНазначения&method=ЗначениеРеквизитаОбъекта))`\r\n"
             + "- `([EDT](http://127.0.0.1:" + port + "/open?module=Документ.ЗаказКлиента.МодульОбъекта&line=120))`\r\n"
             + "- `([EDT](http://127.0.0.1:" + port + "/open?md=Справочник.Номенклатура&attr=Артикул))`\r\n"
             + "<!-- edt-url-navigator:end -->\r\n";
    }
}

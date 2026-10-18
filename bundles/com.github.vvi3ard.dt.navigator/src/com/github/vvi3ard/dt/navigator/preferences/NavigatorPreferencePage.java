package com.github.vvi3ard.dt.navigator.preferences;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.github.vvi3ard.dt.navigator.NavigationServer;
import com.github.vvi3ard.dt.navigator.NavigatorPlugin;

public class NavigatorPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    private Text txtPort;
    private Label lblServerStatus;

    public NavigatorPreferencePage() {
        setTitle("1C:EDT URL Navigator");
        setDescription("Локальный HTTP-шлюз навигации для Claude Code и плагина edt-links.");
    }

    @Override
    public void init(IWorkbench workbench) {
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(GridData.FILL_BOTH));

        createServerGroup(container);
        createInfoGroup(container);

        updateStatusDisplay();

        return container;
    }

    private void createServerGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Локальный HTTP-сервер");
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

    private void createInfoGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Справка и установка");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Link link = new Link(group, SWT.NONE);
        link.setText("Плагин работает в связке с Claude Code через мод <b>edt-links</b>.\n" +
                     "Инструкция по установке и документация доступны в репозитории: " +
                     "<a href=\"https://github.com/VVi3ard/edt-url-navigator\">https://github.com/VVi3ard/edt-url-navigator</a>");
        link.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                org.eclipse.swt.program.Program.launch(e.text);
            }
        });
    }

    private void updateStatusDisplay() {
        if (getShell() == null || getShell().isDisposed()) {
            return;
        }
        Display display = getShell().getDisplay();

        NavigationServer server = NavigatorPlugin.getDefault() != null ? NavigatorPlugin.getDefault().getServer() : null;
        if (server != null && server.isRunning()) {
            lblServerStatus.setText("● Сервер активен на 127.0.0.1:" + server.getPort());
            lblServerStatus.setForeground(display.getSystemColor(SWT.COLOR_DARK_GREEN));
        } else {
            lblServerStatus.setText("○ Сервер остановлен");
            lblServerStatus.setForeground(display.getSystemColor(SWT.COLOR_RED));
        }
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

        if (newPort != oldPort) {
            NavigatorPreferences.setPort(newPort);
            try {
                if (NavigatorPlugin.getDefault() != null) {
                    NavigatorPlugin.getDefault().restartServer(newPort);
                }
            } catch (Exception e) {
                MessageDialog.openError(getShell(), "Ошибка перезапуска сервера",
                        "Не удалось перезапустить сервер на порту " + newPort + ": " + e.getMessage());
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
}

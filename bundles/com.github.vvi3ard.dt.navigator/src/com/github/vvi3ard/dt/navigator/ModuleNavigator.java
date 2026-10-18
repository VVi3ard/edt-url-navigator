package com.github.vvi3ard.dt.navigator;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public final class ModuleNavigator {

    private ModuleNavigator() {}

    public static class NavigationTarget {
        public String module;
        public String file;
        public String path;
        public String project;
        public String md;
        public String method;
        public String attr;
        public String search;
        public int line = 1;
        public String rawUrl;

        @Override
        public String toString() {
            return "NavigationTarget[module=" + module + ", md=" + md + ", method=" + method + ", attr=" + attr + ", file=" + file + ", path=" + path + ", project=" + project + ", line=" + line + "]";
        }
    }

    public static boolean open(NavigationTarget target) {
        if (target == null) {
            return false;
        }

        File resolvedFile = resolveFile(target);
        if (resolvedFile == null || !resolvedFile.isFile()) {
            NavigatorPlugin.logError("Could not resolve file for target: " + target, null);
            return false;
        }

        return openResolved(resolvedFile, target);
    }

    public static boolean openResolved(File resolvedFile, NavigationTarget target) {
        if (resolvedFile == null || !resolvedFile.isFile()) {
            return false;
        }

        NavigatorPlugin.logInfo("Processing navigation request: " + target);

        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
            if (windows.length > 0) {
                window = windows[0];
            }
        }

        if (window == null) {
            NavigatorPlugin.logError("No active workbench window available", null);
            return false;
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            NavigatorPlugin.logError("No active workbench page available", null);
            return false;
        }

        try {
            IEditorPart editor = null;
            IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
            IFile[] iFiles = root.findFilesForLocationURI(resolvedFile.toURI());
            IFile iFile = (iFiles != null && iFiles.length > 0 && iFiles[0].exists()) ? iFiles[0] : null;

            if (iFile == null) {
                // Robust fallback search across workspace projects
                String resolvedPath = resolvedFile.getAbsolutePath().replace('\\', '/');
                for (IProject project : root.getProjects()) {
                    if (!project.isAccessible()) continue;
                    IPath projectLoc = project.getLocation();
                    if (projectLoc == null) continue;
                    String projPath = projectLoc.toFile().getAbsolutePath().replace('\\', '/');
                    if (resolvedPath.toLowerCase(Locale.ROOT).startsWith(projPath.toLowerCase(Locale.ROOT))) {
                        String rel = resolvedPath.substring(projPath.length());
                        if (rel.startsWith("/")) rel = rel.substring(1);
                        IFile candidate = project.getFile(rel);
                        if (candidate.exists()) {
                            iFile = candidate;
                            break;
                        }
                    }
                }
            }

            boolean isMdo = resolvedFile.getName().toLowerCase(Locale.ROOT).endsWith(".mdo");

            if (iFile != null && iFile.exists()) {
                editor = openWithEdtHelper(page, iFile, target);
            } else {
                IFileStore fileStore = EFS.getStore(resolvedFile.toURI());
                if (isMdo) {
                    editor = openMetadataEditorOnFileStore(page, fileStore, target);
                } else {
                    editor = IDE.openEditorOnFileStore(page, fileStore);
                }
            }

            if (editor != null) {
                positionInEditor(editor, target);
            }

            bringToFront(window.getShell());
            return true;
        } catch (Exception e) {
            NavigatorPlugin.logError("Error opening editor for: " + resolvedFile, e);
            return false;
        }
    }

    private static IEditorPart openWithEdtHelper(IWorkbenchPage page, IFile iFile, NavigationTarget target) {
        boolean isMdo = iFile.getName().toLowerCase(Locale.ROOT).endsWith(".mdo");

        // Try EDT OpenHelper first (opens modules and metadata objects as 1C model with full visual editor)
        try {
            org.osgi.framework.Bundle bundle = org.eclipse.core.runtime.Platform.getBundle("com._1c.g5.v8.dt.ui");
            if (bundle != null) {
                Class<?> helperClass = bundle.loadClass("com._1c.g5.v8.dt.ui.util.OpenHelper");
                java.lang.reflect.Constructor<?> ctor = helperClass.getConstructor(IWorkbenchPage.class);
                Object helper = ctor.newInstance(page);
                java.lang.reflect.Method openMethod = helperClass.getMethod("openEditor", IFile.class, org.eclipse.jface.viewers.ISelection.class);
                Object result = openMethod.invoke(helper, iFile, null);
                if (result instanceof IEditorPart) {
                    NavigatorPlugin.logInfo("Successfully opened editor with EDT OpenHelper for " + iFile.getFullPath());
                    return (IEditorPart) result;
                }
            }
        } catch (Throwable t) {
            NavigatorPlugin.logInfo("OpenHelper reflection failed or not available, falling back: " + t.getMessage());
        }

        // Fallback for .mdo if OpenHelper failed
        if (isMdo) {
            return openMetadataEditor(page, iFile, target);
        }

        // Standard fallback
        try {
            return IDE.openEditor(page, iFile, true);
        } catch (Exception e) {
            NavigatorPlugin.logError("IDE.openEditor fallback failed for " + iFile, e);
            return null;
        }
    }

    private static IEditorPart openMetadataEditor(IWorkbenchPage page, IFile iFile, NavigationTarget target) {
        // Try EDT specific editor for container
        String editorId = getEdtMetadataEditorId(target);
        if (editorId != null) {
            try {
                return page.openEditor(new org.eclipse.ui.part.FileEditorInput(iFile), editorId);
            } catch (Throwable t) {
                NavigatorPlugin.logInfo("Could not open EDT editor " + editorId + ", falling back to DefaultTextEditor: " + t.getMessage());
            }
        }

        // Safe fallback: built-in DefaultTextEditor (NEVER external OS Explorer!)
        try {
            return IDE.openEditor(page, iFile, "org.eclipse.ui.DefaultTextEditor", true);
        } catch (Exception e) {
            NavigatorPlugin.logError("Failed to open DefaultTextEditor for " + iFile, e);
            return null;
        }
    }

    private static IEditorPart openMetadataEditorOnFileStore(IWorkbenchPage page, IFileStore fileStore, NavigationTarget target) {
        try {
            return IDE.openEditorOnFileStore(page, fileStore);
        } catch (Exception e) {
            NavigatorPlugin.logError("Failed to open editor on file store: " + fileStore, e);
            return null;
        }
    }

    private static String getEdtMetadataEditorId(NavigationTarget target) {
        if (target == null || target.md == null || target.md.isBlank()) {
            return null;
        }
        String lower = target.md.toLowerCase(Locale.ROOT);
        if (lower.startsWith("документ.") || lower.startsWith("document.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.document";
        }
        if (lower.startsWith("справочник.") || lower.startsWith("catalog.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.catalog";
        }
        if (lower.startsWith("регистрсведений.") || lower.startsWith("informationregister.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.informationRegister";
        }
        if (lower.startsWith("регистрнакопления.") || lower.startsWith("accumulationregister.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.accumulationRegister";
        }
        if (lower.equals("конфигурация") || lower.equals("configuration")) {
            return "com._1c.g5.v8.dt.md.ui.editor.configuration";
        }
        if (lower.startsWith("отчет.") || lower.startsWith("report.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.report";
        }
        if (lower.startsWith("обработка.") || lower.startsWith("dataprocessor.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.dataProcessor";
        }
        if (lower.startsWith("планвидовхарактеристик.") || lower.startsWith("chartofcharacteristictypes.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.chartofcharacteristictypes";
        }
        if (lower.startsWith("плансчетов.") || lower.startsWith("chartofaccounts.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.chartOfAccounts";
        }
        if (lower.startsWith("регистрбухгалтерии.") || lower.startsWith("accountingregister.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.accountingRegister";
        }
        if (lower.startsWith("регистррасчета.") || lower.startsWith("calculationregister.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.calculationRegister";
        }
        if (lower.startsWith("общиймодуль.") || lower.startsWith("commonmodule.")) {
            return "com._1c.g5.v8.dt.md.ui.editor.commonmodule";
        }
        return null;
    }

    public static File resolveFile(NavigationTarget target) {
        // 1. Direct path if specified
        if (target.path != null && !target.path.isBlank()) {
            File directFile = new File(target.path.trim());
            if (directFile.isFile()) {
                return directFile;
            }
        }

        // 2. Candidate relative paths
        List<String> candidateRelPaths = new ArrayList<>();

        if (target.file != null && !target.file.isBlank()) {
            candidateRelPaths.add(target.file.trim().replace('\\', '/'));
        }

        if (target.module != null && !target.module.isBlank()) {
            candidateRelPaths.addAll(translateModuleToPaths(target.module.trim()));
        }

        if (target.md != null && !target.md.isBlank()) {
            candidateRelPaths.addAll(translateMetadataToPaths(target.md.trim()));
        }

        // 3. Search across workspace projects
        List<IProject> candidateProjects = getPrioritizedProjects(target.project);
        for (IProject project : candidateProjects) {
            IPath projectLoc = project.getLocation();
            if (projectLoc == null) {
                continue;
            }
            File baseDir = projectLoc.toFile();

            for (String relPath : candidateRelPaths) {
                // Check in src/
                File candidate1 = new File(baseDir, "src/" + relPath);
                if (candidate1.isFile()) {
                    return candidate1;
                }
                // Check directly in project base
                File candidate2 = new File(baseDir, relPath);
                if (candidate2.isFile()) {
                    return candidate2;
                }
            }
        }

        // 4. Fallback search by base name in src/
        String searchName = extractBaseSearchName(target);
        if (searchName != null && !searchName.isBlank()) {
            for (IProject project : candidateProjects) {
                IPath projectLoc = project.getLocation();
                if (projectLoc == null) continue;
                File srcDir = new File(projectLoc.toFile(), "src");
                if (srcDir.isDirectory()) {
                    File found = findModuleByName(srcDir, searchName.toLowerCase(Locale.ROOT));
                    if (found != null) {
                        return found;
                    }
                }
            }
        }

        return null;
    }

    private static List<String> translateModuleToPaths(String module) {
        List<String> paths = new ArrayList<>();

        if (module.endsWith(".bsl") || module.contains("/") || module.contains("\\")) {
            paths.add(module.replace('\\', '/'));
            return paths;
        }

        String[] parts = module.split("\\.");
        if (parts.length == 0) {
            return paths;
        }

        String type = parts[0].trim();
        String typeLower = type.toLowerCase(Locale.ROOT);

        if (parts.length == 1) {
            // Standalone module or config module
            if (typeLower.equals("модульприложения") || typeLower.equals("managedapplicationmodule")) {
                paths.add("Configuration/ManagedApplicationModule.bsl");
            } else if (typeLower.equals("модульобычногоприложения") || typeLower.equals("ordinaryapplicationmodule")) {
                paths.add("Configuration/OrdinaryApplicationModule.bsl");
            } else if (typeLower.equals("модульсеанса") || typeLower.equals("sessionmodule")) {
                paths.add("Configuration/SessionModule.bsl");
            } else if (typeLower.equals("модульвнешнегосоединения") || typeLower.equals("externalconnectionmodule")) {
                paths.add("Configuration/ExternalConnectionModule.bsl");
            } else {
                paths.add("CommonModules/" + type + "/Module.bsl");
            }
            return paths;
        }

        String name = parts[1].trim();

        if (isCommonModule(typeLower)) {
            paths.add("CommonModules/" + name + "/Module.bsl");
            return paths;
        }

        String container = mapContainerName(typeLower);
        if (container != null) {
            if (parts.length == 2) {
                // Default to ObjectModule or RecordSetModule or Module
                paths.add(container + "/" + name + "/ObjectModule.bsl");
                paths.add(container + "/" + name + "/ManagerModule.bsl");
                paths.add(container + "/" + name + "/RecordSetModule.bsl");
                paths.add(container + "/" + name + "/Module.bsl");
            } else {
                String sub = parts[2].trim();
                String subLower = sub.toLowerCase(Locale.ROOT);

                if (subLower.equals("модульобъекта") || subLower.equals("objectmodule")) {
                    paths.add(container + "/" + name + "/ObjectModule.bsl");
                } else if (subLower.equals("модульменеджера") || subLower.equals("managermodule")) {
                    paths.add(container + "/" + name + "/ManagerModule.bsl");
                } else if (subLower.equals("модульнаборазаписей") || subLower.equals("recordsetmodule")) {
                    paths.add(container + "/" + name + "/RecordSetModule.bsl");
                } else if (subLower.equals("форма") || subLower.equals("form") || subLower.equals("forms")) {
                    if (parts.length > 3) {
                        String formName = parts[3].trim();
                        paths.add(container + "/" + name + "/Forms/" + formName + "/Module.bsl");
                    }
                } else {
                    paths.add(container + "/" + name + "/" + sub + ".bsl");
                    paths.add(container + "/" + name + "/Module.bsl");
                }
            }
            return paths;
        }

        // Generic fallback
        paths.add("CommonModules/" + name + "/Module.bsl");
        paths.add(type + "/" + name + "/Module.bsl");
        paths.add(type + "/" + name + "/ObjectModule.bsl");
        return paths;
    }

    public static List<String> translateMetadataToPaths(String md) {
        List<String> paths = new ArrayList<>();
        if (md.endsWith(".mdo") || md.contains("/") || md.contains("\\")) {
            paths.add(md.replace('\\', '/'));
            return paths;
        }

        String[] parts = md.split("\\.");
        if (parts.length == 0) {
            return paths;
        }

        String type = parts[0].trim();
        String typeLower = type.toLowerCase(Locale.ROOT);

        if (parts.length == 1) {
            if (typeLower.equals("конфигурация") || typeLower.equals("configuration")) {
                paths.add("Configuration/Configuration.mdo");
                paths.add("Configuration.mdo");
            } else {
                paths.add("CommonModules/" + type + "/" + type + ".mdo");
                paths.add("CommonModules/" + type + ".mdo");
            }
            return paths;
        }

        String name = parts[1].trim();

        if (isCommonModule(typeLower)) {
            paths.add("CommonModules/" + name + "/" + name + ".mdo");
            paths.add("CommonModules/" + name + ".mdo");
            return paths;
        }

        String container = mapContainerName(typeLower);
        if (container != null) {
            paths.add(container + "/" + name + "/" + name + ".mdo");
            paths.add(container + "/" + name + ".mdo");
            return paths;
        }

        paths.add(type + "/" + name + "/" + name + ".mdo");
        paths.add(type + "/" + name + ".mdo");
        return paths;
    }

    private static boolean isCommonModule(String typeLower) {
        return typeLower.equals("общиймодуль") || typeLower.equals("общиемодули")
                || typeLower.equals("commonmodule") || typeLower.equals("commonmodules");
    }

    private static String mapContainerName(String typeLower) {
        switch (typeLower) {
            case "справочник":
            case "справочники":
            case "catalog":
            case "catalogs":
                return "Catalogs";
            case "документ":
            case "документы":
            case "document":
            case "documents":
                return "Documents";
            case "регистрсведений":
            case "регистрысведений":
            case "informationregister":
            case "informationregisters":
                return "InformationRegisters";
            case "регистрнакопления":
            case "регистрынакопления":
            case "accumulationregister":
            case "accumulationregisters":
                return "AccumulationRegisters";
            case "регистрбухгалтерии":
            case "регистрыбухгалтерии":
            case "accountingregister":
            case "accountingregisters":
                return "AccountingRegisters";
            case "регистррасчета":
            case "регистрырасчета":
            case "calculationregister":
            case "calculationregisters":
                return "CalculationRegisters";
            case "планвидовхарактеристик":
            case "планывидовхарактеристик":
            case "chartofcharacteristictypes":
            case "chartsofcharacteristictypes":
                return "ChartsOfCharacteristicTypes";
            case "плансчетов":
            case "планысчетов":
            case "chartofaccounts":
            case "chartsofaccounts":
                return "ChartsOfAccounts";
            case "планобмена":
            case "планыобмена":
            case "exchangeplan":
            case "exchangeplans":
                return "ExchangePlans";
            case "обработка":
            case "обработки":
            case "dataprocessor":
            case "dataprocessors":
                return "DataProcessors";
            case "отчет":
            case "отчеты":
            case "report":
            case "reports":
                return "Reports";
            case "общаяформа":
            case "общиеформы":
            case "commonform":
            case "commonforms":
                return "CommonForms";
            case "бизнеспроцесс":
            case "бизнеспроцессы":
            case "businessprocess":
            case "businessprocesses":
                return "BusinessProcesses";
            case "задача":
            case "задачи":
            case "task":
            case "tasks":
                return "Tasks";
            case "планвидоврасчета":
            case "планывидоврасчета":
            case "chartofcalculationtypes":
            case "chartsofcalculationtypes":
                return "ChartsOfCalculationTypes";
            case "константа":
            case "константы":
            case "constant":
            case "constants":
                return "Constants";
            case "webservice":
            case "webservices":
            case "вебсервис":
            case "вебсервисы":
                return "WebServices";
            case "httpservice":
            case "httpservices":
            case "httpсервис":
            case "httpсервисы":
                return "HTTPServices";
            default:
                return null;
        }
    }

    private static String extractBaseSearchName(NavigationTarget target) {
        if (target.module != null && !target.module.isBlank()) {
            String[] parts = target.module.split("\\.");
            if (parts.length > 1) {
                return parts[1].trim();
            }
            return parts[0].trim();
        }
        if (target.md != null && !target.md.isBlank()) {
            String[] parts = target.md.split("\\.");
            if (parts.length > 1) {
                return parts[1].trim();
            }
            return parts[0].trim();
        }
        if (target.file != null && !target.file.isBlank()) {
            String path = target.file.replace('\\', '/');
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                int prevSlash = path.lastIndexOf('/', lastSlash - 1);
                if (prevSlash >= 0) {
                    return path.substring(prevSlash + 1, lastSlash);
                }
            }
        }
        return null;
    }

    private static File findModuleByName(File dir, String nameLower) {
        File[] children = dir.listFiles();
        if (children == null) return null;

        for (File child : children) {
            if (child.isDirectory()) {
                if (child.getName().toLowerCase(Locale.ROOT).equals(nameLower)) {
                    File mod = new File(child, "Module.bsl");
                    if (mod.isFile()) return mod;
                    File objMod = new File(child, "ObjectModule.bsl");
                    if (objMod.isFile()) return objMod;
                    File mgrMod = new File(child, "ManagerModule.bsl");
                    if (mgrMod.isFile()) return mgrMod;
                    File rsMod = new File(child, "RecordSetModule.bsl");
                    if (rsMod.isFile()) return rsMod;
                    File mdo = new File(child, child.getName() + ".mdo");
                    if (mdo.isFile()) return mdo;
                }
                // Recurse into major container dirs (CommonModules, Catalogs, Documents, etc.)
                if (isPotentialMetadataContainer(child.getName())) {
                    File found = findModuleByName(child, nameLower);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static boolean isPotentialMetadataContainer(String dirName) {
        String lower = dirName.toLowerCase(Locale.ROOT);
        return lower.equals("commonmodules") || lower.equals("catalogs") || lower.equals("documents")
                || lower.equals("informationregisters") || lower.equals("accumulationregisters")
                || lower.equals("reports") || lower.equals("dataprocessors")
                || lower.equals("commonforms");
    }

    private static List<IProject> getPrioritizedProjects(String projectFilter) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] all = root.getProjects();
        List<IProject> result = new ArrayList<>();

        for (IProject p : all) {
            if (!p.isAccessible() || p.getLocation() == null) {
                continue;
            }
            if (projectFilter != null && !projectFilter.isBlank()) {
                if (p.getName().equalsIgnoreCase(projectFilter.trim())) {
                    result.add(p);
                }
            } else {
                result.add(p);
            }
        }

        result.sort((p1, p2) -> {
            boolean isConfig1 = isMainConfigProject(p1);
            boolean isConfig2 = isMainConfigProject(p2);
            if (isConfig1 != isConfig2) {
                return isConfig1 ? -1 : 1;
            }
            boolean hasSrc1 = hasSrcDirectory(p1);
            boolean hasSrc2 = hasSrcDirectory(p2);
            if (hasSrc1 != hasSrc2) {
                return hasSrc1 ? -1 : 1;
            }
            return p1.getName().compareToIgnoreCase(p2.getName());
        });

        return result;
    }

    private static boolean isMainConfigProject(IProject p) {
        IPath loc = p.getLocation();
        if (loc == null) return false;
        File base = loc.toFile();
        return new File(base, "src/Configuration/Configuration.mdo").isFile()
            || new File(base, "src/Configuration.mdo").isFile();
    }

    private static boolean hasSrcDirectory(IProject p) {
        IPath loc = p.getLocation();
        if (loc == null) return false;
        return new File(loc.toFile(), "src").isDirectory();
    }

    private static void positionInEditor(IEditorPart editor, NavigationTarget target) {
        if (!(editor instanceof ITextEditor)) {
            return;
        }
        ITextEditor textEditor = (ITextEditor) editor;
        IDocumentProvider provider = textEditor.getDocumentProvider();
        if (provider == null) {
            return;
        }

        IDocument document = provider.getDocument(textEditor.getEditorInput());
        if (document == null) {
            return;
        }

        String text = document.get();

        // 1. Method search
        if (target.method != null && !target.method.isBlank()) {
            String methodName = target.method.trim();
            Pattern p = Pattern.compile("^[ \\t]*(?:Процедура|Функция|Procedure|Function)[ \\t]+" + Pattern.quote(methodName) + "[ \\t]*\\(", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                textEditor.selectAndReveal(m.start(), m.end() - m.start());
                return;
            }
            int idx = indexOfIgnoreCase(text, methodName);
            if (idx >= 0) {
                textEditor.selectAndReveal(idx, methodName.length());
                return;
            }
        }

        // 2. Attribute search
        if (target.attr != null && !target.attr.isBlank()) {
            String attrName = target.attr.trim();
            Pattern p = Pattern.compile("<name>[ \\t]*" + Pattern.quote(attrName) + "[ \\t]*</name>", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                textEditor.selectAndReveal(m.start(), m.end() - m.start());
                return;
            }
            int idx = indexOfIgnoreCase(text, attrName);
            if (idx >= 0) {
                textEditor.selectAndReveal(idx, attrName.length());
                return;
            }
        }

        // 3. Search query
        if (target.search != null && !target.search.isBlank()) {
            String searchStr = target.search.trim();
            int idx = indexOfIgnoreCase(text, searchStr);
            if (idx >= 0) {
                textEditor.selectAndReveal(idx, searchStr.length());
                return;
            }
        }

        // 4. Fallback to line
        if (target.line > 0) {
            goToLine(editor, target.line);
        }
    }

    private static int indexOfIgnoreCase(String source, String target) {
        if (source == null || target == null || target.isEmpty()) {
            return -1;
        }
        return source.toLowerCase(Locale.ROOT).indexOf(target.toLowerCase(Locale.ROOT));
    }

    private static void goToLine(IEditorPart editor, int line) {
        if (!(editor instanceof ITextEditor)) {
            return;
        }
        ITextEditor textEditor = (ITextEditor) editor;
        IDocumentProvider provider = textEditor.getDocumentProvider();
        if (provider == null) {
            return;
        }

        IDocument document = provider.getDocument(textEditor.getEditorInput());
        if (document == null) {
            return;
        }

        int targetLineIndex = Math.max(0, Math.min(line - 1, document.getNumberOfLines() - 1));
        try {
            int offset = document.getLineOffset(targetLineIndex);
            textEditor.selectAndReveal(offset, 0);
        } catch (BadLocationException e) {
            NavigatorPlugin.logError("Failed to position at line " + line, e);
        }
    }

    public static void bringToFront(Shell shell) {
        if (shell == null || shell.isDisposed()) {
            return;
        }

        try {
            if (shell.getMinimized()) {
                shell.setMinimized(false);
            }
            shell.forceActive();
            shell.setActive();
            shell.setFocus();

            // Try Win32 native foreground window via reflection
            try {
                Class<?> osClass = Class.forName("org.eclipse.swt.internal.win32.OS");
                java.lang.reflect.Field handleField = Shell.class.getField("handle");
                Object hwnd = handleField.get(shell);
                if (hwnd instanceof Number) {
                    long h = ((Number) hwnd).longValue();
                    java.lang.reflect.Method setFg = osClass.getMethod("SetForegroundWindow", long.class);
                    setFg.invoke(null, h);
                    java.lang.reflect.Method bringTop = osClass.getMethod("BringWindowToTop", long.class);
                    bringTop.invoke(null, h);
                }
            } catch (Throwable ignored) {
                // Ignore if not on Windows or internal OS class not accessible
            }
        } catch (Exception e) {
            NavigatorPlugin.logError("Failed to bring window to front", e);
        }
    }
}

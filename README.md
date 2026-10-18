# 1C:EDT URL Navigator

Плагин для **1C:Enterprise Development Tools (1C:EDT)**, обеспечивающий мгновенный переход к модулям 1С, строкам кода, методам, объектам метаданных и реквизитам по URL-ссылкам схемы `edt://open?...` (или HTTP fallback `http://127.0.0.1:18321/open?...`) прямо из чата LLM (Gemini, Claude, ChatGPT), браузера, документации или трекеров задач.

---

## 🚀 Установка и обновление в 1C:EDT

### Способ 1: Онлайн через Update Site (Рекомендуемый)

1. В 1C:EDT откройте главное меню: **Справка** (*Help*) → **Установить новое ПО...** (*Install New Software...*).
2. Нажмите кнопку **Добавить...** (*Add...*).
3. Заполните поля:
   - **Имя**: `1C:EDT URL Navigator`
   - **Расположение**: `https://vvi3ard.github.io/edt-url-navigator/`
4. Нажмите **Добавить** (*Add*).
5. В списке появится группа `1C:EDT URL Navigator` — отметьте флажком **1C:EDT URL Navigator Feature**.
6. Нажмите **Далее** (*Next*), примите условия лицензии и нажмите **Готово** (*Finish*).
7. После завершения установки перезапустите 1C:EDT по запросу.

> [!TIP]
> При выходе новых версий плагин будет автоматически обновляться через стандартный диалог 1C:EDT: **Справка → Проверить наличие обновлений**.

---

### Способ 2: Офлайн из релизного архива (.zip)

1. Скачайте архив репозитория `com.github.vvi3ard.dt.navigator.repository-<версия>.zip` со страницы [GitHub Releases](https://github.com/VVi3ard/edt-url-navigator/releases).
2. В 1C:EDT откройте **Справка** → **Установить новое ПО...** → **Добавить...** → **Архив...** (*Archive...*).
3. Выберите скачанный `.zip` файл, выберите `1C:EDT URL Navigator Feature` и завершите установку.

---

## ⚙️ Первоначальная настройка

1. После установки откройте настройки EDT: **Окно** (*Window*) → **Параметры** (*Preferences*) → категория **1C:EDT URL Navigator**.
2. В блоке «Регистрация схемы edt:// в Windows»:
   - Нажмите кнопку **Зарегистрировать протокол edt://**.
   - Регистрация выполняется в ветку текущего пользователя `HKCU\Software\Classes\edt` (права администратора **не требуются**).
   - Индикатор статуса сменится на зеленый: `● Протокол edt:// зарегистрирован`.
3. Нажмите кнопку **📋 Скопировать инструкцию для LLM** — в буфер обмена скопируется готовый системный промпт для добавления в пользовательские инструкции ИИ-ассистента.

---

## 🔗 Поддерживаемые форматы ссылок

Спецификация формирования URL поддерживает два протокола:
- **Основной протокол**: `edt://open?...` (открывает напрямую через Windows URI handler).
- **HTTP Fallback**: `http://127.0.0.1:18321/open?...` (для сред, блокирующих кастомные URI-схемы).

### Примеры:

1. **Переход к модулю и строке:**
   ```markdown
   [АванкорСервер (строка 42)](edt://open?module=ОбщийМодуль.АванкорСервер&line=42)
   ```
2. **Переход к методу/процедуре в модуле:**
   ```markdown
   [Печать](edt://open?module=Справочник.Организации.МодульМенеджера&method=Печать)
   ```
3. **Открытие объекта метаданных и позиционирование на реквизит:**
   ```markdown
   [ВидыКонтактнойИнформации.ВводитьНомерПоМаске](edt://open?md=Справочник.ВидыКонтактнойИнформации&attr=ВводитьНомерПоМаске)
   ```
4. **Поиск по имени файла или относительному пути:**
   ```markdown
   [ЗаявкаНаОперацию](edt://open?file=Documents/ЗаявкаНаОперацию/Forms/ФормаДокумента/Module.bsl&line=100)
   ```

Подробные правила формирования ссылок для LLM смотрите в руководстве [docs/LLM-LINKS-GUIDELINE.md](docs/LLM-LINKS-GUIDELINE.md).

---

## 🧩 Мод для Claude Code (`edt-links`)

Для пользователей **Claude Code** (терминал и Claude Desktop) в репозитории опубликован плагин-мод `edt-links`. По клику на ссылку `([EDT](http://127.0.0.1:18321/open?...))` он отправляет запрос в EDT напрямую через `curl` **без открытия вкладки браузера**.

### Установка мода:

- **В интерактивном режиме Claude Code:**
  ```text
  /plugin install edt-links --marketplace VVi3ard/edt-url-navigator
  ```
- **Из командной строки:**
  ```bash
  claude plugin install edt-links --marketplace VVi3ard/edt-url-navigator
  ```
- **В приложении Claude Desktop:**
  Нажмите **+** рядом с полем ввода → **Plugins** → **Add marketplace** → укажите `VVi3ard/edt-url-navigator` → выберите **Add plugin**.

### 📋 Готовые инструкции для настройки ассистентов:
- [SETUP-CLAUDE-CODE.md](SETUP-CLAUDE-CODE.md) — отправьте промпт в Claude Code: он сам установит мод `edt-links` и добавит правила оформления ссылок в ваш `CLAUDE.md`.
- [SETUP-OTHER-AGENTS.md](SETUP-OTHER-AGENTS.md) — настройка для остальных агентов (Codex, Cursor, GitHub Copilot, Gemini CLI). Ссылки открываются через локальный HTTP-шлюз, а открытая вкладка браузера автоматически закрывается страницей плагина 1.1.0 (`window.close()`).
- [docs/LLM-LINKS-GUIDELINE.md](docs/LLM-LINKS-GUIDELINE.md) — полная спецификация синтаксиса ссылок (модули, методы, строки, метаданные и реквизиты).

---

## 🛠️ Сборка из исходников

Проект построен на базе **Eclipse Tycho / Maven**:

### Требования:
- JDK 17 или JDK 21 (x64)
- Apache Maven 3.9+
- Доступ к целевой платформе 1C:EDT 2026.1 / 2026.2

### Сборка:
```bash
mvn clean verify -Dtycho.localArtifacts=ignore
```

Готовые артефакты формируются в каталогах:
- `bundles/com.github.vvi3ard.dt.navigator/target/com.github.vvi3ard.dt.navigator-*.jar`
- `repositories/com.github.vvi3ard.dt.navigator.repository/target/com.github.vvi3ard.dt.navigator.repository-*.zip`
- `repositories/com.github.vvi3ard.dt.navigator.repository/target/repository/` (готовый p2 update site)

---

## 📄 Лицензия

MIT License. См. [LICENSE](LICENSE).

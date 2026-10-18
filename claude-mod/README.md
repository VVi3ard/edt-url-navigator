# edt-links — мод Claude Code для 1C:EDT URL Navigator

В ответах Claude ссылки `(EDT)` на шлюз плагина (`http://127.0.0.1:18321/open?...`) по клику открывают объект 1С прямо в EDT, без вкладки браузера. Остальные ссылки работают как обычно.

Нужно:
- 1C:EDT с установленным плагином EDT URL Navigator;
- LLM, которая пишет ссылки `(EDT)`: инструкция `SETUP-CLAUDE-CODE.md` ставит мод и прописывает правила ссылок за один раз.

Без мода ссылки тоже работают, но каждый клик открывает вкладку браузера.

## Установка

В терминале (`claude`):

```
/plugin install edt-links --marketplace VVi3ard/edt-url-navigator
```

Из командной строки:

```
claude plugin install edt-links --marketplace VVi3ard/edt-url-navigator
```

В приложении Claude Desktop: **+** рядом с полем ввода → **Plugins** → **Add marketplace** → `VVi3ard/edt-url-navigator` → **Add plugin**.

## Проверено

Claude Code 2.1.299: приложение Claude Desktop (вкладка Code).

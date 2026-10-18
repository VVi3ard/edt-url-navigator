# Setup for other AI assistants

For Codex, Cursor, GitHub Copilot, Gemini CLI and other assistants (for Claude Code use `SETUP-CLAUDE-CODE.md`). Copy everything below the line into the assistant's chat. It will add the link rules to its instruction file, and every 1C code reference will then include an "EDT" link.

Clicking an EDT link opens a browser tab that calls the plugin; the object opens in 1C:EDT and the tab closes itself (plugin 1.1.0 or later; older versions leave the tab open). Requires 1C:EDT running with the EDT URL Navigator plugin. If you changed the plugin's port in its preferences, replace `18321` before sending.

---

Add the section below to your persistent instruction file so you follow it in every session.

1. Pick the file:
   - If I name a file or say "global", use that (global: `~/.codex/AGENTS.md` or your tool's equivalent).
   - Otherwise, use the instruction file in the project root that your tool reads: `AGENTS.md`, `GEMINI.md`, `.github/copilot-instructions.md`, or a file in `.cursor/rules/`. If none exists, create `AGENTS.md`.
2. If the file already has a block between `<!-- edt-url-navigator:begin -->` and `<!-- edt-url-navigator:end -->`, replace that block. Otherwise append it at the end. Change nothing else in the file.
3. Copy the section exactly: keep the Cyrillic as is and do not percent-encode it.
4. Reply with the file path and one example link built for a module from this project.

Section to add:

````markdown
<!-- edt-url-navigator:begin -->
## 1C code links (EDT URL Navigator)

When you link a 1C module, method, line, or metadata object, keep your usual link as is and append an EDT link right after it: ` ([EDT](http://127.0.0.1:18321/open?<params>))`.

- Raw UTF-8 Cyrillic in the URL, never percent-encode.
- Never use the `edt://` scheme: many clients do not make it clickable.
- Prefer `method=` when the method is known (survives edits); use `line=` for exact spots (errors, diagnostics).

Params:
- `module=` 1C module name: `ОбщийМодуль.X`, `Документ.X.МодульОбъекта`, `.МодульМенеджера`, `.МодульНабораЗаписей`, `Документ.X.Форма.F`, `МодульПриложения`, `МодульСеанса`
- `line=N` · `method=Name`
- `md=Type.Name` metadata object, e.g. `Справочник.X`, `Конфигурация`; `attr=Name` with `md` (attribute, dimension, resource, tabular section)
- `project=<EDT project>` only if the name exists in several projects (e.g. base config vs extension)

Examples (EDT part):
- `([EDT](http://127.0.0.1:18321/open?module=ОбщийМодуль.ОбщегоНазначения&method=ЗначениеРеквизитаОбъекта))`
- `([EDT](http://127.0.0.1:18321/open?module=Документ.ЗаказКлиента.МодульОбъекта&line=120))`
- `([EDT](http://127.0.0.1:18321/open?md=Справочник.Номенклатура&attr=Артикул))`
<!-- edt-url-navigator:end -->
````

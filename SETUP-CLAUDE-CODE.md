# Setup for Claude Code

For Claude Code (terminal, desktop app, IDE extensions), any model. Copy everything below the line into a Claude Code chat. Claude will install the `edt-links` mod and add the link rules to its instructions. After that, clicking an "EDT" link in a reply opens the 1C object in 1C:EDT, with no browser tab.

Requires 1C:EDT running with the EDT URL Navigator plugin. If you changed the plugin's port in its preferences, replace `18321` before sending.

---

Set up EDT URL Navigator links for me. Do the steps in order and stop at the first one that fails, telling me why.

1. Check the Claude Code CLI: run `claude --version`. It must be 2.1.275 or later. If it is older or missing, do not update it yourself: tell me to run `npm i -g @anthropic-ai/claude-code`, or to install the mod in the desktop app (**+** next to the prompt box → **Plugins** → **Add marketplace** → `VVi3ard/edt-url-navigator` → **Add plugin**), then continue from step 3.
2. Install the mod: run `claude plugin install edt-links --marketplace VVi3ard/edt-url-navigator`. If it asks to add the marketplace, accept; choose the user scope. Then confirm with `claude plugin list` that `edt-links` is listed.
3. Add the section below to `~/.claude/CLAUDE.md`, or to the project's `CLAUDE.md` if I say "project". If the file already has a block between `<!-- edt-url-navigator:begin -->` and `<!-- edt-url-navigator:end -->`, replace that block; otherwise append it at the end. Change nothing else. Copy it exactly: keep the Cyrillic as is, do not percent-encode it.
4. Tell me: the file you changed, and that the mod takes effect in new sessions. Then give one example link for a module from this project, built by the rules below.

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

# Cleanup Plans Agent — jfitVLMC

Rimuove i file piano completati dalla directory `plan/`. Verifica se il branch associato e' stato merged in main.

## Input

$ARGUMENTS

Flags opzionali:
- `--dry-run` — mostra cosa verrebbe rimosso senza rimuovere (DEFAULT)
- `--all` — rimuove tutti i piani completati senza chiedere per ognuno
- `--force` — salta la conferma

Se nessun argomento: dry-run.

---

## Fase 1: Scoperta piani

### 1.1 Lista file plan

```bash
ls plan/*.md 2>/dev/null
```

Se la directory e' vuota o non esiste: STOP con messaggio.

### 1.2 Per ogni piano, estrai il branch

Leggi ogni file `.md` in `plan/` e cerca la riga `**Branch:** \`<branch-name>\``.

---

## Fase 2: Classificazione

Per ogni piano trovato:

### 2.1 Verifica se il branch e' stato merged

```bash
git branch -a --merged main | grep -q "<branch-name>"
```

Oppure verifica se una PR con quel branch e' stata merged:
```bash
gh pr list --state merged --head "<branch-name>" --json number,title --jq '.[0].number'
```

### 2.2 Classifica

- **COMPLETATO** — branch merged in main (o PR merged)
- **IN CORSO** — branch esiste ma non merged
- **ORFANO** — branch non esiste ne' localmente ne' su remote (probabilmente legacy)

---

## Fase 3: Report

```
===================================
  PLAN CLEANUP REPORT
===================================

  Piani completati (candidati a rimozione):
    - plan/fix-critical-learning-bugs.md (branch merged)
    - plan/maven-multimodule-ci-fix.md (branch merged)

  Piani in corso (NON rimuovere):
    - plan/agentic-workflow-upgrade.md (branch attivo)

  Piani orfani (nessun branch trovato):
    - plan/comprehensive-test-suite.md

===================================
```

---

## Fase 4: Esecuzione

### 4.1 Dry-run (default)

Mostra solo il report. Suggerisci il comando per rimuovere.

### 4.2 Rimozione

**Se non dry-run:**

Per ogni piano COMPLETATO (e ORFANO se --all):

Chiedi conferma (a meno che --force o --all):
```
Rimuovere plan/<file>.md? (y/n)
```

```bash
git rm plan/<file>.md
```

Commit finale:
```bash
git commit -m "chore: cleanup completed plan files

Removed: <lista file>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Regole

1. **Mai rimuovere** piani con branch attivo (non merged)
2. **Dry-run di default** — mostra prima, rimuove poi
3. **Conferma** — chiedi prima di ogni rimozione (tranne --all/--force)
4. **Git rm** — rimuovi dal tracking git, non solo dal filesystem
5. **Un singolo commit** per tutte le rimozioni

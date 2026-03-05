# Cleanup Branches Agent — jfitVLMC

Pulisce i branch merged locali e remoti. Protegge main/master/develop.

## Input

$ARGUMENTS

Flags opzionali:
- `--dry-run` — mostra cosa verrebbe cancellato senza cancellare (DEFAULT)
- `--remote` — cancella anche i branch remoti
- `--all` — combina local + remote
- `--force` — salta la conferma (usare con cautela)

Se nessun argomento: dry-run locale.

---

## Fase 1: Preparazione

### 1.1 Fetch e prune

```bash
git fetch --prune
```

### 1.2 Identifica branch corrente

```bash
git branch --show-current
```

Salva come `$CURRENT`. Non cancellare mai il branch corrente.

---

## Fase 2: Trova branch merged

### 2.1 Branch locali merged in main

```bash
git branch --merged main | grep -v -E '^\*|main|master|develop'
```

### 2.2 Branch remoti merged in main (se --remote o --all)

```bash
git branch -r --merged origin/main | grep -v -E 'main|master|develop|HEAD' | sed 's/origin\///'
```

### 2.3 Branch locali con tracking branch cancellato

```bash
git branch -vv | grep ': gone]' | awk '{print $1}'
```

---

## Fase 3: Report

Stampa:
```
===================================
  BRANCH CLEANUP REPORT
===================================

  Branch locali merged (candidati a cancellazione):
    - feat/fix-critical-learning-bugs-r2
    - feat/fix-vlmc-learning-bugs
    - test/ci-verification

  Branch locali con remote cancellato:
    - feat/old-experiment

  Branch remoti merged (candidati):
    - origin/feat/fix-critical-learning-bugs-r2
    - origin/test/ci-verification

  Protetti (non cancellati):
    - main, master, develop

  Branch corrente (non cancellato):
    - $CURRENT
===================================
```

---

## Fase 4: Esecuzione

### 4.1 Modalita' dry-run (default)

Se `--dry-run` o nessun flag: mostra solo il report, suggerisci il comando per cancellare.

### 4.2 Cancellazione

**Se non dry-run:**

Chiedi conferma (a meno che `--force`):
```
Cancellare N branch locali e M branch remoti? (y/n)
```

Branch locali:
```bash
git branch -d <branch-name>
```

Branch remoti (se --remote o --all):
```bash
git push origin --delete <branch-name>
```

Usa `-d` (safe delete), mai `-D`. Se `-d` fallisce (branch non merged), segnala e salta.

---

## Fase 5: Report finale

```
  Cancellati:
    Local:  N branch
    Remote: M branch

  Skippati (non merged):
    - <branch> — contiene commit non merged

  Suggerimento:
    git config --global fetch.prune true
```

---

## Regole

1. **Mai cancellare** main, master, develop, o il branch corrente
2. **Dry-run di default** — meglio mostrare prima, cancellare poi
3. **Safe delete** (`-d`) — non forzare cancellazione di branch non merged
4. **Conferma** — chiedi sempre prima di cancellare (tranne con --force)
5. **Fetch prima** — assicurati che le info sui branch remoti siano aggiornate

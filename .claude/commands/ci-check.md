# CI Check Agent — jfitVLMC

Diagnostica e fixa automaticamente i fallimenti della CI GitHub Actions. Scarica i log dei job falliti, analizza le root cause e applica fix iterativamente.

## Input

$ARGUMENTS

Flags opzionali:
- `--fix` — applica fix automaticamente senza chiedere conferma
- `--watch` — aspetta che una run in corso finisca prima di analizzare
- `<run-id>` — analizza una run specifica invece dell'ultima

Se nessun argomento: analizza l'ultima CI run sul branch corrente.

---

## Fase 1: Rileva contesto

### 1.1 Branch corrente

```bash
git branch --show-current
```

Salva come `$BRANCH`.

### 1.2 Trova l'ultima CI run

```bash
gh run list -b $BRANCH --limit 1 --json databaseId,status,conclusion,event,headSha,createdAt
```

**Casi:**

- **Nessuna run trovata:** STOP con messaggio informativo.
- **Run in corso:** `gh run watch <run-id> --exit-status`, poi procedi all'analisi.
- **Run completata con successo:** STOP. Nulla da fixare.
- **Run fallita:** Procedi alla Fase 2.

---

## Fase 2: Analisi fallimenti

### 2.1 Lista job e loro stato

```bash
gh run view <run-id> --json jobs --jq '.jobs[] | {name: .name, conclusion: .conclusion, status: .status}'
```

### 2.2 Scarica log dei job falliti

```bash
gh run view <run-id> --log-failed 2>&1 | tail -200
```

### 2.3 Analisi root cause

Per ogni job fallito, identifica:
1. **Tipo di errore**: compile error, test failure, dependency issue, quality gate (spotless/spotbugs/jacoco)
2. **Root cause**: il motivo preciso (es. "SpotBugs ha trovato null dereference in VlmcNode.java:45")
3. **File coinvolti**: quali file del progetto devono cambiare
4. **Fix proposto**: la modifica specifica da applicare

Stampa per ogni job:
```
--- <job-name> ---
  Tipo:   <tipo di errore>
  Causa:  <root cause in una riga>
  File:   <lista file da modificare>
  Fix:    <descrizione del fix>
```

---

## Fase 3: Applicazione fix

### 3.1 Decisione

**Se invocato con `--fix`:** procedi direttamente.
**Altrimenti:** mostra i fix proposti e chiedi conferma.

### 3.2 Implementa i fix

Per ogni fix approvato:

1. **Leggi** il file da modificare con Read
2. **Applica** la modifica con Edit
3. **Compila:**
   ```bash
   mvn compile -q
   ```
4. **Test locale** rapido:
   ```bash
   # Unit test
   mvn test -q
   # Se il fix riguarda quality gate
   mvn spotless:check -q
   mvn compile spotbugs:check -q
   mvn verify -DskipTests -q
   ```

### 3.3 Commit e push

```bash
git add <file modificati>
git commit -m "fix(ci): <descrizione concisa del fix>

<dettaglio delle root cause risolte>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"

git push origin $BRANCH
```

---

## Fase 4: Re-check (loop)

### 4.1 Aspetta la nuova run

```bash
sleep 10
gh run list -b $BRANCH --limit 1 --json databaseId,status,headSha
```

Se la nuova run e' stata creata:
```bash
gh run watch <new-run-id> --exit-status
```

### 4.2 Retry logic

- **Retry <= 3:** Torna alla Fase 2 con la nuova run
- **Retry > 3:** Report con stato WARNING, potrebbe richiedere intervento manuale

---

## Fase 5: Report finale

```
===================================
  CI CHECK REPORT — jfitVLMC
===================================

  Branch:     $BRANCH
  Run:        #<id>
  Stato:      PASS | FAIL | PARTIAL

  Job Results:
    compile:  PASS | FAIL
    test:     PASS | FAIL
    quality:  PASS | FAIL

  Fix applicati:  <N>
  Tentativi:      <N>/3
  Commit:         <lista hash>

===================================
```

---

## Regole

1. **Diagnosi prima di tutto** — mai applicare fix senza aver capito la root cause
2. **Test locali** — verifica sempre localmente prima di pushare
3. **Max 3 retry** — non entrare in loop infiniti
4. **Trasparenza** — mostra sempre cosa sta succedendo e perche'
5. **Rispetta il codebase** — i fix devono seguire le convenzioni del progetto (CamelCase, Java 17, Maven)
6. **No scope creep** — fixa SOLO gli errori CI, non "migliorare" altro codice

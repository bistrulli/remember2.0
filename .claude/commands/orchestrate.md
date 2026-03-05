# Orchestrate Agent — jfitVLMC

Pipeline autonomo end-to-end. Legge un piano strutturato da disco, crea un feature branch, implementa ogni task con verifica e micro-commit, e produce un report finale.

## Input

$ARGUMENTS

L'argomento e' il path al file piano (es. `plan/add-prediction-cache.md`). Se non specificato, cerca il file `.md` piu' recente in `plan/`.

## Modalita'

- **Normale:** `/orchestrate plan/<file>.md` — esegue dall'inizio
- **Continue:** `/orchestrate --continue plan/<file>.md` — riprende dal primo task non ancora committato

---

## Fase 0: Setup

### 0.1 Leggi il piano

```
Read(plan/<file>.md)
```

Parsa il piano markdown. Estrai:
- **Branch name** dal campo `Branch:` nell'header
- **Lista task** dalle sezioni `## Task N: ...`
- Per ogni task: package, file, cosa, dipendenze, comando di verifica

Se il file non esiste o il formato e' invalido, **STOP** con messaggio di errore chiaro.

### 0.2 Setup git

```bash
# Assicurati di essere su un working tree pulito
git status --porcelain
```

- Se ci sono modifiche non committate: **STOP** — "Working tree non pulito. Committa o stasha prima di procedere."
- Se flag `--continue`: salta la creazione branch, verifica di essere gia' sul branch corretto.

```bash
# Crea e switcha al feature branch (solo se non --continue)
git checkout -b feat/<feature-name>
```

### 0.3 Verifica ambiente

```bash
java -version 2>&1 | head -1
mvn -version 2>&1 | head -1
```

Se Java o Maven non sono disponibili: **STOP** — "Java 17+ e Maven 3.x sono richiesti. Esegui `/setup-env --check`."

### 0.4 Compilazione iniziale

Verifica che il progetto compili prima di iniziare:

```bash
mvn compile -q
```

Se fallisce: **STOP** — "Il progetto non compila. Risolvi gli errori prima di procedere."

### 0.5 Draft PR anticipata (solo se non --continue)

Pusha il branch e crea una draft PR subito, cosi' la CI si attiva ad ogni push successivo:

```bash
git push -u origin feat/<feature-name>
gh pr create --draft --title "feat: <titolo dal piano>" --body "$(cat <<'PREOF'
## In progress

Piano: `plan/<file>.md`

Auto-created by `/orchestrate` — will be updated on completion.
PREOF
)"
```

### 0.6 Determina progresso (se --continue)

Leggi i commit message del branch per capire quali task sono gia' completati:

```bash
git log --oneline main..HEAD
```

I commit seguono il pattern `feat(<package>): <titolo> (#N/M)`. Marca come completati i task il cui numero appare nei commit.

---

## Fase 1: Esecuzione task-by-task

Per ogni task **non ancora completato**, in ordine numerico:

### 1.1 Verifica dipendenze

Se il task ha `Dipende da: Task N` e Task N non e' completato (ne' committato ne' completato in questa sessione):
- Se Task N e' stato **skippato** — **STOP** con messaggio: "Task #X dipende da Task #N che e' fallito. Intervento manuale richiesto."
- Se Task N non e' ancora stato raggiunto — **errore nel piano** (ordine sbagliato)

### 1.2 Implementa

```
Stampa: "Task #N/M: <titolo>"
```

1. **Leggi** tutti i file elencati nel task PRIMA di modificarli
2. **Implementa** il cambiamento minimale — solo quello che il task richiede
3. Segui le convenzioni del codebase esistente (naming, imports, patterns)
4. Se il task richiede un nuovo file, usa `Write`; altrimenti `Edit`

### 1.3 Compila

Dopo ogni modifica, verifica che il progetto compili:

```bash
mvn compile -q 2>&1 | tail -20
```

Se la compilazione fallisce, fixa automaticamente e ri-verifica.

### 1.4 Test specifico

Esegui il comando di verifica specificato nel task:

```bash
<comando dal campo Verifica>
```

Di default: `mvn test -q` oppure `mvn test -pl . -Dtest=<TestClass> -q` se il task specifica un test.

### 1.4c BS Detection

Prima di committare, analizza le modifiche del task corrente per scovare pattern tipici di LLM. Segui le regole definite in `.claude/commands/detect-bs.md`.

1. **Calcola il diff del task**: `git diff -- <file modificati dal task>`
2. **Fase A — Detection deterministica**: Sulle righe aggiunte del diff, cerca:
   - **CAT-1 (Scaffolding vuoto)**: metodi con solo `throw new UnsupportedOperationException()`, `return null` come unico statement, `// TODO`/`// FIXME`/`// HACK`
   - **CAT-2 (Fallback silenziosi)**: `catch (Exception e) {}`, catch block che swallowano eccezioni senza logging ne' re-throw
   - **CAT-6 (Test finti)**: test senza assert, `assertTrue(true)`, `assertNotNull` come unica verifica
   - **CAT-7 (Workaround fragili)**: check su substring di messaggi, `Thread.sleep()` come sincronizzazione, instanceof chain al posto di polimorfismo
   - **CAT-4 (Codice morto)**: commenti `// removed`/`// deprecated`, `@SuppressWarnings` senza motivo
3. **Fase B — Analisi semantica**: Confronta con la descrizione del task:
   - **CAT-3 (Over-engineering)**: file nuovi con <10 righe di logica, helper monouso, interfacce con una sola implementazione
   - **CAT-5 (Scope creep)**: error handling non richiesto, parametri opzionali bonus, javadoc aggiunto a codice non toccato

**Azione in base al risultato:**

- **BLOCKER trovati (CAT-1, CAT-2, CAT-6, CAT-7)**:
  - Stampa i finding
  - Fixa automaticamente
  - Ri-esegui compile + test
  - Ri-esegui BS detection (max 2 iterazioni)
  - Se ancora BLOCKER dopo 2 fix: segnala nel report e procedi
- **Solo WARNING (CAT-3, CAT-4, CAT-5)**: stampa i finding, procedi con il commit
- **CLEAN**: procedi silenziosamente

### 1.5 Decisione

**Se PASS:**

```bash
git add <file modificati>
git commit -m "$(cat <<'EOF'
feat(<package>): <titolo task> (#N/M)

<una riga di spiegazione del perche'>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

Stampa:
```
Task #N/M: <titolo> — PASS
   File: <lista>
   Commit: <hash breve>
```

Push al remote per attivare la CI in background:
```bash
git push origin $BRANCH
```

**Se FAIL (primo tentativo):**
- Analizza l'errore
- Tenta un fix (max 2 tentativi)
- Se il fix funziona — committa normalmente
- Se il fix non funziona — vai a 1.6

### 1.6 Gestione fallimento

Stampa:
```
Task #N/M: <titolo> — FAIL dopo 2 tentativi
   Errore: <messaggio di errore>
```

**Strategia smart:**
- Controlla se task successivi dipendono da questo task
- Se **SI** (task bloccante): **STOP COMPLETO**
  ```
  STOP: Task #N e' bloccante per task successivi.
     Intervento manuale richiesto.
     Per riprendere dopo il fix: /orchestrate --continue plan/<file>.md
  ```
- Se **NO** (task indipendente): **SKIP e continua**
  ```
  SKIP: Task #N non blocca task successivi. Continuo con Task #N+1.
     Task #N andra' completato manualmente.
  ```
  Annulla le modifiche del task fallito:
  ```bash
  git checkout -- <file modificati dal task>
  ```

---

## Fase 2: Self-review

Dopo che tutti i task (o tutti quelli non skippati) sono completati:

```bash
git diff main..HEAD --stat
git diff main..HEAD
```

Verifica rapida su tutte le modifiche del branch:
- [ ] Correttezza logica
- [ ] Error handling presente dove serve
- [ ] No credenziali esposte
- [ ] Naming coerente (CamelCase classi, camelCase metodi)
- [ ] Import non ridondanti
- [ ] Compatibilita' Java 17

Se trovi problemi:
1. Fixa
2. Committa il fix: `fix(<package>): <descrizione fix>`
3. Annota nel report

---

## Fase 3: Test suite completa

Esegui la test suite completa:

```bash
mvn clean test
```

Se la test suite fallisce ma i test specifici dei task erano passati, potrebbe essere una regressione. Analizza e fixa se possibile.

---

## Fase 3.5: CI Verification

La draft PR e' stata creata in Fase 0.5 e ogni task ha fatto push in Fase 1.5, quindi la CI e' gia' in corso sull'ultimo commit. Qui aspettiamo il risultato e fixiamo se necessario.

### 3.5.1 Aspetta la CI run

```bash
sleep 10

# Trova la run piu' recente
gh run list -b $BRANCH --limit 1 --json databaseId,status,conclusion,headSha
```

Se c'e' una run in corso o appena creata:
```bash
gh run watch <run-id> --exit-status
```

### 3.5.2 Analisi risultato

**Se PASS:**
```
CI PASS — Tutti i job sono passati.
```
Procedi alla Fase 4.

**Se FAIL:**
```
CI FAIL — Analisi in corso...
```

Scarica i log dei job falliti:
```bash
gh run view <run-id> --log-failed 2>&1 | tail -200
```

Per ogni job fallito:
1. Identifica tipo di errore e root cause
2. Leggi i file coinvolti
3. Applica il fix minimale
4. Compile + test locale
5. Commit:
   ```bash
   git add <file>
   git commit -m "$(cat <<'EOF'
   fix(ci): <descrizione fix>

   Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
   EOF
   )"
   ```
6. Push:
   ```bash
   git push origin $BRANCH
   ```

### 3.5.3 Retry loop

- Dopo il push, aspetta la nuova CI run (torna a 3.5.1)
- **Max 3 tentativi** di fix
- Se dopo 3 retry la CI ancora fallisce:
  ```
  CI ancora in errore dopo 3 tentativi.
     Ultimo errore: <descrizione>
  ```
  Procedi alla Fase 4 con stato CI = WARNING.

---

## Fase 4: Report finale

```
===================================================
  ORCHESTRATE REPORT — jfitVLMC
===================================================

  Piano:    plan/<file>.md
  Branch:   feat/<feature-name>
  Durata:   <N task completati>/<M totali>

  Task completati:
    #1: <titolo> — <commit hash>
    #2: <titolo> — <commit hash>
    #3: <titolo> — SKIPPED (non bloccante)
    #4: <titolo> — <commit hash>

  Review:   CLEAN | <N fix applicati>
  BS Check: <N BLOCKER fixati, M WARNING segnalati> | CLEAN
  Test:     PASS | FAIL (<dettagli>)
  CI:       PASS | FAIL | WARNING (<dettagli>) | SKIPPED (no PR)

  Commit totali: N
  File modificati: N

  --- Prossimi passi ---

  # Rivedi le modifiche:
  git log --oneline main..HEAD
  git diff main..HEAD

  # Squasha se vuoi un singolo commit:
  git rebase -i main

  # Merge quando pronto:
  gh pr merge --squash
===================================================
```

Se ci sono task SKIPPED, aggiungere:
```
  Task incompleti:
    #3: <titolo> — <motivo del fallimento>
    Risolvi manualmente, poi: /orchestrate --continue plan/<file>.md
```

---

## Regole fondamentali

1. **Autonomia massima** — procedi senza chiedere, fermati solo su fallimenti bloccanti
2. **Verifica sempre** — mai committare codice che non compila o non passa i test
3. **Micro-commit** — un commit per task, messaggi descrittivi con numero task
4. **Reversibilita'** — se un task fallisce, `git checkout` delle sue modifiche prima di procedere
5. **Trasparenza** — stampa lo stato dopo ogni task per tenere l'utente informato
6. **No scope creep** — implementa ESATTAMENTE quello nel piano, niente bonus
7. **Working tree pulito** — non lasciare mai modifiche non committate tra un task e l'altro

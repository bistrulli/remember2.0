# Piano: Upgrade Workflow Agentico jfitVLMC

**Branch:** `feat/agentic-workflow-upgrade`
**Servizi coinvolti:** .claude/commands, .claude/skills, pom.xml, .github/workflows, .githooks
**Stima:** 13 task, ~25 file

---

## Task 1: Aggiungere comando /ci-check
- **Package:** .claude/commands
- **File:** `.claude/commands/ci-check.md`
- **Cosa:** Creare agent `/ci-check` che diagnostica e fixa CI failures. Adattato da repo-slopilot-whatif per Maven: scarica log CI via `gh`, identifica root cause (compile error, test failure, dependency), propone fix, applica con `--fix`, rilancia CI. Supporta `--fix`, `--watch`, `<run-id>`. Max 3 retry.
- **Perche:** L'orchestratore fa retry generico senza analizzare i log. Un agent dedicato riduce i cicli falliti.
- **Dipende da:** Nessuno
- **Criteri:** Il comando appare nella lista skill, la struttura segue il pattern degli altri comandi, gestisce `mvn test` e `mvn compile` failures.
- **Verifica:** `cat .claude/commands/ci-check.md | head -5` (esiste e ha contenuto)

---

## Task 2: Aggiungere comando /cleanup-branches
- **Package:** .claude/commands
- **File:** `.claude/commands/cleanup-branches.md`
- **Cosa:** Creare agent che pulisce branch merged (local+remote). Supporta `--dry-run`, `--remote`, `--all`. Protegge main/master/develop. Chiede conferma prima di cancellare. Usa `git branch -d` (safe delete) e `git push origin --delete`.
- **Perche:** I branch feat/* e fix/* si accumulano dopo ogni piano eseguito dall'orchestratore.
- **Dipende da:** Nessuno
- **Criteri:** Comando funzionale, protegge branch protetti, dry-run di default.
- **Verifica:** `cat .claude/commands/cleanup-branches.md | head -5`

---

## Task 3: Aggiungere comando /cleanup-plans
- **Package:** .claude/commands
- **File:** `.claude/commands/cleanup-plans.md`
- **Cosa:** Creare agent che rimuove piani completati dalla directory `plan/`. Per ogni `.md` in `plan/`, verifica se il branch corrispondente e' stato merged in main. Supporta `--dry-run`, `--all`. Usa `git rm` + commit.
- **Perche:** I file plan si accumulano e confondono l'agente durante la navigazione.
- **Dipende da:** Nessuno
- **Criteri:** Identifica correttamente piani merged vs non-merged, dry-run di default.
- **Verifica:** `cat .claude/commands/cleanup-plans.md | head -5`

---

## Task 4: Aggiornare skill testing/SKILL.md con pattern JUnit 5 moderni
- **Package:** .claude/skills
- **File:** `.claude/skills/testing/SKILL.md`
- **Cosa:** Aggiungere sezioni per: `@ParameterizedTest` con `@CsvSource`/`@MethodSource`, `@Tag("unit")`/`@Tag("integration")`/`@Tag("e2e")`, `@TempDir` per file temporanei, `assertAll()` per asserzioni multiple, `assertThrows()` per eccezioni, pattern E2E con ProcessBuilder (come EndToEndTest). Aggiornare da JUnit 4 a JUnit 5 convention. Aggiungere tabella test esistenti aggiornata (CsvEventLogReaderTest, LikelihoodTest, EndToEndTest, PruningTest, ecc.).
- **Perche:** La skill attuale descrive solo JUnit 4 e pattern base. I test nuovi usano JUnit 5.
- **Dipende da:** Nessuno
- **Criteri:** La skill documenta tutti i pattern JUnit 5 usati nel progetto. Nessun riferimento a JUnit 4 residuo.
- **Verifica:** `grep -c "ParameterizedTest\|assertAll\|TempDir\|Tag" .claude/skills/testing/SKILL.md` (almeno 4 match)

---

## Task 5: Aggiungere Spotless + google-java-format al pom.xml
- **Package:** build
- **File:** `pom.xml`
- **Cosa:** Aggiungere `spotless-maven-plugin` (v2.43.0) con google-java-format (v1.22.0, stile AOSP 4-space). Configurare `ratchetFrom` a `origin/main` per enforcement incrementale. Goal `check` bound alla fase `verify`. Aggiungere `removeUnusedImports`.
- **Perche:** L'hook PostToolUse formatta solo i file toccati da Claude, ma non c'e' enforcement in CI. Spotless garantisce consistenza.
- **Dipende da:** Nessuno
- **Criteri:** `mvn spotless:check` passa senza errori sul codice attuale (o con `ratchetFrom` che limita ai file changed). `mvn spotless:apply` formatta correttamente.
- **Verifica:** `mvn spotless:check -q`

---

## Task 6: Aggiungere SpotBugs al pom.xml
- **Package:** build
- **File:** `pom.xml`
- **Cosa:** Aggiungere `spotbugs-maven-plugin` (v4.9.8.0) con effort=Max, threshold=Medium, failOnError=true. Goal `check` bound alla fase `verify`. Aggiungere exclude filter per falsi positivi noti (es. ANTLR generated code in `antlr/` package).
- **Perche:** Nessun tool di static analysis nel progetto. SpotBugs trova null pointer, resource leak, concurrency bug a livello bytecode.
- **Dipende da:** Nessuno
- **Criteri:** `mvn spotbugs:check` passa. Se ci sono finding legittimi, creare `spotbugs-exclude.xml` per il codice generato ANTLR.
- **Verifica:** `mvn compile spotbugs:check -q`

---

## Task 7: Aggiungere JaCoCo al pom.xml
- **Package:** build
- **File:** `pom.xml`
- **Cosa:** Aggiungere `jacoco-maven-plugin` (v0.8.14) con: `prepare-agent` (instrument), `report` (genera HTML), `check` (enforce threshold). Threshold: BUNDLE LINE 50% (partenza conservativa dato il codice legacy), BUNDLE BRANCH 40%. Escludere dal coverage: package `antlr/` (generato), `TestEcfGenerator` (main class di test).
- **Perche:** Nessuna misura di coverage. Serve una baseline per poi alzare progressivamente.
- **Dipende da:** Nessuno
- **Criteri:** `mvn verify` passa con coverage check. Report HTML generato in `target/site/jacoco/`.
- **Verifica:** `mvn verify -q && ls target/site/jacoco/index.html`

---

## Task 8: Separare unit test e integration test (Surefire + Failsafe)
- **Package:** build, test
- **File:** `pom.xml`, `src/test/java/test/EndToEndTest.java`
- **Cosa:** Configurare `maven-surefire-plugin` (v3.2.5) per escludere `@Tag("e2e")`. Aggiungere `maven-failsafe-plugin` (v3.2.5) per includere solo `@Tag("e2e")`. Annotare `EndToEndTest` con `@Tag("e2e")`. Aggiungere `trimStackTrace=false` a entrambi. Usare `@{argLine}` per compatibilita' JaCoCo.
- **Perche:** Il test E2E richiede il fat JAR buildato e impiega piu' tempo. Separarlo permette `mvn test` veloce e `mvn verify` completo.
- **Dipende da:** Task 7 (JaCoCo argLine compatibility)
- **Criteri:** `mvn test` esegue solo unit test (no EndToEndTest). `mvn verify` esegue tutto incluso E2E.
- **Verifica:** `mvn test -q 2>&1 | grep -c "EndToEndTest"` (deve essere 0) e `mvn verify -q 2>&1 | grep "EndToEndTest"` (deve apparire)

---

## Task 9: Aggiungere .githooks/pre-push per branch naming
- **Package:** git
- **File:** `.githooks/pre-push`, `pom.xml` (opzionale: git-build-hook plugin)
- **Cosa:** Creare script `.githooks/pre-push` che valida il nome del branch corrente con regex `^(feat|fix|hotfix|chore|release|main|master)(/[a-z0-9._-]+)?$`. Se non matcha, blocca il push con messaggio d'errore chiaro. Documentare `git config core.hooksPath .githooks` nel setup-env.
- **Perche:** L'orchestratore crea branch `feat/<name>` ma non c'e' enforcement. Previene branch con nomi sbagliati.
- **Dipende da:** Nessuno
- **Criteri:** Push su branch `feat/test-name` funziona. Push su branch `my bad name` viene bloccato.
- **Verifica:** `bash -c 'BRANCH="feat/test"; [[ "$BRANCH" =~ ^(feat|fix|hotfix|chore|release|main|master)(/[a-z0-9._-]+)?$ ]] && echo PASS || echo FAIL'`

---

## Task 10: Aggiungere git-cliff per changelog automatico
- **Package:** build, docs
- **File:** `cliff.toml`, `.github/workflows/ci.yml` (opzionale)
- **Cosa:** Creare `cliff.toml` configurato per conventional commits (feat, fix, refactor, test, docs, chore). Group by type, include scope (fitvlmc, vlmc, suffixarray, ci, build). Template con link ai commit. Non aggiungere a CI per ora — solo uso manuale `git-cliff -o CHANGELOG.md`.
- **Perche:** Il progetto usa gia' conventional commits. Changelog automatico serve per tracciare le release.
- **Dipende da:** Nessuno
- **Criteri:** `git-cliff --config cliff.toml -o CHANGELOG.md` genera un changelog valido (se git-cliff e' installato). La config e' corretta anche se il tool non e' installato.
- **Verifica:** `cat cliff.toml | head -5` (esiste e ha formato TOML valido)

---

## Task 11: Aggiornare CI pipeline con quality gates
- **Package:** ci
- **File:** `.github/workflows/ci.yml`
- **Cosa:** Aggiungere step alla CI pipeline: (1) `mvn spotless:check` — formatting gate, (2) `mvn compile spotbugs:check` — static analysis, (3) `mvn test` — unit test (solo surefire), (4) `mvn verify -DskipTests` — JaCoCo coverage check. Mantenere i job `compile` e `test` esistenti, aggiungere job `quality` che dipende da `compile`.
- **Perche:** Nessun quality gate in CI. Spotless, SpotBugs e JaCoCo devono bloccare la build se ci sono violazioni.
- **Dipende da:** Task 5, Task 6, Task 7, Task 8
- **Criteri:** CI pipeline ha almeno 3 job: compile, test, quality. Quality job include spotless+spotbugs+jacoco.
- **Verifica:** `cat .github/workflows/ci.yml | grep -c "spotless\|spotbugs\|jacoco"` (almeno 3 match)

---

## Task 12: Migliorare /orchestrate con integrazione /ci-check
- **Package:** .claude/commands
- **File:** `.claude/commands/orchestrate.md`
- **Cosa:** Nella Phase 3.5 (CI Verification), sostituire il retry generico con una chiamata esplicita al pattern di `/ci-check`: (1) scarica log CI con `gh run view --log-failed`, (2) identifica tipo di failure (compile/test/quality), (3) applica fix mirato, (4) ri-committa e ri-pusha. Aggiungere anche: dopo ogni task completato, creare un checkpoint mentale (annotare nel task update quali file sono stati modificati e quale era lo stato pre-modifica). Se un task fallisce dopo 2 retry, documentare nel report finale esattamente cosa e' fallito e perche'.
- **Perche:** Il retry generico spreca cicli CI. L'analisi mirata dei log riduce i tentativi necessari.
- **Dipende da:** Task 1 (ci-check)
- **Criteri:** orchestrate.md contiene riferimento alla strategia di log analysis. La sezione CI verification e' piu' dettagliata.
- **Verifica:** `grep -c "gh run view\|log-failed\|ci-check" .claude/commands/orchestrate.md` (almeno 2 match)

---

## Task 13: Aggiornare /setup-env con nuovi tool
- **Package:** .claude/commands
- **File:** `.claude/commands/setup-env.md`
- **Cosa:** Aggiungere verifica di: (1) `git config core.hooksPath` punta a `.githooks`, (2) git-cliff installato (opzionale, con istruzioni `brew install git-cliff`), (3) `mvn verify` completo (include spotless, spotbugs, jacoco). Aggiornare il report finale con lo stato di tutti i nuovi tool.
- **Perche:** Il setup-env attuale non conosce i nuovi tool aggiunti nel piano.
- **Dipende da:** Task 5, 6, 7, 9, 10
- **Criteri:** setup-env verifica almeno: Java, Maven, ECF dep, git hooks, formatter, quality tools.
- **Verifica:** `grep -c "githooks\|spotless\|spotbugs\|jacoco\|git-cliff" .claude/commands/setup-env.md` (almeno 3 match)

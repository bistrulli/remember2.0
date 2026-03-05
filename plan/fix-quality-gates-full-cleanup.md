# Piano: Fix Quality Gates — Full Cleanup

- **Moduli:** fitvlmc, vlmc, suffixarray, ecf, build
- **Stima:** 15 task, ~20 file
- **Data:** 2026-03-05

## Fase 1: Fix bug reali (rimuovere cause delle exclusion SpotBugs)

### Task 1: Fix DM_DEFAULT_ENCODING — FileWriter/FileReader senza charset
- **Package:** fitvlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/fitVlmc.java, jfitvlmc/src/main/java/fitvlmc/GenerateEcfToFile.java, jfitvlmc/src/main/java/fitvlmc/SelfLoopRemover.java
- **Cosa:** Sostituire tutti i `new FileWriter(file)` con `new FileWriter(file, StandardCharsets.UTF_8)` e `new FileReader(file)` con `new FileReader(file, StandardCharsets.UTF_8)`. Riguarda 9 occorrenze:
  - fitVlmc.java righe 141, 184, 216, 217, 385, 508, 530
  - GenerateEcfToFile.java riga 41
  - SelfLoopRemover.java riga 46
- **Perche':** Senza charset esplicito il comportamento dipende dalla piattaforma. Puo' causare bug su sistemi non-UTF8 e su CI (Ubuntu default != macOS default).
- **Dipende da:** nessuno
- **Criteri:** Nessun `new FileWriter(` o `new FileReader(` senza charset nel codebase (grep vuoto)
- **Verifica:** `mvn test -pl jfitvlmc -q && grep -rn "new FileWriter\|new FileReader" jfitvlmc/src/main/java/ | grep -v StandardCharsets | grep -v "//"`

### Task 2: Fix DM_BOXED_PRIMITIVE_FOR_PARSING — boxing inutile in hot path
- **Package:** fitvlmc, vlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/EcfNavigator.java, jfitvlmc/src/main/java/vlmc/VlmcRoot.java
- **Cosa:** Sostituire `(new Integer(x)).doubleValue()` con cast diretto `(double) x` o `Integer.parseInt(x)` dove serve. Riguarda 4 occorrenze:
  - EcfNavigator.java righe 174, 190
  - VlmcRoot.java righe 302, 318
- **Perche':** `new Integer()` e' deprecato da Java 9 e crea oggetti inutili. In hot path (fitting loop) causa overhead GC misurabile.
- **Dipende da:** nessuno
- **Criteri:** Nessun `new Integer(` nel codebase (grep vuoto)
- **Verifica:** `mvn test -pl jfitvlmc -q && grep -rn "new Integer(" jfitvlmc/src/main/java/`

### Task 3: Fix DMI_RANDOM_USED_ONLY_ONCE — Random inline
- **Package:** suffixarray, fitvlmc
- **File:** jfitvlmc/src/main/java/suffixarray/SuffixArray.java, jfitvlmc/src/main/java/fitvlmc/fitVlmc.java
- **Cosa:** Spostare `new Random()` come campo statico o di istanza (a seconda del contesto) invece di crearlo inline.
  - SuffixArray.java riga 78: spostare come campo di istanza
  - fitVlmc.java riga 328: spostare come variabile locale riusabile o campo
- **Perche':** Creare Random ogni volta riduce entropia e spreca allocazioni. SpotBugs lo segnala come DMI_RANDOM_USED_ONLY_ONCE.
- **Dipende da:** nessuno
- **Criteri:** Nessun `new Random()` creato e usato una sola volta
- **Verifica:** `mvn test -pl jfitvlmc -q`

### Task 4: Fix exception handling in SuffixArrayInt
- **Package:** suffixarray
- **File:** jfitvlmc/src/main/java/suffixarray/SuffixArrayInt.java
- **Cosa:** Correggere i 3 catch block problematici:
  - Riga 313-317: catch `IndexOutOfBoundsException` vuoto → aggiungere logica esplicita con bounds check prima del loop
  - Riga 330-334: catch `StringIndexOutOfBoundsException` (tipo sbagliato! ArrayList lancia `IndexOutOfBoundsException`) → fix tipo + bounds check esplicito
  - Riga 388-394: `e.printStackTrace()` generico → log significativo o gestione esplicita
- **Perche':** Catch vuoti nascondono bug. Il tipo di eccezione sbagliato (StringIndex vs Index) e' un bug latente che potrebbe non catturare l'eccezione su JVM diverse.
- **Dipende da:** nessuno
- **Criteri:** Nessun catch block vuoto o con solo printStackTrace(); tipo eccezione corretto
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=SuffixArrayTest -q`

### Task 5: Fix null/bounds risk nel likelihood loop
- **Package:** fitvlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/fitVlmc.java
- **Cosa:** Aggiungere bounds checking nel loop likelihood (righe 229-259):
  - Verificare `ctx.size() > 0` prima di `ctx.get(0)` a riga 229
  - Verificare `ctx.size() > p + 1` prima di `ctx.get(p + 1)` a righe 240, 257
  - In caso di violazione, loggare warning e saltare la trace (non crashare)
- **Perche':** Trace malformate o troppo corte causano `IndexOutOfBoundsException` non gestito che crasha il processo.
- **Dipende da:** nessuno
- **Criteri:** Il loop non crasha su trace vuote o con un solo elemento
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=LikelihoodTest -q`

### Task 6: Rimuovere dead code
- **Package:** fitvlmc, vlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/test.java, jfitvlmc/src/main/java/vlmc/VlmcInternalNode.java, jfitvlmc/src/main/java/fitvlmc/EcfNavigator.java
- **Cosa:**
  - Eliminare `test.java` (classe vuota legacy)
  - Eliminare `VlmcInternalNode.java` (stub non usato, documentato come "vuoto, non usato")
  - Rimuovere campi `Integer exist=0; Integer total=0;` inutilizzati in EcfNavigator.java (righe 18-19)
  - Rimuovere import duplicato `java.io.IOException` in fitVlmc.java (riga 45, duplicato di riga 9)
- **Perche':** Dead code confonde chi legge il codebase e puo' generare falsi positivi in analisi statiche.
- **Dipende da:** nessuno
- **Criteri:** I file eliminati non causano errori di compilazione; nessun import duplicato
- **Verifica:** `mvn compile -pl jfitvlmc -q && mvn test -pl jfitvlmc -q`

## Fase 2: Consolidare e inasprire i quality gate

### Task 7: Consolidare spotbugs-exclude.xml e rimuovere exclusion risolte
- **Package:** build
- **File:** spotbugs-exclude.xml (root), jfitvlmc/spotbugs-exclude.xml, ecf/spotbugs-exclude.xml, pom.xml
- **Cosa:**
  - Dopo i fix dei task 1-6, rimuovere le exclusion per `DM_DEFAULT_ENCODING`, `DM_BOXED_PRIMITIVE_FOR_PARSING`, `DMI_RANDOM_USED_ONLY_ONCE` dai filtri
  - Mantenere solo le exclusion per codice ANTLR generated (`antlr` package) e utility standalone esclusi dal flusso principale (`SelfLoopRemover`, `DebugEcfComparison`)
  - Rimuovere l'exclusion `ECFEntity` da jfitvlmc (il modulo ecf ha il suo)
  - Rimuovere `RC_REF_COMPARISON` per SuffixArrayInt (non esiste piu' il bug)
  - Rimuovere `NP_NULL_ON_SOME_PATH` per fitVlmc (fixato nel task 5)
  - Assicurarsi che il parent POM punti a `${project.basedir}/spotbugs-exclude.xml` e ogni modulo abbia il suo file appropriato
- **Perche':** Le exclusion mascheravano bug reali. Ora che sono corretti, rimuoverle rende SpotBugs effettivo.
- **Dipende da:** Task 1, 2, 3, 4, 5
- **Criteri:** `mvn compile spotbugs:check` passa con 0 bug e filtri ridotti al minimo
- **Verifica:** `mvn compile spotbugs:check -q`

### Task 8: Abbassare SpotBugs threshold a Medium
- **Package:** build
- **File:** pom.xml
- **Cosa:** Cambiare `<threshold>High</threshold>` a `<threshold>Medium</threshold>` nel plugin SpotBugs del parent POM (riga 101).
- **Perche':** Threshold High nasconde bug di media severita'. Dopo il cleanup, Medium cattura piu' problemi senza generare troppo rumore.
- **Dipende da:** Task 7
- **Criteri:** `mvn compile spotbugs:check` passa a threshold Medium
- **Verifica:** `mvn compile spotbugs:check -Dspotbugs.threshold=Medium -q`

### Task 9: Alzare soglia JaCoCo da 25% a 50%
- **Package:** build
- **File:** pom.xml
- **Cosa:** Cambiare `<minimum>0.25</minimum>` a `<minimum>0.50</minimum>` nella configurazione JaCoCo del parent POM (riga 79).
- **Perche':** 25% e' una soglia simbolica che non garantisce nulla. 50% e' raggiungibile dopo i nuovi test (task 10-13) e piu' significativa.
- **Dipende da:** Task 10, 11, 12, 13 (i nuovi test devono prima alzare la coverage)
- **Criteri:** `mvn verify` passa con soglia 50%
- **Verifica:** `mvn verify -pl jfitvlmc -q`

## Fase 3: Nuovi test per alzare coverage

### Task 10: Test per Trace2EcfIntegrator (0% → ~80%)
- **Package:** test
- **File:** jfitvlmc/src/test/java/test/Trace2EcfIntegratorTest.java (nuovo)
- **Cosa:** Creare test JUnit 5 per `Trace2EcfIntegrator`:
  - `testCreateEcfFromContent_simpleLinearTrace`: "A B C end$" → Flow con 3 nodi e 2 archi
  - `testCreateEcfFromContent_multipleTraces`: "A B end$ C D end$" → 2 trace distinte
  - `testCreateEcfFromContent_withCosts`: "A_5 B_3 end$" → verifica parsing costi
  - `testCreateEcfFromContent_emptyInput`: input vuoto → IllegalArgumentException
  - `testCreateEcfFromContent_nullInput`: null → IllegalArgumentException
  - `testCreateEcfFromContentWithValidation`: verifica output validazione (start/end nodes count)
  - `testCreateEcfFromContent_cyclicTrace`: "A B A end$" → verifica ciclo
- **Perche':** Classe core per auto-ECF generation a 0% coverage. Qualsiasi regressione sarebbe invisibile.
- **Dipende da:** nessuno
- **Criteri:** 7+ test che coprono path normali, edge case, e errori
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=Trace2EcfIntegratorTest -q`

### Task 11: Test per SuffixArrayInt (0% → ~60%)
- **Package:** test
- **File:** jfitvlmc/src/test/java/test/SuffixArrayIntTest.java (nuovo)
- **Cosa:** Creare test JUnit 5 per `SuffixArrayInt`:
  - `testConstruction`: costruzione da ArrayList<Integer>, verifica lunghezza
  - `testCount_existingPattern`: pattern presente → count > 0
  - `testCount_missingPattern`: pattern assente → count = 0
  - `testLcp`: verifica LCP array (Longest Common Prefix)
  - `testFirstLastOccurrence`: verifica posizioni first/last
  - `testBoundaryConditions`: input vuoto, singolo elemento, pattern piu' lungo dell'array
- **Perche':** Classe fondamentale per il pattern matching a 0% coverage. I catch block corretti nel task 4 devono essere validati.
- **Dipende da:** Task 4 (catch block corretti)
- **Criteri:** 6+ test che coprono costruzione, ricerca, LCP, e boundary
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=SuffixArrayIntTest -q`

### Task 12: Test aggiuntivi per VlmcRoot (37% → ~55%)
- **Package:** test
- **File:** jfitvlmc/src/test/java/test/VlmcSerializationTest.java (esistente, estendere), jfitvlmc/src/test/java/test/LikelihoodTest.java (esistente, estendere)
- **Cosa:** Aggiungere test per path non coperti di VlmcRoot:
  - Simulazione: `testSimulate_producesValidTraces` — verifica che le tracce simulate contengano solo simboli noti
  - Likelihood su trace vuota: `testLikelihood_emptyTrace` — verifica comportamento con lista vuota
  - Likelihood su trace con simbolo sconosciuto: `testLikelihood_unknownSymbol` — verifica che ritorna 0
  - Parsing VLMC da file malformato: `testParseVlmc_malformedFile` — verifica eccezione appropriata
- **Perche':** VlmcRoot e' la classe piu' importante (root dell'albero VLMC) ma ha solo 37% coverage.
- **Dipende da:** nessuno
- **Criteri:** Coverage VlmcRoot sale ad almeno 55%
- **Verifica:** `mvn test -pl jfitvlmc -Dtest="VlmcSerializationTest+LikelihoodTest" -q`

### Task 13: Ampliare EndToEndTest
- **Package:** test
- **File:** jfitvlmc/src/test/java/test/EndToEndTest.java (esistente, estendere)
- **Cosa:** Aggiungere scenari e2e per coprire path non testati:
  - `testFullPipeline_withAutoEcfGeneration`: pipeline senza `--ecf` (usa Trace2EcfIntegrator)
  - `testFullPipeline_withPrediction`: fitting → `--pred --initCtx` → verifica output predizione
  - `testFullPipeline_withCsvCustomColumns`: CSV con colonne personalizzate e separatore diverso
  - Tutti taggati `@Tag("e2e")` per esecuzione via Failsafe
- **Perche':** Il test e2e attuale copre solo fitting→likelihood. Prediction e auto-ECF sono path critici non testati end-to-end.
- **Dipende da:** Task 1, 5 (encoding fix e bounds check necessari per robustezza)
- **Criteri:** 3 nuovi test e2e che passano; coprono prediction, auto-ECF, custom CSV
- **Verifica:** `mvn verify -pl jfitvlmc -q`

## Fase 4: Sostituire System.out con logging

### Task 14: Introdurre java.util.logging e sostituire System.out.println
- **Package:** fitvlmc, vlmc, suffixarray
- **File:** jfitvlmc/src/main/java/fitvlmc/fitVlmc.java, jfitvlmc/src/main/java/fitvlmc/EcfNavigator.java, jfitvlmc/src/main/java/fitvlmc/Trace2EcfIntegrator.java, jfitvlmc/src/main/java/suffixarray/SuffixArray.java, jfitvlmc/src/main/java/suffixarray/SuffixArrayInt.java
- **Cosa:**
  - Aggiungere `private static final Logger LOGGER = Logger.getLogger(ClassName.class.getName());` nelle classi coinvolte
  - **NON toccare** l'output CLI intenzionale di fitVlmc.java (help text righe 554-635, likelihood report righe 306-313, error messages su stderr). Questi sono output utente, non logging.
  - Sostituire con `LOGGER.fine()` o `LOGGER.info()`:
    - Debug progress: EcfNavigator.java riga 56 (`done/edges.size()`) → `LOGGER.fine()`
    - Config feedback: EcfNavigator.java riga 30 (maxDepth) → `LOGGER.info()`
    - Performance timings: fitVlmc.java righe 122, 130, 135, 137, 395, 492 → `LOGGER.info()`
    - Bare debug prints: fitVlmc.java riga 114, 354, 458 → `LOGGER.fine()`
    - "sorting suffixes": SuffixArray.java riga 87, SuffixArrayInt.java riga 84 → `LOGGER.fine()`
    - Validation output: Trace2EcfIntegrator.java (7 println) → `LOGGER.info()`
  - Totale: ~22 println da convertire (le ~90 di help/CLI output restano)
- **Perche':** System.out.println per debug non e' filtrabile, non ha timestamp, non ha livelli. java.util.logging e' nel JDK, zero dipendenze aggiuntive.
- **Dipende da:** Task 6 (dead code rimosso per evitare conflitti)
- **Criteri:** Nessun `System.out.println` per output di debug (solo CLI output intenzionale); `mvn test` passa
- **Verifica:** `mvn test -pl jfitvlmc -q && grep -rn "System.out.println" jfitvlmc/src/main/java/ | grep -v "//\|help\|usage\|===\|Total\|uEMSC\|Traces\|Aggregate\|Distinct\|Server\|Saving\|saving" | wc -l`

## Fase 5: Verifica finale

### Task 15: Verifica completa quality gate + aggiornamento CI
- **Package:** build, ci
- **File:** .github/workflows/ci.yml
- **Cosa:**
  - Eseguire la pipeline completa: `mvn clean verify`
  - Verificare: SpotBugs a threshold Medium con 0 bug, JaCoCo >= 50%, Spotless OK, tutti i test (unit + e2e) passano
  - Se necessario, aggiustare il CI per eseguire `mvn verify` (include Failsafe e2e) invece di solo `mvn test`
  - Report coverage finale
- **Perche':** Verifica end-to-end che tutti i quality gate sono effettivi e non ci sono regressioni.
- **Dipende da:** Task 7, 8, 9, 10, 11, 12, 13, 14
- **Criteri:** `mvn clean verify` passa con 0 warning SpotBugs, coverage >= 50%, 0 test failures
- **Verifica:** `mvn clean verify -q`

## Ordine di Esecuzione Consigliato

```
Fase 1 (paralleli): Task 1, 2, 3, 4, 5, 6  → fix bug reali
Fase 2 (sequenziali): Task 7 → 8            → consolidare gate
Fase 3 (paralleli): Task 10, 11, 12         → nuovi test
Fase 3b: Task 13                            → e2e ampliato (dipende da 1, 5)
Fase 3c: Task 9                             → alza soglia (dipende da 10-13)
Fase 4: Task 14                             → logging
Fase 5: Task 15                             → verifica finale
```

## Rischi e Mitigazioni

| Rischio | Probabilita' | Mitigazione |
|---------|-------------|-------------|
| Fix encoding cambia output binario di file salvati | Media | Test e2e verifica round-trip: salva → ricarica modello |
| Cast diretto `(double) x` cambia semantica su overflow | Bassa | I valori sono conteggi piccoli (suffix count), overflow impossibile |
| Nuovi test flaky per timing/concurrency | Bassa | Nessun test usa thread; e2e usa subprocess con timeout |
| Coverage 50% non raggiungibile senza test fitVlmc CLI | Media | Se necessario, escludere utility standalone (SelfLoopRemover, Debug, Generate) dalla soglia JaCoCo |

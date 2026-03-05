# Piano: Fix Critical Learning Bugs (Round 2)

- **Branch:** feat/fix-critical-learning-bugs-r2
- **Moduli:** fitvlmc, vlmc
- **Stima:** 10 task, ~6 file
- **Data:** 2026-03-05

## Analisi pre-piano

L'analisi approfondita ha identificato 9 potenziali bug. Dopo verifica sul codice:
- **7 confermati** (5 CRITICAL/BUG + 2 DEFENSIVE)
- **2 rigettati**:
  - Bug 6 (getState): l'algoritmo è corretto — naviga il suffix più lungo dal fondo del contesto. Confermato da VlmcNavigationTest.
  - Bug 8 (Trace2EcfIntegrator end$): il codice registra correttamente la transizione verso "end$" (righe 62-67) e resetta lastEdge. L'ECF cattura la struttura aggregata.

---

## Task 1: Aggiungere tracciamento contesti visitati in EcfNavigator.visit()
- **Package:** fitvlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/EcfNavigator.java
- **Cosa:** Aggiungere un `Set<String>` di contesti già visitati al metodo `visit()`. Prima di creare un nodo per un contesto, verificare che non sia già stato esplorato. Se è duplicato, restituire il nodo come foglia (senza ricorsione). Il Set viene inizializzato vuoto nella chiamata a 2 parametri (riga 62) e passato ricorsivamente.
- **Perche':** Con ECF ciclici (A→B→A), il depth limit ferma la ricorsione ma non impedisce l'esplorazione ridondante dello stesso ciclo fino a maxDepth volte, producendo sottoalberi duplicati e tempi di esecuzione esponenziali.
- **Dipende da:** nessuno
- **Criteri:** Per un ECF ciclico A→B→A, ogni contesto appare al massimo una volta nell'albero VLMC. La compilazione e i test esistenti passano.
- **Verifica:** `mvn compile -q && mvn test -q`

## Task 2: Fixare regex non escaped in fitVlmc.java
- **Package:** fitvlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/fitVlmc.java
- **Cosa:** Alla riga 401-403, sostituire:
  ```java
  String[] pieces = fitVlmc.vlmcOutFile.split("/");
  pieces = pieces[pieces.length - 1].split(".vlmc");
  outFile = new File(pieces[0] + ".dcdt");
  ```
  Con:
  ```java
  String baseName = Paths.get(fitVlmc.vlmcOutFile).getFileName().toString().replaceAll("\\.vlmc$", "");
  outFile = new File(baseName + ".dcdt");
  ```
- **Perche':** `split(".vlmc")` tratta il punto come "qualsiasi carattere" nella regex. Per file come "model_vlmc.txt" il risultato è imprevedibile. Inoltre il codice fa splitting fragile su path separators.
- **Dipende da:** nessuno
- **Criteri:** Il nome base viene estratto correttamente per "model.vlmc", "/path/to/model.vlmc", "model_vlmc", etc.
- **Verifica:** `mvn compile -q && mvn test -q`

## Task 3: Fixare RESTVlmc per request malformate
- **Package:** fitvlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/RESTVlmc.java
- **Cosa:** Gestire correttamente i casi:
  1. `query == null` → rispondere HTTP 400 con messaggio "Missing query parameter"
  2. `params.length != 2` → rispondere HTTP 400 con messaggio "Invalid query format"
  3. Aggiungere null check su `node.getDist()` prima di chiamare toString()
  Sostituire il pattern throw-catch-ignore con risposte HTTP appropriate.
- **Perche':** Attualmente se la query è null il metodo esce senza inviare risposta HTTP, lasciando il client appeso. Se il formato è sbagliato, viene stampata solo una stack trace senza risposta.
- **Dipende da:** nessuno
- **Criteri:** Il server risponde HTTP 400 per request malformate e non crasha mai.
- **Verifica:** `mvn compile -q && mvn test -q`

## Task 4: Fixare KL-divergence per simboli assenti nel parent
- **Package:** vlmc
- **File:** jfitvlmc/src/main/java/vlmc/VlmcNode.java
- **Cosa:** In `KullbackLeibler()` (righe 143-154), modificare la gestione del caso `pParent == null || pParent == 0`:
  - Se `pChild > 0` ma `pParent` è null o 0 → questo indica che il child ha una distribuzione radicalmente diversa dal parent. Restituire `Double.POSITIVE_INFINITY` (il nodo NON deve essere potato).
  - Se `pChild == 0` (o < epsilon) → skip corretto (contributo 0 per convenzione KL).
  - Usare `Math.abs(pChild) < 1e-15` invece di `pChild == 0` per confronto floating-point.
- **Perche':** Attualmente il termine viene skippato, sottostimando il KL. Il risultato è che nodi con distribuzioni molto diverse dal parent vengono erroneamente potati (KL basso → pruning), perdendo informazione dal modello.
- **Dipende da:** nessuno
- **Criteri:** `KullbackLeibler()` restituisce `+∞` quando child ha simboli non presenti nel parent con probabilità > 0. I test esistenti PruningTest continuano a passare (testKLWithSymbolAbsentInParent va aggiornato per aspettarsi +∞).
- **Verifica:** `mvn compile -q && mvn test -q`

## Task 5: Fixare copy constructor che perde la distribuzione
- **Package:** vlmc
- **File:** jfitvlmc/src/main/java/vlmc/VlmcNode.java
- **Cosa:** Nel copy constructor (righe 22-30), sostituire `this.dist = new NextSymbolsDistribution()` con una copia profonda della distribuzione del nodo sorgente. Copiare symbols, probability e totalCtx. Fare lo stesso in `clone()` (riga 200).
- **Perche':** Qualsiasi operazione di copia (clone, copy constructor) produce un albero VLMC con tutte le distribuzioni vuote, rendendo il modello copiato inutile per simulazione, predizione e likelihood.
- **Dipende da:** nessuno
- **Criteri:** `new VlmcNode(node)` produce un nodo con la stessa distribuzione dell'originale. `clone()` preserva le distribuzioni.
- **Verifica:** `mvn compile -q && mvn test -q`

## Task 6: Aggiungere null safety in VlmcRoot.getLikelihood()
- **Package:** vlmc
- **File:** jfitvlmc/src/main/java/vlmc/VlmcRoot.java
- **Cosa:** In `getLikelihood()` (riga 211), aggiungere null check su `state.getDist()` prima di chiamare `getProbBySymbol()`. Se la distribuzione è null, trattare come probabilità 0 (p=0, break).
- **Perche':** Sebbene il costruttore inizializzi sempre dist, nodi creati tramite il copy constructor difettoso (Task 5) o da codice esterno potrebbero avere dist=null, causando NPE.
- **Dipende da:** nessuno
- **Criteri:** getLikelihood() non lancia mai NPE anche con nodi privi di distribuzione.
- **Verifica:** `mvn compile -q && mvn test -q`

## Task 7: Gestire distribuzione vuota quando usedCtx == 0
- **Package:** fitvlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/EcfNavigator.java, jfitvlmc/src/main/java/vlmc/VlmcRoot.java
- **Cosa:** In `createNextSymbolDistribution()` (EcfNavigator righe 169-210 e il duplicato in VlmcRoot righe 292-332): dopo il blocco else (multi-out-edge), se `usedCtx == 0` e la lista probabilità è vuota, creare una distribuzione uniforme su tutti gli out-edge (1/n per ciascuno). Questo evita distribuzioni vuote che causano problemi a valle (simulazione si ferma prematuramente, likelihood ritorna 0).
- **Perche':** Se nessun simbolo successore è osservato nel suffix array, la distribuzione resta vuota. Il modello perde informazione sulla struttura ECF. Una distribuzione uniforme è il prior non-informativo più ragionevole.
- **Dipende da:** nessuno
- **Criteri:** `createNextSymbolDistribution()` non restituisce mai una distribuzione con 0 simboli quando l'ECF ha out-edges. Le probabilità sommano sempre a 1.
- **Verifica:** `mvn compile -q && mvn test -q`

## Task 8: Test per cicli ECF (cycle detection)
- **Package:** test
- **File:** jfitvlmc/src/test/java/test/EcfNavigatorTest.java (nuovo)
- **Cosa:** Creare test che:
  1. Costruisce un ECF ciclico minimale (A→B→A) con un suffix array sintetico
  2. Esegue EcfNavigator.visit() e verifica che non ci siano nodi duplicati nel tree
  3. Verifica che il numero di nodi sia ragionevole (non esponenziale)
  4. Verifica che la navigazione termini in tempo ragionevole (timeout JUnit)
- **Perche':** Il fix del Task 1 deve essere verificato con un test dedicato per ECF ciclici, caso non coperto dalla test suite esistente.
- **Dipende da:** Task 1
- **Criteri:** Test passa e verifica assenza di duplicati e terminazione.
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=EcfNavigatorTest -q`

## Task 9: Test per KL-divergence con simboli assenti e copy constructor
- **Package:** test
- **File:** jfitvlmc/src/test/java/test/PruningTest.java (modifica)
- **Cosa:** Aggiornare e aggiungere test in PruningTest:
  1. `testKLWithSymbolAbsentInParent` → aspettarsi `Double.POSITIVE_INFINITY` invece di solo "non NaN/Infinity"
  2. Nuovo test: `testKLSymmetricMissingSymbol` — parent ha {A:0.8, B:0.2}, child ha {A:0.5, C:0.5}. KL deve essere +∞ (child ha C che parent non ha).
  3. Nuovo test: `testCopyConstructorPreservesDistribution` — verifica che copia di un nodo mantiene symbols, probabilità e totalCtx.
  4. Nuovo test: `testClonePreservesDistribution` — verifica che clone() mantiene la distribuzione.
- **Perche':** I test esistenti verificano che KL non è NaN/Infinity, ma dopo il fix del Task 4 il comportamento cambia: deve essere +∞ in certi casi. I test del copy constructor verificano il fix del Task 5.
- **Dipende da:** Task 4, Task 5
- **Criteri:** Tutti i test passano con il nuovo comportamento.
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=PruningTest -q`

## Task 10: Test per RESTVlmc con request malformate
- **Package:** test
- **File:** jfitvlmc/src/test/java/test/RESTVlmcTest.java (nuovo)
- **Cosa:** Creare test che:
  1. Avvia un HttpServer locale con RESTVlmc handler
  2. Invia request senza query → verifica HTTP 400
  3. Invia request con query malformata (nessun "=") → verifica HTTP 400
  4. Invia request valida → verifica HTTP 200 con distribuzione
  5. Shutdowna il server dopo il test
- **Perche':** Il fix del Task 3 deve essere verificato. Nessun test REST esiste attualmente.
- **Dipende da:** Task 3
- **Criteri:** Tutti i test passano, il server risponde correttamente a request valide e invalide.
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=RESTVlmcTest -q`

# Piano: Online BMA Attention per STA

- **Moduli:** vlmc, sta, test
- **Stima:** 9 task, ~12 file
- **Data:** 2026-03-06
- **Branch:** feat/online-bma-attention

## Obiettivo

Implementare due miglioramenti al meccanismo STA (Stochastic Tree Attention):

1. **Cache KL** in `VlmcNode` — eliminare il ricalcolo di `KullbackLeibler()` ad ogni predict (bottleneck di performance)
2. **Online Bayesian Model Averaging (BMA)** con fixed-share — pesi dinamici che si adattano ad ogni step della traccia (vero meccanismo di attention)
3. **Smoothing epsilon** — prevenire la morte dei contesti quando P(simbolo)=0

**VINCOLO FONDAMENTALE:** L'algoritmo di learning VLMC (EcfNavigator, pruning, costruzione albero) NON viene modificato. Tutte le modifiche sono nel package `sta/` e nella cache KL in `vlmc/VlmcNode`.

## Contesto tecnico

### Stato attuale (score statico)
```java
// StaWeightFunction.klBased() — score FISSO per ogni nodo
score(node) = log(max(1, n)) * KL(child || parent)
// Dopo softmax: pesi identici per la stessa VLMC, indipendentemente dalla traccia
```

### Nuovo design (Online BMA con fixed-share)
```
Inizializzazione: w_i = 1/K  per K contesti matchati

Ad ogni step t della traccia:
  1. Raccogli contesti matchati per history[0..t]
  2. P_i(s_t) = distribuzione del contesto i per il simbolo osservato s_t
  3. P_mix(s_t) = SUM_i w_i * P_i(s_t)
  4. anomaly_score_t = -log(P_mix(s_t))
  5. w_i <- (1-eta) * w_i * P_i(s_t) / P_mix(s_t) + eta/K    [Bayesian update + fixed-share]

Con smoothing: P_i(s_t) = max(P_i(s_t), epsilon) dove epsilon = 1e-10
```

### Riferimenti teorici
- Cesa-Bianchi & Lugosi (2006), "Prediction, Learning, and Games" — fixed-share expert framework
- Herbster & Warmuth (1998), "Tracking the Best Expert" — fixed-share regret bound
- Willems, Shtarkov & Tjalkens (1995), Context Tree Weighting — Bayesian model averaging su alberi

---

## Task 1: Cache KL in VlmcNode

- **Package:** vlmc
- **File:** `jfitvlmc/src/main/java/vlmc/VlmcNode.java`
- **Cosa:** Aggiungere campo `private Double cachedKL = null` e modificare `KullbackLeibler()` per restituire il valore cached se disponibile. Aggiungere metodo `invalidateKLCache()` che resetta il cache (chiamato se la distribuzione cambia). Aggiornare il copy constructor e `clone()` per NON copiare il cache (deve essere ricalcolato sul nuovo nodo).
- **Perche:** `KullbackLeibler()` viene chiamato milioni di volte durante lo scoring STA ma restituisce sempre lo stesso valore per lo stesso nodo. Il cache elimina il bottleneck principale (stimato: ~45M chiamate evitate su HDFS).
- **Dipende da:** nessuno
- **Criteri:**
  1. `KullbackLeibler()` restituisce lo stesso valore con e senza cache
  2. Chiamate successive a `KullbackLeibler()` sullo stesso nodo NON ricalcolano (il calcolo avviene una sola volta)
  3. `invalidateKLCache()` forza il ricalcolo alla prossima chiamata
  4. Il copy constructor e `clone()` non copiano il cache
  5. I test esistenti in `PruningTest`, `StaWeightFunctionTest` e `StaPredictorTest` passano senza modifiche
- **Verifica:** `mvn test -pl jfitvlmc -q`

## Task 2: Test per il cache KL

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/VlmcNodeKLCacheTest.java`
- **Cosa:** Creare test class dedicata con:
  1. `testCachedKLMatchesDirectComputation` — verifica che il valore cached sia identico al calcolo diretto
  2. `testCacheIsReused` — verifica che chiamate multiple non ricalcolano (misura indirettamente: il risultato deve essere identico bit-per-bit)
  3. `testInvalidateCacheForcesRecalculation` — dopo invalidazione, il ricalcolo avviene
  4. `testCloneDoesNotCopyCache` — il clone deve ricalcolare il KL (il parent potrebbe essere diverso)
  5. `testCopyConstructorDoesNotCopyCache` — same per il copy constructor
  6. `testCacheWithInfiniteKL` — se P_parent=0, KL=+inf, il cache deve gestirlo
  7. `testCacheWithZeroKL` — distribuzioni identiche, KL=0, cache funziona
- **Perche:** Il cache KL e' critico per la correttezza — un valore cached sbagliato inquinerebbe tutti gli score STA e il pruning. Test esaustivo obbligatorio.
- **Dipende da:** Task 1
- **Criteri:** Tutti i 7 test passano. Il test usa nodi costruiti a mano (come in `StaPredictorTest.buildVlmc()`), non dipende da suffix array o ECF.
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=VlmcNodeKLCacheTest -q`

## Task 3: Smoothing epsilon in StaPredictor

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/StaPredictor.java`
- **Cosa:** Aggiungere campo `private final double epsilon` (default `1e-10`). Nel metodo `mixDistributions()`, dopo il mixing, applicare floor epsilon a tutte le probabilita'. Nel metodo `predict()`, quando si calcola P_i per il Bayesian update (task successivi), usare `max(P_i, epsilon)` per evitare che un contesto "muoia" (peso -> 0 irreversibilmente). Aggiungere constructor overload per specificare epsilon.
- **Perche:** Senza smoothing, un simbolo con P=0 in un contesto causa `w_i <- 0` dopo il Bayesian update, e quel contesto non contribuisce mai piu'. L'epsilon floor previene questo senza distorcere le distribuzioni (epsilon e' trascurabile rispetto alle probabilita' reali).
- **Dipende da:** nessuno
- **Criteri:**
  1. `mixDistributions` produce probabilita' >= epsilon per tutti i simboli presenti
  2. La distribuzione resta normalizzata dopo l'applicazione di epsilon
  3. I test esistenti `StaPredictorTest` passano (epsilon trascurabile non cambia risultati)
  4. Nuovo test: un simbolo con P=0 in un contesto produce P >= epsilon nella distribuzione mixata
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=StaPredictorTest -q`

## Task 4: Implementare predictOnline con BMA fixed-share

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/StaPredictor.java`
- **Cosa:** Aggiungere metodo `public List<StaResult> predictOnline(VlmcRoot tree, List<String> trace, double eta)` che:
  1. Inizializza pesi uniformi `w_i = 1/K` per i K contesti matchati al primo step
  2. Per ogni step t (da 0 a trace.size()-2):
     a. Raccogli contesti matchati per `trace[0..t]` usando `collectMatchedContexts`
     b. Calcola `P_i(s_{t+1})` per ogni contesto i, usando `max(P_i, epsilon)` per smoothing
     c. Calcola `P_mix = SUM_i w_i * P_i`
     d. Crea `StaResult` con la distribuzione mixata e le contributions (con pesi correnti)
     e. Bayesian update: `w_i <- (1-eta) * w_i * P_i / P_mix + eta/K`
     f. Ri-normalizza i pesi (per stabilita' numerica)
  3. Gestione cambio set contesti: quando i contesti matchati cambiano tra step (es. un nuovo contesto diventa raggiungibile), i nuovi contesti ricevono peso `eta/K_new` e i vecchi vengono rinormalizzati
  4. Ritorna la lista di `StaResult` per ogni step (utile per analisi dettagliata)
  Aggiungere overload `predictOnline(VlmcRoot tree, List<String> trace)` con `eta=0.05` come default.
- **Perche:** Questo e' il core dell'Online BMA. I pesi si adattano ad ogni step: contesti che predicono bene l'osservazione ricevono piu' peso, contesti che sbagliano vengono penalizzati. Il parametro eta (fixed-share) previene che un contesto venga eliminato permanentemente, permettendo "recupero" se il miglior esperto cambia nel tempo.
- **Dipende da:** Task 1, Task 3
- **Criteri:**
  1. I pesi dopo ogni step sommano a 1.0 (entro 1e-9)
  2. La distribuzione mixata ad ogni step e' normalizzata
  3. Con eta=0 (pure BMA), il contesto che predice meglio accumula peso
  4. Con eta=1/K (massimo sharing), i pesi restano uniformi
  5. Il metodo gestisce tracce di lunghezza 1 (nessun step)
  6. Il metodo gestisce simboli non visti (P=epsilon, score alto)
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=StaPredictorTest -q`

## Task 5: Test esaustivi per predictOnline

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/OnlineBmaTest.java`
- **Cosa:** Creare test class dedicata usando la stessa VLMC costruita a mano di `StaPredictorTest` (root + 2 livelli). Test:
  1. `testWeightsConvergeTowardsBestPredictor` — traccia `[A, B, B, B, B]`: il contesto che assegna alta probabilita' a B deve guadagnare peso ad ogni step. Verificare che il peso del miglior contesto AUMENTA monotonicamente.
  2. `testWeightsSumToOneAtEveryStep` — per ogni step della traccia, la somma dei pesi deve essere 1.0 (entro 1e-9)
  3. `testDistributionNormalizedAtEveryStep` — la distribuzione mixata ad ogni step ha somma = 1.0
  4. `testMemoryEffectAfterSurprise` — traccia `[A, B, B, C, B]` dove C e' "sorprendente" per il contesto dominante. Dopo lo step con C, i pesi devono redistribuirsi. Verificare che l'anomaly score allo step C e' significativamente piu' alto degli step precedenti.
  5. `testFixedSharePreventsWeightDeath` — con eta > 0, nessun peso scende sotto eta/K anche dopo molti step in cui quel contesto predice male
  6. `testPureBmaEtaZero` — con eta=0, dopo sufficienti step, il peso del peggior contesto tende a 0
  7. `testSingleStepTraceFallsBackToStaticPredict` — una traccia di un solo evento produce un singolo StaResult consistente
  8. `testAnomalyScoreHigherForAnomalousTrace` — confronta score totale di una traccia "normale" vs una con evento anomalo: quella anomala deve avere score piu' alto
  9. `testOnlineVsStaticDifferentResults` — verifica che predictOnline produce risultati DIVERSI da predict statico (i pesi sono dinamici, non fissi)
  10. `testEpsilonPreventsInfiniteScore` — simbolo non visto in nessun contesto produce score alto ma finito (grazie a epsilon)
- **Perche:** Il BMA online e' il cuore del nuovo meccanismo. Ogni proprieta' matematica (normalizzazione, convergenza, memory effect, fixed-share bound) deve essere verificata con test dedicati. Questi test servono anche come documentazione viva del comportamento atteso.
- **Dipende da:** Task 4
- **Criteri:** Tutti i 10 test passano. Nessun test usa infrastruttura esterna (suffix array, ECF, file). Solo nodi VLMC costruiti a mano.
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=OnlineBmaTest -q`

## Task 6: Metodo scoreTraceOnline in StaAnomalyScorer

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/StaAnomalyScorer.java`
- **Cosa:** Aggiungere metodo `public TraceScore scoreTraceOnline(List<String> trace)` che:
  1. Usa `staPredictor.predictOnline(vlmc, trace)` per ottenere la lista di StaResult
  2. Calcola l'anomaly score totale come somma dei `-log(P_mix(s_t))` per ogni step
  3. Calcola anche lo score VLMC classico (come nel metodo `scoreTrace` esistente) per confronto
  4. Ritorna un `TraceScore` con entrambi gli score e i dettagli per step
  NON modificare il metodo `scoreTrace` esistente — il nuovo metodo e' in aggiunta, non in sostituzione.
- **Perche:** Separare il metodo online da quello statico permette di confrontare i due approcci sullo stesso dataset senza rottura di interfaccia. `HdfsBenchmark` e il codice esistente continuano a funzionare.
- **Dipende da:** Task 4
- **Criteri:**
  1. `scoreTraceOnline` produce un `TraceScore` valido
  2. Il campo `staScore` contiene lo score BMA online (diverso dallo statico)
  3. Il campo `vlmcScore` contiene lo score VLMC classico (invariato)
  4. Per tracce normali, `staScore` e `vlmcScore` sono entrambi finiti
  5. Il metodo `scoreTrace` esistente NON e' modificato
- **Verifica:** `mvn test -pl jfitvlmc -q`

## Task 7: Integrare Online BMA in HdfsBenchmark

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/HdfsBenchmark.java`
- **Cosa:** Aggiungere metodo `public List<BenchmarkMetrics.ScoredTrace> scoreWithStaOnline(VlmcRoot vlmc, List<HdfsSession> sessions, double beta, double eta)` che:
  1. Per ogni sessione, crea uno `StaPredictor(beta)` e chiama `predictOnline(vlmc, trace, eta)`
  2. Calcola lo score totale come somma dei anomaly score per step
  3. Ritorna lista di `ScoredTrace` per il calcolo metriche
  Nel metodo `run()`, aggiungere una sezione DOPO lo scoring STA esistente che esegue anche lo scoring Online BMA per ogni valore di beta:
  ```java
  for (double beta : betas) {
      List<ScoredTrace> onlineScores = scoreWithStaOnline(vlmc, testAll, beta, 0.05);
      BenchmarkMetrics onlineBm = new BenchmarkMetrics(onlineScores);
      results.put(String.format("BMA beta=%.2f", beta), onlineBm.findBestF1());
  }
  ```
  NON rimuovere lo scoring STA statico esistente — servono entrambi per il confronto.
- **Perche:** Il benchmark HDFS deve confrontare VLMC classic vs STA statico vs Online BMA sullo stesso split train/test per un confronto equo.
- **Dipende da:** Task 4, Task 6
- **Criteri:**
  1. `run()` produce risultati per "VLMC classic", "STA beta=X", E "BMA beta=X"
  2. I risultati BMA sono diversi dai risultati STA (pesi dinamici vs statici)
  3. Il report include tutte e tre le categorie di metodi
  4. Nessuna regressione sui risultati VLMC classic e STA statico
- **Verifica:** `mvn compile -pl jfitvlmc -q`

## Task 8: Aggiornare HdfsFullBenchmark per parametro eta

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/HdfsFullBenchmark.java`
- **Cosa:** Aggiungere opzione CLI `--eta <double>` (default 0.05) per il parametro fixed-share. Passare il valore a `HdfsBenchmark`. Aggiungere nel report finale una sezione separata per i risultati BMA con stampa del parametro eta usato.
- **Perche:** Permettere di sperimentare con diversi valori di eta dalla riga di comando senza ricompilare.
- **Dipende da:** Task 7
- **Criteri:**
  1. `--eta 0.1` viene parsato correttamente
  2. Il default e' 0.05 se non specificato
  3. Il report mostra il valore di eta usato
  4. `--help` (se presente) documenta il nuovo parametro
- **Verifica:** `mvn compile -pl jfitvlmc -q`

## Task 9: Compilazione finale, formatting, test completi

- **Package:** tutti
- **File:** tutti i file modificati nei task precedenti
- **Cosa:**
  1. Eseguire `mvn spotless:apply` per formattare tutti i file modificati
  2. Eseguire `mvn compile -q` per verificare compilazione
  3. Eseguire `mvn test -pl jfitvlmc -q` per tutti i test unitari
  4. Eseguire `mvn compile spotbugs:check -pl jfitvlmc -q` per static analysis
  5. Se ci sono errori spotbugs, fixarli (tipicamente: null checks, resource leaks)
  6. Eseguire `mvn verify -pl jfitvlmc -q` per coverage JaCoCo
- **Perche:** Quality gates devono passare prima del push. I nuovi test dovrebbero aumentare la coverage.
- **Dipende da:** Task 1, 2, 3, 4, 5, 6, 7, 8
- **Criteri:**
  1. `mvn spotless:check` passa senza errori
  2. `mvn compile spotbugs:check` passa senza errori
  3. `mvn test` tutti i test passano (esistenti + nuovi)
  4. `mvn verify` coverage >= 35%
- **Verifica:** `mvn clean verify -q`

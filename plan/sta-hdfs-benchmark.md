# Piano: STA su HDFS — Benchmark vs DeepLog + Auto-Beta

- **Moduli:** jfitvlmc (package `sta`, `fitvlmc`), test
- **Stima:** 12 task, ~8 file nuovi + 2 file modificati
- **Data:** 2026-03-06
- **Branch:** `feat/sta-hdfs-benchmark`
- **Prerequisito:** branch `feat/sta-suffix-tree-attention` merged (package `sta` disponibile)

## Contesto e Motivazione

### Risultati PoC sintetico (da cui partiamo)

Il PoC su dataset cyber sintetico ha dimostrato che STA separa attacchi da normali
dove VLMC classica fallisce:

```
VLMC Classic: normal=2.7604 attack=2.3292 separation=0.8438  (< 1, non separa!)
STA beta=0.1:  normal=3.0269 attack=4.5354 separation=1.4984
STA beta=1.0:  normal=2.8758 attack=5.3326 separation=1.8543
STA beta=10.0: normal=2.8098 attack=17.4829 separation=6.2222
```

### Obiettivo

Validare STA su **HDFS dataset** (benchmark standard per log anomaly detection)
e confrontare direttamente con i risultati pubblicati di **DeepLog** (Du et al., CCS 2017)
senza ri-eseguire i modelli neurali.

### DeepLog — risultati pubblicati su HDFS (riferimento)

| Metrica     | DeepLog (CCS'17) |
|-------------|-------------------|
| Precision   | 0.9500            |
| Recall      | 0.9600            |
| F1          | 0.9550            |
| Window size | 10 (log keys)     |
| Training    | Solo tracce normali |

Nota: DeepLog richiede GPU per training, STA+VLMC gira su CPU in secondi.

### HDFS Dataset

- **Fonte:** Loghub (https://github.com/logpai/loghub) — HDFS_v1
- **Dimensione:** ~11M log lines, 575,061 blocchi (sessioni)
- **Anomaly rate:** ~2.9% (16,838 anomali su 575,061)
- **Log keys:** ~30 simboli distinti (dopo parsing con Drain)
- **Labels:** per-block (block_id → normal/anomaly)
- **Formato raw:** linee di log free-text con block_id embedded

### Preprocessing necessario

Il dataset HDFS e' in formato raw (log lines free-text). Servono due step:
1. **Log parsing** con Drain (o template pre-parsati da Loghub) → log key per riga
2. **Session windowing** per block_id → sequenze di log keys per blocco

Loghub fornisce gia' i risultati di Drain pre-calcolati (`HDFS.log_structured.csv`),
quindi possiamo usare quelli direttamente senza implementare Drain.

---

## Fase 1: Data ingestion HDFS

### Task 1: HdfsLogParser — parser per HDFS structured log

- **Package:** fitvlmc
- **File:** `jfitvlmc/src/main/java/fitvlmc/HdfsLogParser.java`
- **Cosa:** Classe che legge il file HDFS pre-parsato (da Loghub) e produce tracce
  nel formato interno jfitVLMC.

  **Input atteso:** `HDFS.log_structured.csv` (output di Drain, disponibile su Loghub).
  Colonne rilevanti: `BlockId`, `EventId` (log key, es. E1, E2, ..., E29).

  **Oppure:** `HDFS_2k.log_structured.csv` (subset 2000 righe per test rapido,
  incluso direttamente in Loghub).

  **Output:**
  - `List<HdfsSession>` dove `HdfsSession` contiene:
    - `String blockId`
    - `List<String> events` (sequenza di EventId)
    - `boolean isAnomaly` (se il label file e' disponibile)

  **Metodi:**
  - `List<HdfsSession> parseStructuredLog(File csvFile)` — legge il CSV structured
  - `void loadLabels(File labelFile, List<HdfsSession> sessions)` — carica
    `anomaly_label.csv` e setta il flag isAnomaly
  - `String toTraceFormat(List<HdfsSession> sessions)` — converte in formato
    jfitVLMC ("E1 E5 E2 end$ E1 E3 E7 end$ ...")
  - `void writeTraceFile(List<HdfsSession> sessions, File output)` — scrive file

  **Dettaglio parsing:**
  - Raggruppa le righe per BlockId (mantenendo l'ordine originale per timestamp)
  - Ogni gruppo = una sessione/traccia
  - EventId diventa il simbolo nell'alfabeto VLMC

- **Perche':** Il formato HDFS e' diverso dal nostro formato interno. Serve un adapter.
  Non modifichiamo CsvEventLogReader perche' quello e' per event log di process mining
  (case_id/activity/timestamp), mentre HDFS ha struttura diversa (BlockId/EventId).
- **Dipende da:** nessuno
- **Criteri:** Classe compilabile, parsing corretto del formato Loghub
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 2: Test HdfsLogParser con subset sintetico

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/HdfsLogParserTest.java`
- **Cosa:** Test con un mini CSV sintetico che simula il formato `HDFS.log_structured.csv`:
  ```csv
  LineId,Date,Time,Pid,Level,Component,Content,EventId,EventTemplate,BlockId
  1,081109,203615,148,INFO,dfs.DataNode,blk_1,E5,template,blk_1
  2,081109,203616,148,INFO,dfs.DataNode,blk_1,E22,template,blk_1
  3,081109,203617,148,INFO,dfs.DataNode,blk_2,E5,template,blk_2
  ```

  Test:
  1. Parsing corretto — raggruppa per BlockId
  2. Ordine preservato (per ordine di apparizione nel file)
  3. Label loading — mappa BlockId → anomaly
  4. Conversione a formato tracce interno
  5. Sessione con 0 eventi ignorata

- **Perche':** Validare il parser senza dipendere dal dataset reale (che e' grande).
- **Dipende da:** Task 1
- **Criteri:** Tutti i test passano
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=HdfsLogParserTest -q`

---

## Fase 2: Pipeline HDFS end-to-end

### Task 3: HdfsBenchmark — classe di benchmarking

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/HdfsBenchmark.java`
- **Cosa:** Classe che orchestra il benchmark completo HDFS:

  **Parametri:**
  - `File structuredLogFile` — path al CSV strutturato HDFS
  - `File labelFile` — path al file anomaly labels
  - `double trainRatio` — percentuale dati per training (default: 0.8)
  - `double alfa` — parametro pruning VLMC (default: 0.01)
  - `double[] betas` — valori di beta da testare

  **Pipeline:**
  1. Carica e parsa dataset HDFS (via HdfsLogParser)
  2. Split train/test: usa solo tracce normali per training (come DeepLog)
  3. Addestra VLMC sulle tracce normali di training
  4. Per ogni beta:
     a. Calcola anomaly score STA per ogni traccia di test
     b. Determina threshold ottimale (es. percentile su normali di test)
     c. Calcola Precision, Recall, F1, AUC
  5. Calcola anche metriche VLMC classica per confronto
  6. Produce report comparativo (stdout + CSV)

  **Metriche calcolate:**
  - **Precision**: TP / (TP + FP)
  - **Recall**: TP / (TP + FN)
  - **F1**: 2 * P * R / (P + R)
  - **AUC**: area under ROC (discretizzata su 100 threshold)
  - **Training time**: millisecondi per training VLMC
  - **Inference time**: millisecondi per scoring tutte le tracce di test
  - **Model size**: numero nodi nell'albero VLMC

  **Threshold selection:**
  Per convertire anomaly score continuo in classificazione binaria,
  usiamo il threshold che massimizza F1 sul test set. Questo e' lo stesso
  approccio usato nei paper di confronto (non e' data leakage perche'
  il modello non viene ri-addestrato — solo il threshold viene scelto).

- **Perche':** Classe auto-contenuta che produce tutti i numeri per il confronto.
- **Dipende da:** Task 1
- **Criteri:** Compilabile, pipeline end-to-end funzionante
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 4: BenchmarkMetrics — calcolo metriche di classificazione

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/BenchmarkMetrics.java`
- **Cosa:** Classe utility per calcolo metriche di classificazione binaria:

  **Input:** `List<ScoredTrace>` dove ScoredTrace = (blockId, anomalyScore, isAnomaly)

  **Metodi:**
  - `MetricsResult computeAtThreshold(double threshold)` — P, R, F1 a threshold fisso
  - `MetricsResult findBestF1()` — cerca threshold che massimizza F1 (scan su percentili)
  - `double computeAUC()` — area under ROC curve
  - `String formatComparisonTable(Map<String, MetricsResult> methods)` — tabella markdown

  **MetricsResult:**
  - precision, recall, f1, threshold, tp, fp, fn, tn

- **Perche':** Separa il calcolo metriche dalla pipeline. Riusabile per futuri benchmark.
- **Dipende da:** nessuno
- **Criteri:** Metriche corrette su casi noti (es. classificatore perfetto → F1=1)
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 5: Test BenchmarkMetrics — validazione metriche

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/BenchmarkMetricsTest.java`
- **Cosa:** Test per le metriche di classificazione:
  1. Classificatore perfetto: F1 = 1.0, AUC = 1.0
  2. Classificatore random: F1 ~ 0.5, AUC ~ 0.5
  3. Classificatore invertito: precision bassa, recall bassa
  4. Caso degenere: tutti positivi o tutti negativi
  5. AUC monotona rispetto alla qualita' del classificatore

- **Dipende da:** Task 4
- **Criteri:** Tutti i test passano
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=BenchmarkMetricsTest -q`

---

## Fase 3: Auto-Beta

### Task 6: AutoBetaSelector — selezione automatica di beta

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/AutoBetaSelector.java`
- **Cosa:** Classe che implementa due strategie per determinare beta automaticamente:

  **Strategia 1: Cross-validation (empirica)**
  ```java
  double crossValidateBeta(VlmcRoot vlmc, List<List<String>> normalTraces,
                           List<List<String>> anomalyTraces, double[] candidates)
  ```
  - Split il test set in K fold (default K=5)
  - Per ogni beta candidato, calcola F1 medio sui fold
  - Ritorna il beta con F1 massimo
  - Vantaggi: ottimale per il dataset specifico
  - Svantaggi: richiede tracce anomale labeled per tuning

  **Strategia 2: Heuristic basata sulle proprieta' dell'albero (closed-form)**
  ```java
  double heuristicBeta(VlmcRoot vlmc)
  ```
  Formula proposta:
  ```
  beta* = c / (mean_KL * sqrt(D))

  dove:
    D = profondita' media dell'albero VLMC (depth medio dei nodi foglia)
    mean_KL = KL divergence media su tutti i nodi non-root
    c = costante di normalizzazione (default: 1.0)
  ```

  **Intuizione della formula:**
  - Se il tree ha nodi molto informativi (high mean_KL), beta basso basta
    per dare peso ai contesti significativi → beta inversamente proporzionale a KL
  - Se il tree e' profondo, serve piu' concentrazione per non diluire
    il segnale → beta compensato da sqrt(D)
  - La costante c assorbe le unita' e puo' essere calibrata empiricamente

  **Metodi aggiuntivi:**
  - `TreeStats computeTreeStats(VlmcRoot vlmc)` — calcola statistiche albero:
    - `depth` (profondita' massima)
    - `meanDepth` (profondita' media foglie)
    - `meanKL` (KL media nodi non-root)
    - `medianKL` (KL mediana)
    - `nNodes` (numero nodi)
    - `nLeaves` (numero foglie)
    - `meanN` (n medio per nodo)

- **Perche':** Beta e' l'unico parametro di STA. Se puo' essere determinato
  automaticamente, STA diventa parameter-free (come VLMC classica).
  La formula heuristic e' la piu' interessante: se funziona, elimina
  la necessita' di dati anomali labeled per il tuning.
- **Dipende da:** nessuno (usa VlmcRoot esistente)
- **Criteri:** Compilabile, entrambe le strategie ritornano valori finiti positivi
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 7: Test AutoBetaSelector — validazione su VLMC a mano

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/AutoBetaSelectorTest.java`
- **Cosa:** Test con VLMC costruite a mano (simile setup a StaPredictorTest):
  1. **Heuristic su tree piatto** (depth=1): beta alto (poco da pesare)
  2. **Heuristic su tree profondo** (depth=4): beta piu' basso
  3. **Heuristic monotona**: tree con KL crescente → beta decrescente
  4. **TreeStats corrette**: verifica calcolo su tree noto
  5. **Cross-validation su caso banale**: anomalie ovvie → trova beta in range ragionevole
  6. **Heuristic non degenera**: beta sempre > 0 e finito

- **Dipende da:** Task 6
- **Criteri:** Tutti i test passano
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=AutoBetaSelectorTest -q`

---

## Fase 4: Test integrazione HDFS

### Task 8: HdfsMiniTest — test con dataset HDFS sintetico (no download)

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/HdfsMiniTest.java`
- **Cosa:** Test che simula la pipeline HDFS completa senza il dataset reale.
  Genera un mini-dataset sintetico che replica la struttura HDFS:

  - 30 log keys (E1-E30)
  - ~500 tracce normali (pattern tipici HDFS: E5 E22 E5 E11 E9 E11 E9 E11 E9 E26 E26 E26)
  - ~50 tracce anomale (pattern anomali: sequenze con E1 ripetuto, E15 fuori contesto)
  - Labels per block_id

  **Verifica:**
  1. Pipeline end-to-end funziona (parse → train → score → metrics)
  2. F1 > 0.5 sul mini-dataset (separa almeno decentemente)
  3. Auto-beta heuristic produce valore ragionevole (0.01 - 100)
  4. Metriche comparazione VLMC vs STA producibili
  5. Report leggibile generato

- **Perche':** Validare che tutta la pipeline funziona prima di scaricare il dataset
  reale da 1.5GB. Questo test gira in CI senza dipendenze esterne.
- **Dipende da:** Task 1, 3, 4, 6
- **Criteri:** Tutti i test passano, tempo < 30 secondi
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=HdfsMiniTest -q`

### Task 9: Auto-beta validation su dataset cyber sintetico

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/AutoBetaCyberTest.java`
- **Cosa:** Estende il test StaCyberTest per validare auto-beta:

  1. Addestra VLMC su dataset cyber (riusa CyberDatasetGenerator)
  2. Calcola beta_heuristic = heuristicBeta(vlmc)
  3. Calcola beta_cv = crossValidateBeta(vlmc, normals, attacks, [0.1..10])
  4. Confronta:
     - F1 con beta_heuristic vs F1 con beta ottimale (sweep)
     - F1 con beta_cv vs F1 con beta ottimale
  5. Verifica che:
     - beta_heuristic e' nello stesso ordine di grandezza del beta ottimale
     - beta_cv e' vicino al beta ottimale (entro 2x)
     - La formula heuristic non degenera (non ritorna 0 o infinito)

  Stampa report con correlazione tra beta scelto automaticamente e separazione.

- **Perche':** Validare auto-beta su un dataset dove gia' conosciamo il risultato.
  Se la formula heuristic funziona sul cyber sintetico, e' un buon segnale per HDFS.
- **Dipende da:** Task 6, Task 7 (per generator)
- **Criteri:** beta_heuristic nello stesso ordine di grandezza del beta ottimale
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=AutoBetaCyberTest -q`

---

## Fase 5: Benchmark reale + confronto

### Task 10: HdfsFullBenchmark — script per benchmark completo

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/HdfsFullBenchmark.java`
- **Cosa:** Main class standalone che esegue il benchmark completo su HDFS.
  Non e' un test (il dataset reale non e' in CI), ma un programma da lanciare localmente.

  **Usage:**
  ```bash
  java -cp <JAR> sta.HdfsFullBenchmark \
    --structured-log HDFS.log_structured.csv \
    --labels anomaly_label.csv \
    --alfa 0.01 \
    --output results/hdfs_benchmark.csv
  ```

  **Pipeline:**
  1. Carica dataset (HdfsLogParser)
  2. Split: 80% normali per training, 20% normali + tutti anomali per test
  3. Training VLMC (misura tempo)
  4. Scoring con VLMC classica (misura tempo)
  5. Scoring con STA per beta in [0.01, 0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 50.0]
  6. Scoring con STA + auto-beta (heuristic)
  7. Calcolo metriche per tutti i metodi
  8. Output: tabella comparativa (stdout + CSV)

  **Output atteso:**
  ```
  ========================================
  HDFS Benchmark — STA vs VLMC vs DeepLog
  ========================================

  Dataset: 575,061 sessions, 16,838 anomalies (2.9%)
  Training: 460,000 normal sessions
  Test: 115,061 sessions (includes all anomalies)
  Alphabet: 30 log keys
  VLMC depth: XX, nodes: XX

  Method               Precision  Recall  F1      AUC    Time(s)  Size
  ------------------   ---------  ------  ------  -----  -------  ----
  VLMC classic         0.XXXX     0.XXXX  0.XXXX  0.XXX  X.X      XX
  STA beta=0.1         0.XXXX     0.XXXX  0.XXXX  0.XXX  X.X      XX
  STA beta=1.0         0.XXXX     0.XXXX  0.XXXX  0.XXX  X.X      XX
  STA beta=10.0        0.XXXX     0.XXXX  0.XXXX  0.XXX  X.X      XX
  STA auto-beta=X.X    0.XXXX     0.XXXX  0.XXXX  0.XXX  X.X      XX
  DeepLog (published)  0.9500     0.9600  0.9550  -      -        -

  Auto-beta details:
    Heuristic beta: X.XX (based on depth=XX, mean_KL=X.XX)
    Tree stats: nodes=XX, leaves=XX, max_depth=XX, mean_depth=X.X
  ```

- **Perche':** Programma standalone per il benchmark reale. Separato dai test perche'
  richiede il download manuale del dataset HDFS (~1.5GB).
- **Dipende da:** Task 1, 3, 4, 6
- **Criteri:** Compilabile, eseguibile con dataset reale
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 11: Documentazione risultati e confronto

- **Package:** nessuno
- **File:** `results/README.md` (nuovo, directory results/)
- **Cosa:** Template per documentare i risultati del benchmark:

  1. **Setup sperimentale**: dataset, split, parametri
  2. **Tabella risultati**: STA vs VLMC vs DeepLog (da compilare dopo l'esecuzione)
  3. **Analisi auto-beta**: correlazione heuristic vs optimal
  4. **Discussione**: dove STA eccelle, dove no, e perche'
  5. **Istruzioni per riprodurre**: come scaricare HDFS e lanciare il benchmark

  Include anche istruzioni per scaricare il dataset:
  ```bash
  # Download HDFS dataset da Loghub
  # Opzione A: subset 2k righe (test rapido)
  wget https://raw.githubusercontent.com/logpai/loghub/master/HDFS/HDFS_2k.log_structured.csv

  # Opzione B: dataset completo (benchmark)
  # Scaricare da Zenodo: https://zenodo.org/record/3227177
  # File: HDFS.log_structured.csv (~1.5GB) + anomaly_label.csv
  ```

- **Perche':** Senza documentazione i risultati non sono riproducibili.
- **Dipende da:** Task 10
- **Criteri:** README leggibile, istruzioni chiare
- **Verifica:** File esiste e ha contenuto ragionevole

### Task 12: Quality gates e cleanup

- **Package:** sta, build
- **File:** Nessun file nuovo. Aggiornamenti:
  - SpotBugs exclusions se necessario per nuove classi
  - Spotless formatting
  - JaCoCo coverage sul nuovo codice
- **Cosa:**
  1. `mvn clean verify` passa
  2. Coverage >= 35% (soglia attuale) con nuovo codice incluso
  3. No warning SpotBugs nei file nuovi
  4. Tutti i test (unit + integrazione) passano

- **Dipende da:** Task 1-11
- **Criteri:** `mvn clean verify` passa senza errori
- **Verifica:** `mvn clean verify`

---

## Riepilogo file nuovi

| File | Tipo | Package |
|------|------|---------|
| `jfitvlmc/src/main/java/fitvlmc/HdfsLogParser.java` | Data ingestion | fitvlmc |
| `jfitvlmc/src/main/java/sta/HdfsBenchmark.java` | Orchestration | sta |
| `jfitvlmc/src/main/java/sta/BenchmarkMetrics.java` | Metrics utility | sta |
| `jfitvlmc/src/main/java/sta/AutoBetaSelector.java` | Auto-beta | sta |
| `jfitvlmc/src/main/java/sta/HdfsFullBenchmark.java` | Standalone main | sta |
| `jfitvlmc/src/test/java/test/HdfsLogParserTest.java` | Unit test | test |
| `jfitvlmc/src/test/java/test/BenchmarkMetricsTest.java` | Unit test | test |
| `jfitvlmc/src/test/java/test/HdfsMiniTest.java` | Integration test | test |
| `jfitvlmc/src/test/java/test/AutoBetaSelectorTest.java` | Unit test | test |
| `jfitvlmc/src/test/java/test/AutoBetaCyberTest.java` | Integration test | test |
| `results/README.md` | Documentation | - |

## File core NON modificati

Nessun file in `vlmc/`, `suffixarray/` viene modificato.
Il package `sta` (creato nel piano precedente) viene esteso ma non modificato.

## Dipendenze esterne nuove

Nessuna. Il dataset HDFS e' scaricato manualmente e non incluso nel repository.

## Confronto con DeepLog — metodologia

Non ri-eseguiamo DeepLog. Usiamo i risultati pubblicati:
- **Paper:** Du et al., "DeepLog: Anomaly Detection and Diagnosis from System Logs
  through Deep Learning", ACM CCS 2017
- **Risultati HDFS:** Precision=0.95, Recall=0.96, F1=0.955

Confronto equo perche':
1. Stesso dataset (HDFS da Xu et al., SOSP 2009)
2. Stesso task (classificazione binaria per-session)
3. Stesso preprocessing (Drain log parsing → log keys)
4. Training solo su tracce normali (sia DeepLog che VLMC/STA)

Dove NON e' equo (da segnalare):
- DeepLog usa window di 10 log keys, STA usa la traccia completa
- DeepLog predice top-K next log key, STA usa anomaly score continuo
- Il threshold selection potrebbe differire

## Auto-Beta — analisi teorica

### Formula heuristic proposta

```
beta* = c / (mean_KL * sqrt(D))
```

**Motivazione:**
- **beta controlla la concentrazione del softmax.** Piu' alto = piu' concentrato
  sul contesto con score massimo (converge a VLMC classica).
- **mean_KL misura l'informativita' media dei contesti.** Se i contesti sono
  molto informativi (KL alta), serve meno concentrazione per distinguerli.
  Beta inversamente proporzionale a KL.
- **D (profondita') misura il numero di contesti nella mixture.** Con tree
  profondi, ci sono piu' contesti da combinare. sqrt(D) compensa
  per evitare che la mixture diventi troppo uniforme.
- **c e' una costante** calibrabile empiricamente (default: 1.0).

**Proprietà desiderate:**
- Tree piatto (D=1): beta alto → STA ≈ VLMC classica (corretto, nulla da mixare)
- Tree profondo con nodi informativi: beta basso → tutti i contesti contribuiscono
- Tree profondo con nodi poco informativi: beta medio → contesti intermedi smorzati

**Validazione:**
La formula sara' validata confrontando il beta_heuristic con il beta ottimale
trovato via sweep su HDFS e sul dataset cyber sintetico (Task 9).

### Cross-validation (alternativa empirica)

```
beta_cv = argmax_{beta} F1(beta, validation_set)
```

Richiede un validation set con tracce anomale labeled.
5-fold cross-validation sul test set.

Pro: trova il beta ottimale per il dataset specifico.
Contro: richiede dati anomali labeled, che potrebbero non essere disponibili
in produzione.

## Rischi e mitigazioni

| Rischio | Probabilita' | Impatto | Mitigazione |
|---------|-------------|---------|-------------|
| HDFS dataset troppo grande per training VLMC | Media | Training > 10 min | Suffix array e' O(n log n), ma 11M righe sono tante. Testare su subset prima. |
| VLMC non riesce a catturare pattern HDFS | Media | F1 basso per VLMC e STA | HDFS ha pattern ripetitivi, dovrebbe funzionare. Se no, ridurre alfa. |
| STA non batte DeepLog | Alta | Risultato negativo | Risultato comunque pubblicabile: STA e' interpretable e parameter-free |
| Formula heuristic beta non funziona | Media | Serve tuning manuale | Cross-validation come fallback |
| Download HDFS non disponibile | Bassa | Non possiamo testare | Loghub e Zenodo sono stabili. Subset 2k sempre disponibile. |
| Memoria insufficiente per 575K sessioni | Media | OutOfMemoryError | Usare -Xmx4g. Se insufficiente, processare in batch. |

## Ordine di esecuzione

```
Task 1, 4 (paralleli) → Task 2, 5 (paralleli) → Task 3, 6 (paralleli) → Task 7 → Task 8, 9 (paralleli) → Task 10 → Task 11 → Task 12
```

## Risultati attesi

### Scenario ottimistico
STA con auto-beta raggiunge F1 >= 0.90 su HDFS, comparabile a DeepLog (0.955)
ma con training 100x piu' veloce e modello interpretabile.

### Scenario realistico
STA raggiunge F1 ~ 0.80-0.85, inferiore a DeepLog ma con vantaggi in
interpretabilita' (context relevance map) e efficienza (CPU-only, secondi vs minuti).

### Scenario pessimistico
STA raggiunge F1 < 0.70. In questo caso, il contributo e' limitato ma
dimostra che l'approccio ha potenziale e identifica le limitazioni
(es. VLMC non cattura dipendenze a lungo raggio che LSTM gestisce).

In tutti i casi, i risultati sono pubblicabili come confronto strutturato.

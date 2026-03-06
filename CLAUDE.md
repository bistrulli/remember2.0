# CLAUDE.md — jfitVLMC Project

## Panoramica

**jfitVLMC** (REMEMBER) e' un'implementazione Java di algoritmi di apprendimento per Variable Length Markov Chain (VLMC). Apprende modelli di Markov adattivi da tracce di esecuzione (formato interno o CSV event log), supporta simulazione, predizione, likelihood analysis con uEMSC (stochastic conformance checking), generazione automatica di modelli ECF, e anomaly detection tramite STA (Stochastic Tree Attention) con Online Bayesian Model Averaging.

## Java Environment (CRITICAL)

**Java 17 e Maven 3.x sono richiesti.**

```bash
# Verifica versioni
java -version    # deve essere 17+
mvn -version     # deve essere 3.x

# Compilazione (dal root, compila entrambi i moduli)
mvn compile

# Compilazione + test
mvn test

# Build fat JAR (con tutte le dipendenze)
mvn package -DskipTests

# Build completo con quality gates
mvn clean verify
```

### Regole

1. **Target Java 17** — non usare feature di versioni successive
2. **Maven e' il build system** — non usare gradle o altri tool
3. **Multi-module project** — parent POM (`vlmc-parent`) con moduli `ecf/` e `jfitvlmc/`
4. **Fat JAR** via maven-assembly-plugin: `jfitvlmc/target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar`
5. **Main class**: `fitvlmc.fitVlmc`

## Architettura Multi-Modulo

Il progetto e' un reactor Maven con due moduli:

| Modulo | artifactId | Descrizione |
|--------|-----------|-------------|
| `ecf/` | `ecf` | Parser ECF (ANTLR) e strutture dati (Flow, Edge, ECFListener) |
| `jfitvlmc/` | `jfitvlmc` | Core: learning VLMC, simulazione, predizione, STA, benchmark, REST API |

`jfitvlmc` dipende da `ecf` via `${project.version}`. Non serve installazione separata — Maven risolve automaticamente i moduli fratelli nel reactor build.

## Struttura Progetto

```
jfitVLMC/
├── pom.xml                                  # Parent POM (vlmc-parent), multi-module
├── spotbugs-exclude.xml                     # SpotBugs exclusion (root)
├── ecf/                                     # Modulo ECF
│   ├── pom.xml                              # ECF module POM
│   ├── spotbugs-exclude.xml                 # SpotBugs exclusion (ECF)
│   └── src/main/java/
│       ├── ECFEntity/                       # ECF data structures
│       │   ├── Flow.java                    # Flow graph: nodi, edges, navigazione
│       │   ├── Edge.java                    # Edge: source, target, label
│       │   └── ECFListener.java             # ANTLR listener: costruisce Flow da parsing
│       └── antlr/                           # ANTLR generated (auto-generated, do not edit)
│           ├── ECFParser.java
│           ├── ECFLexer.java
│           ├── ECFListener.java
│           └── ECFBaseListener.java
├── jfitvlmc/                                # Modulo principale
│   ├── pom.xml                              # jfitvlmc module POM
│   ├── spotbugs-exclude.xml                 # SpotBugs exclusion (jfitvlmc)
│   └── src/
│       ├── main/java/
│       │   ├── TestEcfGenerator.java        # (default pkg) CLI per generare ECF da tracce
│       │   ├── fitvlmc/                     # Core: learning, execution, REST, HDFS parsing
│       │   │   ├── fitVlmc.java             # Main entry point (CLI + learning)
│       │   │   ├── EcfNavigator.java        # ECF traversal, VLMC tree construction
│       │   │   ├── Trace2EcfIntegrator.java # Auto ECF generation from traces
│       │   │   ├── CsvEventLogReader.java   # CSV event log parser (process mining format)
│       │   │   ├── RESTVlmc.java            # HTTP handler for prediction API (GET /?ctx=)
│       │   │   ├── HdfsLogParser.java       # HDFS structured CSV parser + label loader
│       │   │   ├── HdfsRawLogParser.java    # HDFS raw log parser (11M+ lines, regex-based)
│       │   │   ├── GenerateEcfToFile.java   # ECF file output utility (standalone main)
│       │   │   ├── SelfLoopRemover.java     # Self-loop removal from ECF graphs (standalone main)
│       │   │   ├── DebugEcfComparison.java  # Debug utility per analisi ECF (standalone main)
│       │   │   └── TestAutoEcfGeneration.java # Test manuale generazione ECF (standalone main)
│       │   ├── vlmc/                        # VLMC data structures
│       │   │   ├── VlmcRoot.java            # Root node, tree operations, serialization, simulation
│       │   │   ├── VlmcNode.java            # Tree node: label, distribution, pruning, KL cache, clone
│       │   │   ├── NextSymbolsDistribution.java # Probability distribution + sampling
│       │   │   └── vlmcWalker.java          # Visitor pattern interface (functional)
│       │   ├── sta/                         # STA: Stochastic Tree Attention + anomaly detection
│       │   │   ├── StaPredictor.java        # Core: static predict + Online BMA (predictOnline)
│       │   │   ├── StaResult.java           # Prediction result: mixed distribution + contributions
│       │   │   ├── ContextContribution.java # Per-context weight, KL, depth, distribution
│       │   │   ├── StaWeightFunction.java   # Functional interface for scoring (klBased)
│       │   │   ├── StaAnomalyScorer.java    # Trace scoring: static + online, CSV/report output
│       │   │   ├── AutoBetaSelector.java    # Heuristic + cross-validation beta selection
│       │   │   ├── BenchmarkMetrics.java    # P/R/F1, threshold search, AUC, comparison tables
│       │   │   ├── HdfsBenchmark.java       # HDFS benchmark: train/score VLMC vs STA vs BMA
│       │   │   └── HdfsFullBenchmark.java   # CLI entry point for HDFS benchmark (--eta)
│       │   └── suffixarray/                 # Pattern matching
│       │       ├── SuffixArray.java         # String suffix array (Princeton-based)
│       │       └── SuffixArrayInt.java      # Integer variant with LCP support
│       └── test/java/test/
│           ├── CsvEventLogReaderTest.java   # JUnit 5: CSV parsing, ordering, separators (10 test)
│           ├── LikelihoodTest.java          # JUnit 5: likelihood, getState, DFS (11 test)
│           ├── EcfNavigatorTest.java        # JUnit 5: ECF navigation and VLMC construction (3 test)
│           ├── PruningTest.java             # JUnit 5: statistical pruning chi-square (11 test)
│           ├── RESTVlmcTest.java            # JUnit 5: REST API handler (4 test)
│           ├── EndToEndTest.java            # JUnit 5: end-to-end pipeline (4 test, tag: e2e)
│           ├── UemscTest.java               # JUnit 5: uEMSC stochastic conformance (5 test)
│           ├── VlmcNavigationTest.java      # JUnit 5: VLMC tree navigation (7 test)
│           ├── VlmcSerializationTest.java   # JUnit 5: VLMC model save/load (4 test)
│           ├── VlmcNodeKLCacheTest.java     # JUnit 5: KL divergence caching (7 test)
│           ├── NextSymbolsDistributionTest.java # JUnit 5: probability distribution (6 test)
│           ├── SuffixArrayTest.java         # JUnit 5: suffix array operations (9 test)
│           ├── SuffixArrayIntTest.java      # JUnit 5: integer suffix array (8 test)
│           ├── Trace2EcfIntegratorTest.java # JUnit 5: ECF generation from traces (9 test)
│           ├── StaPredictorTest.java        # JUnit 5: STA static predict, contexts, weights (12 test)
│           ├── StaWeightFunctionTest.java   # JUnit 5: weight function scoring (5 test)
│           ├── OnlineBmaTest.java           # JUnit 5: Online BMA convergence, normalization (10 test)
│           ├── AutoBetaSelectorTest.java    # JUnit 5: beta heuristic and cross-validation (6 test)
│           ├── BenchmarkMetricsTest.java    # JUnit 5: P/R/F1 metrics computation (6 test)
│           ├── HdfsLogParserTest.java       # JUnit 5: HDFS log parsing (6 test)
│           ├── HdfsMiniTest.java            # JUnit 5: HDFS mini benchmark (4 test, tag: e2e)
│           ├── StaCyberTest.java            # JUnit 5: STA on cyber dataset (4 test, tag: e2e)
│           ├── AutoBetaCyberTest.java       # JUnit 5: auto-beta on cyber dataset (3 test, tag: e2e)
│           ├── CyberDatasetGenerator.java   # Test utility: generates synthetic cyber traces
│           ├── SaTest.java                  # Suffix array test (hardcoded path, legacy)
│           └── SaTestInt.java               # Integer suffix array test (legacy)
├── plan/                                    # Piani di implementazione
├── .github/
│   └── workflows/
│       └── ci.yml                           # GitHub Actions: compile + test + quality gates
└── .claude/                                 # Configurazione agenti Claude
    ├── settings.json                        # Permessi (git, mvn, java) e hooks
    ├── commands/                             # Slash commands (14 agenti)
    │   ├── orchestrate.md                   # Pipeline end-to-end con git (branch, commit, CI)
    │   ├── plan.md                          # Pianificazione task strutturati
    │   ├── implement.md                     # Implementazione singolo task
    │   ├── auto.md                          # Pipeline rapida 1-3 file
    │   ├── review.md                        # Code review strutturata
    │   ├── test.md                          # Testing agent
    │   ├── detect-bs.md                     # LLM bullshit detector (CAT-1..CAT-7)
    │   ├── sync-docs.md                     # Sincronizzazione documentazione
    │   ├── prepare.md                       # Setup ambiente
    │   ├── setup-env.md                     # Verifica prerequisiti
    │   ├── ci-check.md                      # CI check agent
    │   ├── cleanup-plans.md                 # Cleanup plan files
    │   ├── cleanup-branches.md              # Cleanup stale branches
    │   └── expert.md                        # Probabilistic model expert
    └── skills/                              # Conoscenza specializzata
        ├── build/SKILL.md                   # Build patterns Maven
        ├── testing/SKILL.md                 # Testing patterns
        └── probabilistic/SKILL.md           # Probabilistic models knowledge
```

## Componenti Chiave

| Classe | Package | Responsabilita |
|--------|---------|---------------|
| `fitVlmc` | `fitvlmc` | Entry point CLI (20 opzioni), modalita learning/prediction/REST/likelihood |
| `EcfNavigator` | `fitvlmc` | Navigazione ECF, costruzione albero VLMC, pruning statistico (chi-square) |
| `Trace2EcfIntegrator` | `fitvlmc` | Generazione automatica ECF `Flow` da tracce raw (split su spazi, reset su `end$`) |
| `CsvEventLogReader` | `fitvlmc` | Parser CSV event log (case_id, activity, timestamp). Auto-detection formato. |
| `RESTVlmc` | `fitvlmc` | HTTP handler: `GET /?ctx=state1-state2-state3` ritorna distribuzione next symbol |
| `HdfsLogParser` | `fitvlmc` | Parser HDFS structured CSV, label loading, trace file writing |
| `HdfsRawLogParser` | `fitvlmc` | Parser HDFS raw log (11M+ lines, regex BlockId extraction) |
| `GenerateEcfToFile` | `fitvlmc` | Utility standalone: genera ECF da file tracce e salva su file |
| `SelfLoopRemover` | `fitvlmc` | Utility standalone: rimuove self-loop (A->A) da ECF, preserva cicli (A->B->A) |
| `DebugEcfComparison` | `fitvlmc` | Utility standalone: analisi dettagliata ECF (self-loop, 2-cycle, struttura) |
| `VlmcRoot` | `vlmc` | Nodo radice: simulazione, likelihood, parsing VLMC da file, DFS traversal |
| `VlmcNode` | `vlmc` | Nodo albero: label, distribuzione, pruning (KL con cache), clone, context extraction |
| `NextSymbolsDistribution` | `vlmc` | Distribuzione probabilita next symbol con sampling (EnumeratedDistribution) |
| `vlmcWalker` | `vlmc` | Interfaccia funzionale visitor pattern per DFS |
| `StaPredictor` | `sta` | Core STA: static predict (softmax su KL-based scores) + Online BMA (predictOnline con fixed-share) |
| `StaResult` | `sta` | Risultato predizione: distribuzione mixata + contributi per contesto + anomaly score |
| `ContextContribution` | `sta` | Contributo singolo contesto: peso, KL, profondita, distribuzione |
| `StaWeightFunction` | `sta` | Interfaccia funzionale per scoring contesti (default: `log(n) * KL`) |
| `StaAnomalyScorer` | `sta` | Scoring tracce: statico (scoreTrace) + online BMA (scoreTraceOnline), output CSV/report |
| `AutoBetaSelector` | `sta` | Selezione beta: euristica su tree stats + cross-validation |
| `BenchmarkMetrics` | `sta` | Metriche: P/R/F1 a threshold, ricerca best F1, AUC, tabelle confronto |
| `HdfsBenchmark` | `sta` | Benchmark HDFS: train VLMC, score con VLMC/STA/BMA, report comparativo |
| `HdfsFullBenchmark` | `sta` | CLI entry point benchmark HDFS (--raw-log, --labels, --eta, --vlmc-model, etc.) |
| `SuffixArray` | `suffixarray` | Suffix array su stringhe: count, first/last occurrence via binary search |
| `SuffixArrayInt` | `suffixarray` | Suffix array su `ArrayList<Integer>` con supporto LCP (Kasai) |
| `Flow` | `ECFEntity` | (modulo ecf) Grafo flow: nodi, edges, navigazione |
| `Edge` | `ECFEntity` | (modulo ecf) Edge: source, target, label |
| `ECFListener` | `ECFEntity` | (modulo ecf) ANTLR listener: costruisce Flow da parsing |

## STA — Stochastic Tree Attention

Il package `sta` implementa il meccanismo di predizione basato su mixture di contesti VLMC a profondita variabile:

### Static Predict (`StaPredictor.predict`)
- Raccoglie i contesti matched dalla radice in giu (root + nodi a profondita crescente)
- Calcola pesi softmax su score `log(max(1,n)) * KL_divergence` (parametro beta)
- Mixa le distribuzioni dei contesti pesandole

### Online BMA (`StaPredictor.predictOnline`)
- Aggiornamento Bayesiano online dei pesi ad ogni step della traccia
- Formula: `w_i <- (1-eta) * w_i * P_i(s_t) / P_mix(s_t) + eta/K`
- Fixed-share (Herbster & Warmuth 1998): eta > 0 previene weight death
- Epsilon floor (default 1e-10) previene P=0 irreversibile
- Gestisce cambi del context set (nuovi contesti, contesti persi)

### HDFS Benchmark
- `HdfsFullBenchmark` CLI: `--raw-log`, `--labels`, `--eta`, `--vlmc-model`, `--save-vlmc`
- Confronta VLMC classic vs STA statico vs Online BMA per ogni valore di beta
- Output: tabella P/R/F1 + CSV report

## CLI Reference

Alias: `java -jar jfitvlmc/target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar`

### Tutte le opzioni (fitVlmc)

| Opzione | Tipo | Descrizione |
|---------|------|-------------|
| `--ecf <file>` | REQUIRED_ARG | File ECF model (opzionale, auto-generato se assente) |
| `--infile <file>` | REQUIRED_ARG | File tracce di input (richiesto per learning) |
| `--outfile <file>` | REQUIRED_ARG | File output tracce simulate (default: `simulation_output.mat`) |
| `--vlmcfile <file>` | REQUIRED_ARG | File output modello VLMC (default: `model.vlmc`) |
| `--nsim <int>` | REQUIRED_ARG | Numero simulazioni da generare (default: 1) |
| `--ntime <int>` | REQUIRED_ARG | Parametro tempo per processing (default: 1) |
| `--alfa <float>` | REQUIRED_ARG | Alpha per pruning statistico (richiesto per learning) |
| `--vlmc <file>` | REQUIRED_ARG | Carica modello VLMC pre-addestrato |
| `--initCtx <ctx>` | REQUIRED_ARG | Contesto iniziale per predizione (separato da spazi) |
| `--lik <file>` | REQUIRED_ARG | Calcola likelihood + uEMSC per tracce nel file |
| `--rnd` | NO_ARG | Genera VLMC randomizzata da modello esistente |
| `--pred` | NO_ARG | Modalita predizione (richiede `--initCtx`) |
| `--pred_rest <port>` | REQUIRED_ARG | Avvia REST API su porta specificata (1-65535) |
| `--maxdepth <int>` | REQUIRED_ARG | Profondita max navigazione ECF (default: 25) |
| `--ecfoutfile <file>` | REQUIRED_ARG | Salva ECF auto-generato su file |
| `--csv-case <name>` | REQUIRED_ARG | Colonna case identifier nel CSV (default: `case_id`) |
| `--csv-activity <name>` | REQUIRED_ARG | Colonna activity nel CSV (default: `activity`) |
| `--csv-timestamp <name>` | REQUIRED_ARG | Colonna timestamp nel CSV (default: `timestamp`) |
| `--csv-separator <char>` | REQUIRED_ARG | Separatore campi CSV (default: `,`) |
| `-h, --help` | NO_ARG | Mostra help |

### Opzioni HdfsFullBenchmark

| Opzione | Tipo | Descrizione |
|---------|------|-------------|
| `--raw-log <path>` | REQUIRED_ARG | HDFS.log (raw log, parsed automatically) |
| `--structured-log <path>` | REQUIRED_ARG | HDFS.log_structured.csv (alternative) |
| `--labels <path>` | REQUIRED_ARG | anomaly_label.csv |
| `--alfa <value>` | REQUIRED_ARG | Pruning alpha (default: 0.01) |
| `--eta <value>` | REQUIRED_ARG | BMA fixed-share parameter (default: 0.05) |
| `--output <path>` | REQUIRED_ARG | Output CSV path |
| `--vlmc-model <path>` | REQUIRED_ARG | Load pre-trained VLMC (skip training) |
| `--save-vlmc <path>` | REQUIRED_ARG | Save trained VLMC model to file |

### Bug noti

- **`--pred` senza `--ecf`**: `--vlmc model.vlmc --pred --initCtx "A"` crasha con NPE perche' tenta di generare ECF anche in prediction mode. Workaround: fornire anche `--ecf`. Issue: [#8](https://github.com/bistrulli/remember2.0/issues/8)

### Esempi

```bash
# Learning da formato interno
java -jar <JAR> --infile traces.txt --vlmcfile model.vlmc --alfa 0.05 --nsim 1000

# Learning da CSV event log
java -jar <JAR> --infile data.csv --csv-case idars --csv-activity activity \
  --csv-timestamp timestamp --vlmcfile model.vlmc --alfa 0.05 --nsim 1

# Likelihood + uEMSC (stochastic conformance)
java -jar <JAR> --vlmc model.vlmc --lik data.csv --csv-case idars \
  --csv-activity activity --csv-timestamp timestamp
# Output: .lik (per-trace), .lik.prefix (per-prefix con contesto), stdout (aggregati + uEMSC)

# Predizione singola (richiede --ecf, vedi bug noti)
java -jar <JAR> --vlmc model.vlmc --ecf model.ecf --pred --initCtx "state1 state2"

# REST server
java -jar <JAR> --vlmc model.vlmc --pred_rest 8080
# Query: GET http://localhost:8080/?ctx=state1-state2-state3

# HDFS Benchmark (anomaly detection)
java -cp <JAR> sta.HdfsFullBenchmark --raw-log HDFS.log --labels anomaly_label.csv \
  --eta 0.05 --save-vlmc model.vlmc --output results.csv
```

### Output `--lik`

Il comando `--lik` produce tre output:

1. **`<basename>.lik`** — per-trace CSV: `trace_id,trace_length,likelihood,log_likelihood`
2. **`<basename>.lik.prefix`** — per-prefix CSV: `trace_id,prefix_length,likelihood,prefix,next_activity,possible_activities`
3. **stdout** — statistiche aggregate:
   ```
   === LIKELIHOOD ANALYSIS ===
   Total traces: N
   Traces with non-zero likelihood: N
   Aggregate log-likelihood: -X.XX
   Distinct traces: N
   uEMSC (stochastic conformance): 0.XXXX
   ```

La **uEMSC** (unit Earth Mover's Stochastic Conformance, Leemans et al. BPM 2019) misura la conformance tra il linguaggio stocastico del log e quello del modello VLMC. Valore in [0, 1]: 1 = conformance perfetta.

## Convenzioni Codice

- **Naming**: `CamelCase` per classi, `camelCase` per metodi/variabili
- **Package**: `fitvlmc` (core), `vlmc` (data structures), `sta` (prediction/anomaly), `suffixarray` (algorithms), `ECFEntity` (modulo ecf)
- **Error handling**: eccezioni checked per I/O, unchecked per errori di programmazione
- **Logging**: `java.util.logging` per debug/progress (non System.out.println). CLI user output resta su stdout.
- **Nessun framework DI** — istanziazione diretta
- **CLI**: java-getopt per parsing argomenti (fitVlmc), manual switch-case (HdfsFullBenchmark)
- **Statistiche**: Apache Commons Math 3.6.1 per chi-square e distribuzioni
- **Parsing**: ANTLR 4.7.2 runtime per parsing ECF (nel modulo ecf)
- **Utilities**: Apache Commons Lang 3.12.0 per StringUtils/ArrayUtils, jfiglet 0.0.9 per banner ASCII
- **HTTP**: `com.sun.net.httpserver` (JDK built-in) per REST API
- **Testing**: JUnit Jupiter 5.10.1 + maven-surefire-plugin 3.2.5 (unit) + maven-failsafe-plugin 3.2.5 (e2e)
- **Formatting**: Spotless con Google Java Format (stile AOSP), ratchet da `origin/main`
- **Coverage**: JaCoCo con minimo 35% line coverage
- **Static analysis**: SpotBugs (effort Max, threshold High, failOnError true)

## Git Workflow

Repository: `https://github.com/bistrulli/remember2.0.git`

### Branch strategy

- `main` — branch stabile, protetto
- `feat/<feature-name>` — feature branch (una per piano)
- `fix/<descrizione>` — bugfix branch

### Commit convention

```
<type>(<package>): <titolo> (#N/M)

<una riga di spiegazione del perche'>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
Packages: `fitvlmc`, `vlmc`, `sta`, `suffixarray`, `ci`, `build`

### CI Pipeline

GitHub Actions (`.github/workflows/ci.yml`):
- **compile** — `mvn compile` con JDK 17
- **test** — `mvn test` (dipende da compile)
- **quality** — Spotless check, SpotBugs, JaCoCo coverage (dipende da compile)

Triggerato su push a `main` e su pull request verso `main`.

### Testing

- **Unit test**: `mvn test` — 150 test (esegue tutti i test TRANNE quelli taggati `e2e`)
- **E2E test**: `mvn verify` — include 4 test taggati `e2e` via Failsafe plugin
- **Totale**: 154 test (150 unit + 4 e2e)
- **Tag e2e**: usare `@Tag("e2e")` di JUnit 5 per test end-to-end

### Workflow agentico con git

L'agente `/orchestrate` segue questo flusso:
1. Crea feature branch da `main`
2. Crea draft PR per attivare CI
3. Per ogni task: implementa, compila, testa, committa, pusha
4. Self-review su `git diff main..HEAD`
5. Aspetta CI pass (max 3 retry)
6. Report finale

## Comandi Rapidi

```bash
# Compila e testa (dal root)
mvn clean test

# Solo compila (veloce)
mvn compile

# Build fat JAR
mvn clean package -DskipTests

# Build completo con quality gates
mvn clean verify

# Quality checks separati
mvn spotless:check          # Formatting
mvn compile spotbugs:check  # Static analysis
mvn verify                  # Coverage (JaCoCo)

# Applica formatting automatico
mvn spotless:apply

# Esegui main class
mvn -pl jfitvlmc exec:java -Dexec.mainClass="fitvlmc.fitVlmc" -Dexec.args="--help"

# Esegui da JAR
java -jar jfitvlmc/target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help

# Git: stato branch
git log --oneline main..HEAD
git diff main..HEAD --stat
```

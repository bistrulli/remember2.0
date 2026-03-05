# CLAUDE.md — jfitVLMC Project

## Panoramica

**jfitVLMC** (REMEMBER) e' un'implementazione Java di algoritmi di apprendimento per Variable Length Markov Chain (VLMC). Apprende modelli di Markov adattivi da tracce di esecuzione (formato interno o CSV event log), supporta simulazione, predizione, likelihood analysis con uEMSC (stochastic conformance checking) e generazione automatica di modelli ECF.

## Java Environment (CRITICAL)

**Java 17 e Maven 3.x sono richiesti.**

```bash
# Verifica versioni
java -version    # deve essere 17+
mvn -version     # deve essere 3.x

# Compilazione
mvn compile

# Compilazione + test
mvn test

# Build fat JAR (con tutte le dipendenze)
mvn package -DskipTests

# Build completo
mvn clean package
```

### Regole

1. **Target Java 17** — non usare feature di versioni successive
2. **Maven e' il build system** — non usare gradle o altri tool
3. **Fat JAR** via maven-assembly-plugin: `target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar`
4. **Main class**: `fitvlmc.fitVlmc`

## Dipendenza ECF (IMPORTANTE)

Il progetto dipende dal modulo ECF (`it.sysma.vlmc:ecf:1.0.0-SNAPSHOT`), un progetto separato che fornisce le classi per Electronic Control Flow. Questa dipendenza:
- **NON e' su Maven Central** — deve essere installata nel repository Maven locale
- Si trova tipicamente in `/Users/emilio-imt/Desktop/vlmc/ECF/`
- Per installarla: `cd <ecf-project-dir> && mvn install`
- Se la build fallisce con "Could not resolve dependencies... ecf", la dipendenza ECF non e' installata

## Struttura Progetto

```
jfitVLMC/
├── src/
│   ├── main/java/
│   │   ├── TestEcfGenerator.java         # (default pkg) CLI per generare ECF da tracce
│   │   ├── fitvlmc/                      # Core: learning, execution, REST
│   │   │   ├── fitVlmc.java              # Main entry point (CLI + learning)
│   │   │   ├── EcfNavigator.java         # ECF traversal, VLMC tree construction
│   │   │   ├── Trace2EcfIntegrator.java  # Auto ECF generation from traces
│   │   │   ├── CsvEventLogReader.java    # CSV event log parser (process mining format)
│   │   │   ├── RESTVlmc.java             # HTTP handler for prediction API (GET /?ctx=)
│   │   │   ├── GenerateEcfToFile.java    # ECF file output utility (standalone main)
│   │   │   ├── SelfLoopRemover.java      # Self-loop removal from ECF graphs (standalone main)
│   │   │   ├── DebugEcfComparison.java   # Debug utility per analisi ECF (standalone main)
│   │   │   ├── TestAutoEcfGeneration.java # Test manuale generazione ECF (standalone main)
│   │   │   └── test.java                 # Classe test vuota (legacy)
│   │   ├── vlmc/                         # VLMC data structures
│   │   │   ├── VlmcRoot.java             # Root node, tree operations, serialization, simulation
│   │   │   ├── VlmcNode.java             # Tree node: label, distribution, pruning, clone
│   │   │   ├── VlmcInternalNode.java     # Internal node variant (vuoto, non usato)
│   │   │   ├── NextSymbolsDistribution.java  # Probability distribution + sampling
│   │   │   └── vlmcWalker.java           # Visitor pattern interface (functional)
│   │   └── suffixarray/                  # Pattern matching
│   │       ├── SuffixArray.java          # String suffix array (Princeton-based)
│   │       └── SuffixArrayInt.java       # Integer variant with LCP support
│   └── test/java/test/
│       ├── SaTest.java                   # Suffix array test (hardcoded path)
│       ├── SaTestInt.java                # Integer suffix array test
│       ├── CsvEventLogReaderTest.java    # JUnit 5: CSV parsing, ordering, separators (10 test)
│       └── LikelihoodTest.java           # JUnit 5: likelihood computation on hand-built VLMC (7 test)
├── pom.xml                               # Maven build config
├── plan/                                 # Piani di implementazione
├── .github/
│   └── workflows/
│       └── ci.yml                        # GitHub Actions: compile + test (JDK 17)
└── .claude/                              # Configurazione agenti Claude
    ├── settings.json                     # Permessi (git, mvn, java) e hooks
    ├── commands/                         # Slash commands (10 agenti)
    │   ├── orchestrate.md                # Pipeline end-to-end con git (branch, commit, CI)
    │   ├── plan.md                       # Pianificazione task strutturati
    │   ├── implement.md                  # Implementazione singolo task
    │   ├── auto.md                       # Pipeline rapida 1-3 file
    │   ├── review.md                     # Code review strutturata
    │   ├── test.md                       # Testing agent
    │   ├── detect-bs.md                  # LLM bullshit detector (CAT-1..CAT-7)
    │   ├── sync-docs.md                  # Sincronizzazione documentazione
    │   ├── prepare.md                    # Setup ambiente
    │   └── setup-env.md                  # Verifica prerequisiti
    └── skills/                           # Conoscenza specializzata
        ├── build/SKILL.md                # Build patterns Maven
        └── testing/SKILL.md              # Testing patterns
```

## Componenti Chiave

| Classe | Package | Responsabilita |
|--------|---------|---------------|
| `fitVlmc` | `fitvlmc` | Entry point CLI (20 opzioni), modalita learning/prediction/REST/likelihood |
| `EcfNavigator` | `fitvlmc` | Navigazione ECF, costruzione albero VLMC, pruning statistico (chi-square) |
| `Trace2EcfIntegrator` | `fitvlmc` | Generazione automatica ECF `Flow` da tracce raw (split su spazi, reset su `end$`) |
| `CsvEventLogReader` | `fitvlmc` | Parser CSV event log (case_id, activity, timestamp). Auto-detection formato. |
| `RESTVlmc` | `fitvlmc` | HTTP handler: `GET /?ctx=state1-state2-state3` ritorna distribuzione next symbol |
| `GenerateEcfToFile` | `fitvlmc` | Utility standalone: genera ECF da file tracce e salva su file |
| `SelfLoopRemover` | `fitvlmc` | Utility standalone: rimuove self-loop (A->A) da ECF, preserva cicli (A->B->A) |
| `DebugEcfComparison` | `fitvlmc` | Utility standalone: analisi dettagliata ECF (self-loop, 2-cycle, struttura) |
| `VlmcRoot` | `vlmc` | Nodo radice: simulazione, likelihood, parsing VLMC da file, DFS traversal |
| `VlmcNode` | `vlmc` | Nodo albero: label, distribuzione, pruning (Kullback-Leibler), clone, context extraction |
| `NextSymbolsDistribution` | `vlmc` | Distribuzione probabilita next symbol con sampling (EnumeratedDistribution) |
| `vlmcWalker` | `vlmc` | Interfaccia funzionale visitor pattern per DFS |
| `SuffixArray` | `suffixarray` | Suffix array su stringhe: count, first/last occurrence via binary search |
| `SuffixArrayInt` | `suffixarray` | Suffix array su `ArrayList<Integer>` con supporto LCP (Kasai) |

## CLI Reference

Alias: `java -jar target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar`

### Tutte le opzioni

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

# Predizione singola
java -jar <JAR> --vlmc model.vlmc --pred --initCtx "state1 state2"

# REST server
java -jar <JAR> --vlmc model.vlmc --pred_rest 8080
# Query: GET http://localhost:8080/?ctx=state1-state2-state3
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
- **Package**: `fitvlmc` (core), `vlmc` (data structures), `suffixarray` (algorithms)
- **Error handling**: eccezioni checked per I/O, unchecked per errori di programmazione
- **Nessun framework DI** — istanziazione diretta
- **CLI**: java-getopt per parsing argomenti
- **Statistiche**: Apache Commons Math 3.6.1 + jdistlib 0.4.5 per chi-square e distribuzioni
- **Parsing**: ANTLR 4.7.2 runtime per parsing ECF
- **Utilities**: Apache Commons Lang 3.12.0 per StringUtils/ArrayUtils, jfiglet 0.0.9 per banner ASCII
- **HTTP**: `com.sun.net.httpserver` (JDK built-in) per REST API
- **Testing**: JUnit Jupiter 5.10.1 + maven-surefire-plugin 3.2.2

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
Packages: `fitvlmc`, `vlmc`, `suffixarray`, `ci`, `build`

### CI Pipeline

GitHub Actions (`.github/workflows/ci.yml`):
- **compile** — `mvn compile` con JDK 17
- **test** — `mvn test` (dipende da compile)

Triggerato su push a `main` e su pull request verso `main`.

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
# Compila e testa
mvn clean test

# Solo compila (veloce)
mvn compile

# Build fat JAR
mvn clean package -DskipTests

# Esegui main class
mvn exec:java -Dexec.mainClass="fitvlmc.fitVlmc" -Dexec.args="--help"

# Esegui da JAR
java -jar target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help

# Git: stato branch
git log --oneline main..HEAD
git diff main..HEAD --stat
```

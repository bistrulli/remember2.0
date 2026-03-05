# CLAUDE.md — jfitVLMC Project

## Panoramica

**jfitVLMC** (REMEMBER) e' un'implementazione Java di algoritmi di apprendimento per Variable Length Markov Chain (VLMC). Apprende modelli di Markov adattivi da tracce di esecuzione, supporta simulazione, predizione e generazione automatica di modelli ECF.

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
- Si trova tipicamente in `/lightWeightMB/` o in un percorso locale
- Per installarla: `cd <ecf-project-dir> && mvn install`
- Se la build fallisce con "Could not resolve dependencies... ecf", la dipendenza ECF non e' installata

## Struttura Progetto

```
jfitVLMC/
├── src/
│   ├── main/java/
│   │   ├── fitvlmc/                  # Core: learning, execution, REST
│   │   │   ├── fitVlmc.java          # Main entry point (CLI + learning)
│   │   │   ├── EcfNavigator.java     # ECF traversal, VLMC tree construction
│   │   │   ├── Trace2EcfIntegrator.java  # Auto ECF generation from traces
│   │   │   ├── RESTVlmc.java         # HTTP handler for prediction API
│   │   │   ├── GenerateEcfToFile.java    # ECF file output utility
│   │   │   └── SelfLoopRemover.java  # Graph optimization
│   │   ├── vlmc/                     # VLMC data structures
│   │   │   ├── VlmcRoot.java         # Root node, tree operations, serialization
│   │   │   ├── VlmcNode.java         # Tree node for states
│   │   │   ├── VlmcInternalNode.java # Internal node variant
│   │   │   ├── NextSymbolsDistribution.java  # Probability distribution
│   │   │   └── vlmcWalker.java       # Visitor pattern interface
│   │   └── suffixarray/              # Pattern matching
│   │       ├── SuffixArray.java      # String suffix array (Princeton)
│   │       └── SuffixArrayInt.java   # Integer variant
│   └── test/java/test/
│       ├── SaTest.java               # Suffix array tests
│       └── SaTestInt.java            # Integer suffix array tests
├── pom.xml                           # Maven build config
├── lib/                              # Local JARs (legacy, ora in pom.xml)
├── dist/                             # Pre-built JARs
├── target/                           # Maven build output
├── plan/                             # Piani di implementazione
└── .claude/                          # Configurazione agenti Claude
    ├── settings.json                 # Permessi e hooks
    ├── commands/                     # Slash commands (agenti)
    └── skills/                       # Conoscenza specializzata
```

## Componenti Chiave

| Classe | Responsabilita |
|--------|---------------|
| `fitVlmc` | Entry point CLI, parsing argomenti, modalita learning/prediction/REST |
| `EcfNavigator` | Navigazione ECF con depth-limiting, costruzione albero VLMC |
| `Trace2EcfIntegrator` | Generazione automatica ECF da tracce raw |
| `VlmcRoot` | Nodo radice VLMC: navigazione, serializzazione, likelihood, simulazione |
| `VlmcNode` / `VlmcInternalNode` | Nodi dell'albero VLMC con distribuzioni di probabilita |
| `SuffixArray` / `SuffixArrayInt` | Pattern matching efficiente su tracce |
| `RESTVlmc` | HTTP handler per predizioni via REST API |

## CLI Reference

```bash
# Learning
java -jar target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --infile traces.txt --vlmcfile model.vlmc --alfa 0.05 --nsim 1000

# Prediction
java -jar target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --vlmc model.vlmc --pred --initCtx "state1 state2"

# REST server
java -jar target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --vlmc model.vlmc --pred_rest 8080

# Likelihood analysis
java -jar target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --vlmc model.vlmc --lik contexts.txt
```

## Convenzioni Codice

- **Naming**: `CamelCase` per classi, `camelCase` per metodi/variabili
- **Package**: `fitvlmc` (core), `vlmc` (data structures), `suffixarray` (algorithms)
- **Error handling**: eccezioni checked per I/O, unchecked per errori di programmazione
- **Nessun framework DI** — istanziazione diretta
- **CLI**: java-getopt per parsing argomenti
- **Statistiche**: Apache Commons Math + jdistlib per chi-square e distribuzioni

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

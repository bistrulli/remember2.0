# Setup Environment — jfitVLMC

Verifica e configura l'ambiente di sviluppo Java + Maven necessario per compilare e testare il progetto.

## Input

$ARGUMENTS

Argomenti opzionali:
- `--check` — verifica che l'ambiente sia corretto, non modificare nulla
- `--clean` — esegui `mvn clean` e ricompila da zero

Se nessun argomento: verifica l'ambiente e compila il progetto.

## Procedura

### 1. Verifica Java

```bash
java -version 2>&1 | head -1
javac -version 2>&1 | head -1
```

- Richiesto: Java 17+
- Se non disponibile: **STOP** — "Java 17+ non trovato. Installa con `brew install openjdk@17` (macOS) o `sdk install java 17-open` (SDKMAN)."

### 2. Verifica Maven

```bash
mvn -version 2>&1 | head -3
```

- Richiesto: Maven 3.x
- Se non disponibile: **STOP** — "Maven 3.x non trovato. Installa con `brew install maven` (macOS) o `sdk install maven` (SDKMAN)."

### 3. Verifica dipendenza ECF

La dipendenza ECF (`it.sysma.vlmc:ecf:1.0.0-SNAPSHOT`) deve essere nel repository Maven locale.

```bash
ls ~/.m2/repository/it/sysma/vlmc/ecf/1.0.0-SNAPSHOT/ 2>/dev/null | head -5
```

Se non presente:
```
ATTENZIONE: La dipendenza ECF non e' installata nel repository Maven locale.
Per installarla:
  1. Clona/copia il progetto ECF
  2. Esegui: cd <ecf-project-dir> && mvn install
  3. Poi ritorna qui e riesegui /setup-env
```

### 4. Compilazione progetto

```bash
mvn compile -q
```

Se fallisce, analizza l'errore:
- Dipendenza mancante -> guida installazione
- Errore di compilazione -> mostra errore e suggerisci fix
- Java version mismatch -> suggerisci versione corretta

### 5. Esecuzione test

```bash
mvn test
```

### 6. Verifica git hooks

```bash
git config core.hooksPath 2>/dev/null || echo "Git hooks path non configurato"
ls .githooks/pre-push 2>/dev/null || echo ".githooks/pre-push non trovato"
```

Se non configurato:
```
Per abilitare i git hooks del progetto:
  git config core.hooksPath .githooks
```

### 7. Verifica quality tools

```bash
# Spotless (formatting)
mvn spotless:check -q 2>&1 | tail -3

# SpotBugs (static analysis)
mvn compile spotbugs:check -q 2>&1 | tail -3

# JaCoCo (coverage) - verifica che il plugin sia configurato
grep -q "jacoco-maven-plugin" pom.xml && echo "JaCoCo: configurato" || echo "JaCoCo: NON configurato"
```

### 8. Verifica google-java-format (opzionale)

```bash
which google-java-format 2>/dev/null || echo "google-java-format non installato (opzionale, per auto-format hook)"
```

### 9. Verifica git-cliff (opzionale)

```bash
which git-cliff 2>/dev/null || echo "git-cliff non installato (opzionale, per changelog)"
```

Se non installato:
```
Per abilitare changelog automatico:
  brew install git-cliff
  git-cliff --config cliff.toml -o CHANGELOG.md
```

### 10. Report

```
===================================================
  ENVIRONMENT SETUP — jfitVLMC
===================================================

  Java:         <version>
  Maven:        <version>
  ECF dep:      INSTALLATA | MANCANTE

  Compilazione: PASS | FAIL
  Unit test:    PASS | FAIL (N tests)

  Quality tools:
    Spotless:   PASS | FAIL
    SpotBugs:   PASS | FAIL
    JaCoCo:     CONFIGURATO (<N%> coverage)

  Git hooks:    CONFIGURATO | NON CONFIGURATO
  Formatter:    google-java-format INSTALLATO | NON INSTALLATO (opzionale)
  Changelog:    git-cliff INSTALLATO | NON INSTALLATO (opzionale)

  Build artifact:
    target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar

  Comandi utili:
    mvn compile              # solo compilazione
    mvn test                 # unit test (esclude E2E)
    mvn verify               # unit + E2E + coverage check
    mvn clean package        # build fat JAR
    mvn spotless:apply       # auto-format codice
    mvn spotless:check       # verifica formatting
    mvn compile spotbugs:check  # static analysis
===================================================
```

## Regole

1. Java 17+ e' obbligatorio
2. Maven 3.x e' obbligatorio
3. La dipendenza ECF deve essere nel repo locale Maven
4. git hooks (`git config core.hooksPath .githooks`) consigliato
5. google-java-format e' opzionale ma consigliato
6. git-cliff e' opzionale (per changelog)
7. Se `--check`: solo verifica, non installare ne' modificare nulla

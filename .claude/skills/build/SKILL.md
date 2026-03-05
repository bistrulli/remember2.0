# Build Skill — jfitVLMC

## Quick Reference

| Task | Comando |
|------|---------|
| Compila | `mvn compile` |
| Compila + test | `mvn test` |
| Build fat JAR | `mvn clean package -DskipTests` |
| Build completo | `mvn clean package` |
| Clean | `mvn clean` |
| Esegui | `java -jar target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar` |

## Artifact di build

| Artifact | Path | Dimensione |
|----------|------|-----------|
| JAR slim | `target/jfitvlmc-1.0.0-SNAPSHOT.jar` | ~54 KB |
| Fat JAR | `target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar` | ~4.9 MB |

Il **fat JAR** include tutte le dipendenze ed e' l'artifact eseguibile principale.

## Dipendenze

| Libreria | Versione | Scopo |
|----------|---------|-------|
| ECF | 1.0.0-SNAPSHOT | Classi Electronic Control Flow (locale) |
| ANTLR4 Runtime | 4.7.2 | Parsing grammatiche ECF |
| Commons Math3 | 3.6.1 | Operazioni matematiche, distribuzioni |
| Commons Lang3 | 3.12.0 | Utilita' stringhe e array |
| JFiglet | 0.0.9 | Banner ASCII per CLI |
| jdistlib | 0.4.5 | Distribuzioni statistiche (chi-square) |

### Dipendenza ECF (locale)

La dipendenza ECF NON e' su Maven Central. Deve essere installata nel repo Maven locale:

```bash
# Dal progetto ECF
cd /path/to/ecf-project
mvn install

# Verifica installazione
ls ~/.m2/repository/it/sysma/vlmc/ecf/1.0.0-SNAPSHOT/
```

Se manca, `mvn compile` fallira' con: `Could not resolve dependencies for project it.sysma.vlmc:jfitvlmc`

## Configurazione Maven

- **Java target**: 17 (source e target)
- **Encoding**: UTF-8
- **Main class**: `fitvlmc.fitVlmc`
- **Assembly plugin**: `maven-assembly-plugin:3.6.0` per fat JAR
- **Compiler plugin**: `maven-compiler-plugin:3.11.0`

## Esecuzione

### CLI modes

```bash
JAR="target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

# Learning
java -jar $JAR --infile traces.txt --vlmcfile model.vlmc --alfa 0.05 --nsim 1000

# Prediction
java -jar $JAR --vlmc model.vlmc --pred --initCtx "state1 state2"

# REST server
java -jar $JAR --vlmc model.vlmc --pred_rest 8080

# Likelihood
java -jar $JAR --vlmc model.vlmc --lik contexts.txt

# Auto ECF generation (no --ecf flag)
java -jar $JAR --infile traces.txt --vlmcfile model.vlmc

# Save auto-generated ECF
java -jar $JAR --infile traces.txt --vlmcfile model.vlmc --ecfoutfile generated.ecf
```

### Parametri chiave

| Parametro | Descrizione | Default |
|-----------|-------------|---------|
| `--infile` | File tracce di input | - |
| `--vlmcfile` | File output modello VLMC | - |
| `--vlmc` | File modello VLMC da caricare | - |
| `--ecf` | File ECF (opzionale, auto-generato se assente) | - |
| `--alfa` | Alpha per pruning chi-square | 0.05 |
| `--nsim` | Numero simulazioni da generare | 0 |
| `--maxdepth` | Limite profondita' navigazione ECF | 25 |
| `--pred` | Modalita' predizione | false |
| `--pred_rest` | Porta REST server | - |
| `--rnd` | Genera modello randomizzato | false |

## Troubleshooting

| Problema | Causa | Soluzione |
|----------|-------|-----------|
| `Could not resolve dependencies... ecf` | ECF non installato localmente | `cd <ecf-dir> && mvn install` |
| `release version 17 not supported` | Java < 17 | Installa Java 17+ |
| `OutOfMemoryError` durante build | Heap troppo piccolo | `export MAVEN_OPTS="-Xmx1g"` |
| `ClassNotFoundException` a runtime | JAR slim invece di fat JAR | Usa `*-jar-with-dependencies.jar` |

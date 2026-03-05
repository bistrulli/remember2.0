# Piano: Maven multi-modulo + eliminazione dipendenze locali

- **Moduli:** ecf (nuovo), jfitvlmc, CI
- **Stima:** 7 task, ~12 file
- **Data:** 2026-03-05

## Contesto

La CI fallisce perche' due dipendenze non sono su Maven Central:
1. `it.sysma.vlmc:ecf:1.0.0-SNAPSHOT` — progetto ECF (parser + strutture dati)
2. `jdistlib:jdistlib:0.4.5` — usato per una sola chiamata a `ChiSquare.quantile()`

### Soluzione
- **ECF**: integrare come sotto-modulo Maven (il sorgente vive in `/Users/emilio-imt/Desktop/vlmc/ECF/`)
- **jdistlib**: sostituire con Apache Commons Math 3 (`ChiSquaredDistribution`) che e' gia' una dipendenza

### Struttura target

```
jfitVLMC/                          (root = parent POM)
├── pom.xml                        (parent POM, packaging=pom, modules=[ecf, jfitvlmc])
├── ecf/                           (modulo ECF)
│   ├── pom.xml                    (parent=root, artifactId=ecf)
│   ├── ECF.g4                     (grammatica ANTLR, reference only)
│   └── src/main/java/
│       ├── ECFEntity/
│       │   ├── Edge.java
│       │   ├── Flow.java
│       │   └── ECFListener.java
│       └── antlr/
│           ├── ECFBaseListener.java
│           ├── ECFLexer.java
│           ├── ECFListener.java
│           └── ECFParser.java
├── jfitvlmc/                      (modulo jfitVLMC, rinominato da src/)
│   ├── pom.xml                    (parent=root, dipende da ecf)
│   └── src/
│       ├── main/java/...          (codice esistente, invariato)
│       └── test/java/...          (test esistenti, invariati)
├── plan/                          (piani, resta al root)
├── CLAUDE.md                      (resta al root)
└── .github/workflows/ci.yml       (aggiornato per multi-modulo)
```

### File ECF da copiare (dal progetto /Users/emilio-imt/Desktop/vlmc/ECF/)

Sorgenti (da `ECF/src/` a `ecf/src/main/java/`):
- `ECFEntity/Edge.java` (102 righe)
- `ECFEntity/Flow.java` (34 righe)
- `ECFEntity/ECFListener.java` (64 righe) — il visitor ANTLR
- `antlr/ECFBaseListener.java` (112 righe)
- `antlr/ECFLexer.java` (140 righe)
- `antlr/ECFListener.java` (71 righe) — interfaccia ANTLR generata
- `antlr/ECFParser.java` (460 righe)

NON copiare (non necessari per jfitVLMC):
- `main/Trace2Ecf.java` — abbiamo gia' `Trace2EcfIntegrator.java`
- `main/grammarTest.java` — test manuale

Copiare al root di `ecf/` come reference:
- `ECF.g4` — grammatica ANTLR (non compilata, solo documentazione)

## Task 1: Creare la struttura del parent POM

- **Package:** root
- **File:** pom.xml (NUOVO — al root del progetto)
- **Cosa:** Creare il parent POM con `packaging=pom` e `modules=[ecf, jfitvlmc]`.
  Il parent POM definisce:
  - `groupId=it.sysma.vlmc`, `artifactId=vlmc-parent`, `version=1.0.0-SNAPSHOT`
  - `packaging=pom`
  - `modules`: `ecf`, `jfitvlmc`
  - `properties`: Java 17, encoding UTF-8
  - `dependencyManagement`: versioni condivise (antlr4-runtime 4.7.2, commons-math3 3.6.1, etc.)
  - Plugin management per compiler, surefire, assembly
  NON includere dipendenze dirette — ogni modulo dichiara le sue.
- **Perche':** Il parent POM e' il collante del multi-modulo. Maven lo usa per risolvere
  l'ordine di compilazione e le versioni condivise.
- **Dipende da:** nessuno
- **Criteri:** Il file pom.xml esiste al root con packaging=pom e 2 moduli.
- **Verifica:** `cat pom.xml | grep -E 'packaging|module'`

## Task 2: Creare il modulo ECF

- **Package:** ecf
- **File:** ecf/pom.xml, ecf/src/main/java/ECFEntity/*.java, ecf/src/main/java/antlr/*.java, ecf/ECF.g4
- **Cosa:**
  1. Creare `ecf/pom.xml`:
     - `parent` = parent POM (groupId, artifactId, version, relativePath=../)
     - `artifactId=ecf`
     - Dipendenze: `antlr4-runtime:4.7.2` (unica dipendenza)
     - NO plugin assembly (non serve un fat JAR per ECF)
  2. Creare la directory `ecf/src/main/java/`
  3. Copiare i file sorgente da `/Users/emilio-imt/Desktop/vlmc/ECF/src/`:
     - `ECFEntity/Edge.java` -> `ecf/src/main/java/ECFEntity/Edge.java`
     - `ECFEntity/Flow.java` -> `ecf/src/main/java/ECFEntity/Flow.java`
     - `ECFEntity/ECFListener.java` -> `ecf/src/main/java/ECFEntity/ECFListener.java`
     - `antlr/ECFBaseListener.java` -> `ecf/src/main/java/antlr/ECFBaseListener.java`
     - `antlr/ECFLexer.java` -> `ecf/src/main/java/antlr/ECFLexer.java`
     - `antlr/ECFListener.java` -> `ecf/src/main/java/antlr/ECFListener.java`
     - `antlr/ECFParser.java` -> `ecf/src/main/java/antlr/ECFParser.java`
  4. Copiare `ECF.g4` in `ecf/ECF.g4` (reference, non compilata)
  5. Verificare che `mvn compile -pl ecf` compila senza errori
- **Perche':** Il modulo ECF deve compilare autonomamente come dipendenza Maven.
- **Dipende da:** Task 1
- **Criteri:** `mvn compile -pl ecf` compila OK. Tutti i file ECF sono presenti.
- **Verifica:** `mvn compile -pl ecf -q`

## Task 3: Spostare jfitVLMC in sotto-directory jfitvlmc/

- **Package:** jfitvlmc
- **File:** jfitvlmc/pom.xml, jfitvlmc/src/** (spostati)
- **Cosa:**
  1. Creare la directory `jfitvlmc/`
  2. Spostare `src/` -> `jfitvlmc/src/`
  3. Creare `jfitvlmc/pom.xml`:
     - `parent` = parent POM
     - `artifactId=jfitvlmc`
     - Dipendenze:
       - `it.sysma.vlmc:ecf:${project.version}` (riferimento al modulo ECF)
       - `antlr4-runtime:4.7.2`
       - `commons-math3:3.6.1`
       - `commons-lang3:3.12.0`
       - `jfiglet:0.0.9`
       - `junit-jupiter:5.10.1` (test)
       - **NON** jdistlib (verra' eliminata nel Task 4)
       - **Temporaneamente** includere jdistlib per non rompere la compilazione
     - Plugin: compiler, surefire, assembly (fat JAR con mainClass=fitvlmc.fitVlmc)
  4. Rimuovere il vecchio `src/` al root (ora e' in `jfitvlmc/src/`)
  5. Rimuovere il vecchio `pom.xml` del singolo modulo (sostituito dal parent POM al Task 1)
  6. Verificare che `mvn compile` dal root compila entrambi i moduli
- **Perche':** La struttura multi-modulo richiede che ogni modulo sia in una sotto-directory.
- **Dipende da:** Task 1, Task 2
- **Criteri:** `mvn compile` dal root compila ecf + jfitvlmc senza errori.
- **Verifica:** `mvn compile -q`

## Task 4: Eliminare dipendenza jdistlib

- **Package:** jfitvlmc
- **File:** jfitvlmc/src/main/java/fitvlmc/fitVlmc.java, jfitvlmc/pom.xml
- **Cosa:**
  1. In `fitVlmc.java`, sostituire:
     ```java
     import jdistlib.ChiSquare;
     // ...
     fitVlmc.cutoff = jdistlib.ChiSquare.quantile(fitVlmc.alfa,
         Math.max(0.1, learner.ecfModel.getEdges().size() - 1), false, false) / 2;
     ```
     con:
     ```java
     import org.apache.commons.math3.distribution.ChiSquaredDistribution;
     // ...
     ChiSquaredDistribution chi2 = new ChiSquaredDistribution(
         Math.max(0.1, learner.ecfModel.getEdges().size() - 1));
     fitVlmc.cutoff = chi2.inverseCumulativeProbability(fitVlmc.alfa) / 2;
     ```
     Nota: `jdistlib.ChiSquare.quantile(p, df, false, false)` e'
     equivalente a `inverseCumulativeProbability(p)` di Commons Math.
     I flag `false, false` significano "upper tail = false, log = false",
     cioe' il quantile standard (lower tail, non-log) — esattamente
     quello che fa `inverseCumulativeProbability`.
  2. Rimuovere la dipendenza `jdistlib` da `jfitvlmc/pom.xml`
  3. Rimuovere qualsiasi altro import di jdistlib (dovrebbe essere solo 1)
- **Perche':** jdistlib non e' su Maven Central e non ha un repository pubblico.
  Apache Commons Math 3 fornisce la stessa funzionalita' ed e' gia' una dipendenza.
- **Dipende da:** Task 3
- **Criteri:** Il progetto compila senza jdistlib nel classpath. Il calcolo del cutoff
  produce lo stesso valore (entro 1e-10) di prima.
- **Verifica:** `mvn compile -q`

## Task 5: Aggiornare il CI workflow

- **Package:** CI
- **File:** .github/workflows/ci.yml
- **Cosa:** Aggiornare il workflow per il multi-modulo:
  1. **Compile job**: `mvn compile -q` (dal root, compila entrambi i moduli)
  2. **Test job**: `mvn test -q` (esegue i test di jfitvlmc)
  3. Rimuovere il commento/step "Install ECF dependency" — non serve piu'
  4. Aggiornare il cache path se necessario (il default Maven cache funziona)
  Il workflow non deve piu' installare dipendenze locali — tutto e' nel repo.
- **Perche':** Il CI deve funzionare con la nuova struttura multi-modulo.
- **Dipende da:** Task 3, Task 4
- **Criteri:** Il workflow e' valido YAML. I comandi Maven sono corretti per multi-modulo.
- **Verifica:** `cat .github/workflows/ci.yml`

## Task 6: Aggiornare il percorso del fat JAR e EndToEndTest

- **Package:** jfitvlmc
- **File:** jfitvlmc/src/test/java/test/EndToEndTest.java
- **Cosa:** Aggiornare il path del fat JAR nell'EndToEndTest. Con il multi-modulo,
  il target directory e' in `jfitvlmc/target/` invece di `target/`.
  Cambiare:
  ```java
  File targetDir = new File("target");
  ```
  in:
  ```java
  File targetDir = new File("jfitvlmc/target");
  // Fallback per quando il test gira dalla directory del modulo
  if (!new File(targetDir, "jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar").exists()) {
      targetDir = new File("target");
  }
  ```
- **Perche':** Il path del JAR cambia con la ristrutturazione. Il test deve trovarlo
  sia quando lanciato dal root (`mvn test`) sia dal modulo (`mvn test -pl jfitvlmc`).
- **Dipende da:** Task 3
- **Criteri:** `mvn package -DskipTests -q && mvn test -Dtest=EndToEndTest -pl jfitvlmc`
  passa (o viene skippato se JAR non presente).
- **Verifica:** `mvn package -DskipTests -q && mvn test -Dtest=EndToEndTest -pl jfitvlmc -q`

## Task 7: Verificare compilazione e test completi

- **Package:** tutti
- **File:** nessuno (solo verifica)
- **Cosa:**
  1. Eseguire `mvn clean compile` dal root — deve compilare ecf + jfitvlmc
  2. Eseguire `mvn clean test` dal root — tutti i test devono passare (EndToEndTest skippato)
  3. Eseguire `mvn clean package -DskipTests` — deve produrre il fat JAR in `jfitvlmc/target/`
  4. Eseguire il fat JAR con `--help` per verificare che funziona
  5. Se qualcosa fallisce, fixare e ri-verificare
- **Perche':** Verifica finale che la ristrutturazione non ha rotto nulla.
- **Dipende da:** Task 1-6
- **Criteri:** Compilazione OK, test OK, fat JAR funzionante.
- **Verifica:** `mvn clean test -q && mvn package -DskipTests -q && java -jar jfitvlmc/target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help`

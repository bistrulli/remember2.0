# Piano: Normalizzazione scoring per lunghezza sessione

Branch: feat/fix-quality-gates-full-cleanup
PR: continua sulla PR esistente #10

## Contesto

Lo scoring attuale (sia VLMC classic che STA) somma i `-log(P)` senza normalizzare per la lunghezza della sessione. Sessioni più lunghe hanno automaticamente score più alto, introducendo un bias. La normalizzazione calcola la **cross-entropy media per step**, rendendo comparabili sessioni di lunghezze diverse.

La modifica è solo nell'inferenza — il modello VLMC non viene ri-addestrato.

---

## Task 1: Normalizzare scoreWithVlmc

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/HdfsBenchmark.java`
- **Cosa:** Nel metodo `scoreWithVlmc()`, dopo aver calcolato `totalScore = -log(finalLik)`, dividere per `(events.size() - 1)`. Il denominatore è il numero di predizioni (dal 2° evento in poi). Gestire il caso `events.size() <= 1` assegnando score 0.
- **Dipende da:** nessuno
- **Verifica:** `mvn compile -q`

## Task 2: Normalizzare scoreWithSta

- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/HdfsBenchmark.java`
- **Cosa:** Nel metodo `scoreWithSta()`, dopo il loop, dividere `totalScore` per `(events.size() - 1)`. Stessa logica: il denominatore è il numero di step di predizione. Gestire `events.size() <= 1` e il caso `totalScore == POSITIVE_INFINITY` (non dividere infinito).
- **Dipende da:** nessuno
- **Verifica:** `mvn compile -q`

## Task 3: Aggiornare test HdfsMiniTest

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/HdfsMiniTest.java`
- **Cosa:** Verificare che i test esistenti passino ancora con la normalizzazione. Le asserzioni non dipendono da valori assoluti di score ma da confronti relativi (F1, P, R), quindi dovrebbero passare. Se necessario, aggiustare soglie nei commenti.
- **Dipende da:** Task 1, Task 2
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=HdfsMiniTest -q`

## Task 4: Build e run benchmark HDFS reale

- **Package:** sta
- **File:** nessun file modificato
- **Cosa:** Ricompilare il fat JAR e rilanciare il benchmark sul dataset HDFS reale con maxDepth=50, alfa=0.01. Comando:
  ```bash
  mvn package -DskipTests -q
  java -Xmx10g -cp jfitvlmc/target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
    sta.HdfsFullBenchmark \
    --raw-log /Users/emilio-imt/Downloads/3227177/HDFS_1/HDFS.log \
    --labels /Users/emilio-imt/Downloads/3227177/HDFS_1/anomaly_label.csv \
    --output results/hdfs_benchmark_normalized.csv
  ```
- **Dipende da:** Task 1, Task 2, Task 3
- **Verifica:** Il benchmark completa senza errori e produce il CSV di output

# Piano: BGL Benchmark — Sliding Window Anomaly Detection

- **Branch:** feat/bgl-benchmark
- **Moduli:** fitvlmc, sta
- **Stima:** 7 task, ~6 file
- **Data:** 2026-03-06

## Contesto

BGL (BlueGene/L) è il secondo dataset più usato per log anomaly detection dopo HDFS.
A differenza di HDFS (sessioni naturali per block ID), BGL è un flusso continuo di log
da un supercomputer — serve sessionizzazione via sliding window.

**Dataset BGL:**
- 4.7M righe, giugno 2005 — gennaio 2006
- 69K nodi compute, 23 tipi di evento (COMPONENT_LEVEL)
- Label inline: prima colonna `-` = normal, altro = anomaly
- 348K righe anomale (7.3%)

**Strategia scelta:** sliding window non-overlapping (stride=window_size=20)
- ~237K finestre totali
- Ogni finestra con almeno 1 riga anomala = sessione anomala
- Comparabile direttamente con HDFS (575K sessioni)
- Eventi: COMPONENT_LEVEL (es. KERNEL_INFO, APP_FATAL) — 23 tipi puliti

**Risultati di riferimento DeepLog su BGL:** F1 ~0.96 (Du et al., CCS 2017)

## Task 1: Creare BglLogParser — parser del raw log BGL
- **Package:** fitvlmc
- **File:** `jfitvlmc/src/main/java/fitvlmc/BglLogParser.java`
- **Cosa:** Nuovo parser che legge `BGL.log` e produce sessioni sliding window:
  1. Legge riga per riga il file BGL
  2. Estrae: label (campo 1), node_id (campo 4), component (campo 8), level (campo 9)
  3. Crea event type come `COMPONENT_LEVEL` (es. `KERNEL_INFO`)
  4. Filtra righe malformate (campo 8 non uppercase)
  5. Raggruppa in finestre non-overlapping di dimensione configurabile (default 20)
  6. Marca ogni finestra come anomala se contiene almeno 1 riga con label != `-`
  7. Riusa `HdfsLogParser.HdfsSession` come struttura dati (blockId = "W_N" per window N)
- **Perché:** BGL ha formato completamente diverso da HDFS, serve parser dedicato
- **Dipende da:** nessuno
- **Criteri:** Parser legge BGL.log, produce lista di HdfsSession con eventi e labels corretti
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=BglLogParserTest -q`

## Task 2: Creare test per BglLogParser
- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/BglLogParserTest.java`
- **Cosa:** Test unitari per il parser BGL:
  1. Test parsing righe normali e anomale
  2. Test sliding window con finestra size=3 su input piccolo (10 righe)
  3. Test che finestra con 1+ anomaly è marcata anomala
  4. Test filtering righe malformate
  5. Test window size configurabile
- **Perché:** Il parser è critico — errori nel parsing invalidano tutto il benchmark
- **Dipende da:** Task 1
- **Criteri:** Tutti i test passano, copertura dei casi edge
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=BglLogParserTest -q`

## Task 3: Generalizzare HdfsBenchmark in LogBenchmark
- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/HdfsBenchmark.java`
- **Cosa:** Rinominare `HdfsBenchmark` in `LogBenchmark` (o mantenere il nome ma rimuovere
  dipendenze HDFS-specifiche). In pratica il benchmark già lavora con `List<HdfsSession>`
  che è una struttura generica (blockId + events + isAnomaly). Verificare che:
  1. `trainVlmc()` usa `HdfsLogParser.writeTraceFile()` — funziona con qualsiasi sessione
  2. `scoreWithVlmc/Sta/StaOnline` — funzionano con qualsiasi sessione
  3. `formatReport()` — parametrizzare il titolo (non hardcode "HDFS")
  4. Aggiungere parametro `String datasetName` al costruttore per il report
- **Perché:** Il benchmark è già quasi dataset-agnostico, serve solo rimuovere l'hardcoding HDFS
- **Dipende da:** nessuno
- **Criteri:** HdfsBenchmark funziona identicamente con sessioni BGL
- **Verifica:** `mvn compile -q` (nessun test rotto, cambio retrocompatibile)

## Task 4: Creare BglFullBenchmark — CLI per benchmark BGL
- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/BglFullBenchmark.java`
- **Cosa:** CLI entry point per il benchmark BGL, modellato su `HdfsFullBenchmark`:
  1. Opzioni: `--bgl-log <path>`, `--window-size <N>` (default 20), `--alfa`, `--output`,
     `--vlmc-model`, `--save-vlmc`, `--eta`
  2. Usa `BglLogParser` per parsing + sessionizzazione
  3. Usa `HdfsBenchmark` (o LogBenchmark) per training + scoring
  4. Stampa report con confronto VLMC vs STA vs BMA vs DeepLog (ref)
  5. Opzionale: salva CSV risultati
- **Perché:** Entry point separato mantiene HDFS e BGL indipendenti, evita complessità nel CLI
- **Dipende da:** Task 1, Task 3
- **Criteri:** `java -cp ... sta.BglFullBenchmark --bgl-log BGL.log --alfa 0.01` funziona end-to-end
- **Verifica:** `mvn compile -q`

## Task 5: Applicare Spotless formatting
- **Package:** build
- **File:** tutti i file nuovi/modificati
- **Cosa:** Eseguire `mvn spotless:apply` per formattare tutti i file nuovi
- **Perché:** CI richiede Google Java Format (AOSP) via Spotless
- **Dipende da:** Task 1, 2, 3, 4
- **Criteri:** `mvn spotless:check` passa
- **Verifica:** `mvn spotless:check -q`

## Task 6: Eseguire test suite completa
- **Package:** test
- **File:** nessuno (solo verifica)
- **Cosa:** Eseguire `mvn test` per verificare nessuna regressione
- **Perché:** I nuovi file e le modifiche a HdfsBenchmark non devono rompere test esistenti
- **Dipende da:** Task 5
- **Criteri:** Tutti i test passano (152+ test)
- **Verifica:** `mvn test -q`

## Task 7: Run benchmark BGL e documentare risultati
- **Package:** sta
- **File:** nessuno (solo esecuzione e output)
- **Cosa:** Eseguire il benchmark BGL completo:
  ```bash
  java -Xmx4g -cp ... sta.BglFullBenchmark \
    --bgl-log /Users/emilio-imt/Downloads/3227177/HDFS_1/../BGL.log \
    --alfa 0.01 --save-vlmc results/bgl_vlmc.vlmc \
    --output results/bgl_benchmark.csv
  ```
  Salvare i risultati nel commit message.
- **Perché:** Obiettivo finale — confronto numerico con DeepLog su BGL
- **Dipende da:** Task 6
- **Criteri:** Benchmark completa senza errori, risultati F1 stampati
- **Verifica:** Esecuzione manuale (non automatizzabile nel CI)

# Piano: Supporto CSV event log per fitting e likelihood

- **Branch:** feat/csv-eventlog-support
- **Moduli:** fitvlmc, vlmc
- **Stima:** 6 task, ~5 file
- **Data:** 2026-03-05

## Contesto

Attualmente REMEMBER accetta solo tracce in formato interno (stati separati da spazi, tracce
separate da `end$`). Questo piano aggiunge:
1. Parsing di CSV event log nel formato standard process mining (case_id, activity, timestamp)
2. Likelihood completa: per-prefix, per-trace, e log-likelihood aggregata del log intero

Formato CSV atteso (colonne configurabili):
```csv
case_id,activity,timestamp
1,login,2024-01-01 10:00:00
1,browse,2024-01-01 10:05:00
1,checkout,2024-01-01 10:10:00
2,login,2024-01-01 11:00:00
2,browse,2024-01-01 11:03:00
```

## Task 1: Creare CsvEventLogReader — parser CSV configurabile
- **Package:** fitvlmc
- **File:** src/main/java/fitvlmc/CsvEventLogReader.java
- **Cosa:** Nuova classe che legge un CSV event log e produce il formato interno (stringa con
  stati separati da spazi e `end$` tra tracce). Logica:
  1. Legge CSV riga per riga, skippa header
  2. Raggruppa eventi per case_id
  3. Ordina ogni gruppo per timestamp
  4. Genera stringa: `activity1 activity2 ... end$ activity1 activity3 ... end$`
  Parametri configurabili: nome colonna case_id, activity, timestamp, separatore CSV.
- **Perche':** Il formato CSV e' lo standard de facto per event log in process mining.
  Senza questo, l'utente deve pre-processare i dati manualmente.
- **Dipende da:** nessuno
- **Criteri:** La classe converte correttamente un CSV multi-case in formato interno.
  Gestisce: header mancante, colonne in ordine diverso, separatori diversi (virgola, tab, punto-e-virgola).
- **Verifica:** `mvn compile -q`

## Task 2: Aggiungere opzioni CLI per CSV
- **Package:** fitvlmc
- **File:** src/main/java/fitvlmc/fitVlmc.java
- **Cosa:** Aggiungere 4 nuove LongOpt:
  - `--csv-case <name>` (default: "case_id") — nome colonna case identifier
  - `--csv-activity <name>` (default: "activity") — nome colonna attivita'
  - `--csv-timestamp <name>` (default: "timestamp") — nome colonna timestamp
  - `--csv-separator <char>` (default: ",") — separatore CSV
  Aggiungere i campi statici corrispondenti e il parsing nel switch/case.
- **Perche':** L'utente deve poter adattare il parser al proprio formato CSV senza modificare codice.
- **Dipende da:** nessuno
- **Criteri:** Le opzioni sono parsate correttamente, valori default funzionano se non specificate.
  `--help` mostra le nuove opzioni.
- **Verifica:** `mvn compile -q`

## Task 3: Integrare CsvEventLogReader nel flusso di fitting
- **Package:** fitvlmc
- **File:** src/main/java/fitvlmc/fitVlmc.java
- **Cosa:** Modificare `readInputTraces()` per auto-detectare il formato dell'input:
  - Se il file ha estensione `.csv` OPPURE se almeno una opzione `--csv-*` e' specificata:
    usa `CsvEventLogReader` per convertire il CSV in formato interno
  - Altrimenti: comportamento attuale (lettura raw come stringa)
  L'auto-detect verifica anche la prima riga del file: se contiene virgole e non `end$`,
  assume CSV.
- **Perche':** Permette di usare `--infile traces.csv` direttamente senza conversione manuale.
  Retrocompatibile col formato attuale.
- **Dipende da:** Task 1, Task 2
- **Criteri:** `--infile file.csv --vlmcfile model.vlmc --alfa 0.05 --nsim 1` produce
  un modello VLMC valido. Il vecchio formato `--infile file.txt` continua a funzionare.
- **Verifica:** `mvn compile -q`

## Task 4: Estendere la likelihood — output per-prefix, per-trace e aggregata
- **Package:** fitvlmc, vlmc
- **File:** src/main/java/fitvlmc/fitVlmc.java, src/main/java/vlmc/VlmcRoot.java
- **Cosa:** Riscrivere il blocco likelihood in fitVlmc (linee 146-191) per produrre tre output:
  1. **File `.lik.prefix`**: per ogni traccia, likelihood di ogni prefisso (una riga per step).
     Formato: `trace_id,prefix_length,likelihood`
  2. **File `.lik`**: per ogni traccia, likelihood finale.
     Formato: `trace_id,trace_length,likelihood,log_likelihood`
  3. **Stdout**: log-likelihood aggregata = somma dei log-likelihood di tutte le tracce.
  Anche il file `--lik` deve supportare formato CSV (stessa logica auto-detect del Task 3).
- **Perche':** Il per-trace serve per confronto tra modelli, il per-prefix per analisi fine,
  l'aggregata come metrica globale di fitness del modello.
- **Dipende da:** Task 1 (se input CSV), ma puo' essere implementato indipendentemente
  per il formato attuale
- **Criteri:** Con `--vlmc model.vlmc --lik traces.txt` produce i 3 output corretti.
  La log-likelihood aggregata e' stampata su stdout.
- **Verifica:** `mvn compile -q`

## Task 5: Aggiungere test JUnit per CsvEventLogReader
- **Package:** test
- **File:** src/test/java/test/CsvEventLogReaderTest.java
- **Cosa:** Test JUnit (non main-based) per CsvEventLogReader:
  - Test CSV standard (case_id, activity, timestamp) con 3+ tracce
  - Test separatore diverso (tab, punto-e-virgola)
  - Test nomi colonne custom
  - Test ordinamento per timestamp (eventi non ordinati nel CSV)
  - Test traccia singola (un solo case_id)
  - Test input vuoto / malformato
- **Perche':** Il parser CSV e' il punto di ingresso dei dati. Bug qui corrompono tutto il pipeline.
- **Dipende da:** Task 1
- **Criteri:** Tutti i test passano. Copertura dei casi edge (CSV vuoto, timestamp non ordinati,
  caratteri speciali nelle attivita').
- **Verifica:** `mvn test -Dtest=CsvEventLogReaderTest -q`

## Task 6: Aggiungere test JUnit per likelihood estesa
- **Package:** test
- **File:** src/test/java/test/LikelihoodTest.java
- **Cosa:** Test JUnit per la nuova logica likelihood:
  - Costruisci un VLMC piccolo a mano (o via parseVLMC da stringa)
  - Verifica che getLikelihood ritorna valori corretti per tracce note
  - Verifica che log-likelihood aggregata e' corretta (somma dei log)
  - Verifica comportamento con simbolo sconosciuto (likelihood = 0)
- **Perche':** La likelihood e' una metrica critica. Errori numerici (underflow, log(0))
  devono essere catturati.
- **Dipende da:** Task 4
- **Criteri:** Test coprono: traccia nel linguaggio, traccia parzialmente fuori, traccia
  completamente fuori dal modello.
- **Verifica:** `mvn test -Dtest=LikelihoodTest -q`

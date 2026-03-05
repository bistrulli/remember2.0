# Piano: Aggiungere uEMSC al flusso likelihood

- **Moduli:** fitvlmc, test
- **Stima:** 4 task, ~4 file
- **Data:** 2026-03-05

## Contesto

La **uEMSC** (unit Earth Mover's Stochastic Conformance, Leemans et al. BPM 2019) misura
la conformance stocastica tra un event log e un modello. Formula:

```
uEMSC(L, M) = 1 - Σ_{t ∈ T} max(L(t) - M(t), 0)
```

Dove:
- `T` = insieme delle tracce **distinte** nel log
- `L(t)` = frequenza relativa della traccia `t` nel log (count / total)
- `M(t)` = probabilità della traccia `t` secondo il modello VLMC (`getLikelihood()` → ultimo valore)

Il risultato è in [0, 1]: 1 = conformance perfetta, 0 = nessun overlap.

**Proprietà computazionale chiave:** se il log è finito, la somma è finita — il modello
viene interrogato solo per le tracce presenti nel log. Non serve costruire l'intero
linguaggio stocastico del modello.

## Task 1: Calcolare uEMSC nel blocco likelihood di fitVlmc

- **Package:** fitvlmc
- **File:** src/main/java/fitvlmc/fitVlmc.java
- **Cosa:** Modificare il blocco `--lik` (righe ~158-250) per:
  1. **Raggruppare le tracce per contenuto** — usare una `HashMap<String, Integer>` che conta
     le occorrenze di ogni traccia distinta (chiave = traccia serializzata come stringa,
     es. `"A B end$"`). Questo serve per calcolare L(t).
  2. **Calcolare M(t)** — per ogni traccia distinta, usare `getLikelihood()` e prendere
     l'ultimo valore (probabilità completa della traccia secondo il modello).
  3. **Calcolare uEMSC** — iterare sulle tracce distinte:
     - `L(t) = count(t) / totalTraces`
     - `M(t) = getLikelihood(t).last()`
     - `surplus += max(L(t) - M(t), 0)`
     - `uEMSC = 1 - surplus`
  4. **Stampare su stdout** — aggiungere alla sezione `=== LIKELIHOOD ANALYSIS ===`:
     ```
     Distinct traces: N
     uEMSC (stochastic conformance): 0.XXXX
     ```
  **Nota:** il calcolo delle likelihood per-traccia e per-prefisso resta invariato.
  La uEMSC è un calcolo aggiuntivo che riusa i dati già computati.
  **Ottimizzazione:** calcolare `getLikelihood()` una sola volta per traccia distinta
  (non per ogni occorrenza), poi moltiplicare per la frequenza.
- **Perche':** La uEMSC è la metrica standard in process mining per confrontare
  linguaggi stocastici. Integrarla nel flusso `--lik` evita tool esterni.
- **Dipende da:** nessuno
- **Criteri:** `--lik` stampa il valore uEMSC su stdout. Il valore è in [0, 1].
  I file .lik e .lik.prefix continuano a essere generati come prima.
- **Verifica:** `mvn compile -q`

## Task 2: Aggiungere test JUnit per uEMSC

- **Package:** test
- **File:** src/test/java/test/UemscTest.java
- **Cosa:** Test JUnit che verifica il calcolo uEMSC su un VLMC costruito a mano:
  1. **Test conformance perfetta** — log con tracce le cui frequenze corrispondono
     esattamente alle probabilità del modello → uEMSC ≈ 1.0
  2. **Test conformance parziale** — log con alcune tracce più frequenti di quanto
     il modello prevede → uEMSC < 1.0, valore atteso calcolato a mano
  3. **Test traccia sconosciuta** — log con traccia non nel modello (M(t) = 0)
     → L(t) - 0 = L(t), che abbassa la uEMSC
  4. **Test log con una sola traccia distinta** — caso degenere
  I test costruiscono un VLMC a mano (stesso pattern di LikelihoodTest.java)
  e calcolano uEMSC direttamente per verificare la formula.
  Per testare il calcolo serve estrarre la logica uEMSC in un metodo statico
  riusabile (vedi Task 1 — il metodo può essere package-private o in una utility).
- **Perche':** La uEMSC coinvolge raggruppamento, frequenze, e confronto con probabilità
  del modello. Errori numerici (divisione, max, somma) devono essere catturati.
- **Dipende da:** Task 1
- **Criteri:** Tutti i test passano. I valori attesi sono calcolati a mano.
- **Verifica:** `mvn test -Dtest=UemscTest -q`

## Task 3: Test end-to-end con esecuzione JAR

- **Package:** test
- **File:** src/test/java/test/UemscTest.java (aggiungere al file del Task 2)
- **Cosa:** Aggiungere un test che verifica che il flusso completo `--vlmc` + `--lik`
  produce output contenente "uEMSC" su stdout. Questo test:
  1. Crea un file VLMC temporaneo (serializzando il VLMC costruito a mano)
  2. Crea un file di tracce temporaneo (formato legacy)
  3. Invoca il main con `--vlmc <tmp.vlmc> --lik <tmp.txt>`
  4. Cattura stdout e verifica che contiene "uEMSC" e un valore numerico
  **Nota:** se invocare il main è troppo complesso (dipendenze ECF, System.exit),
  limitarsi a testare la logica di calcolo unitariamente (Task 2 è sufficiente).
- **Perche':** Verifica l'integrazione end-to-end, non solo la formula isolata.
- **Dipende da:** Task 1, Task 2
- **Criteri:** Il test passa e verifica che l'output contiene la riga uEMSC.
- **Verifica:** `mvn test -Dtest=UemscTest -q`

## Task 4: Test end-to-end full pipeline (CSV → learning → uEMSC = 1)

- **Package:** test
- **File:** src/test/java/test/UemscEndToEndTest.java
- **Cosa:** Test JUnit che valida l'intero pipeline fitting → likelihood → uEMSC:
  1. **Genera un CSV sintetico** con 1000 tracce. Il CSV ha formato process mining
     (`case_id,activity,timestamp`). Le tracce sono generate da un alfabeto piccolo
     (es. 3-4 attività) con distribuzione controllata (es. 5 pattern di tracce
     con frequenze note). Usare `@TempDir` per i file temporanei.
  2. **Esegui il fitting** invocando il JAR (o il main) con:
     `--infile <csv> --csv-case case_id --csv-activity activity --csv-timestamp timestamp
      --vlmcfile <tmp.vlmc> --alfa 1 --nsim 1`
     Con `alfa=1` il cutoff chi-square è massimo → nessun pruning → il modello
     preserva tutta la struttura dell'albero con fedeltà massima.
  3. **Esegui la likelihood + uEMSC** invocando il JAR con:
     `--vlmc <tmp.vlmc> --lik <stesso csv> --csv-case case_id --csv-activity activity
      --csv-timestamp timestamp`
  4. **Verifica** che l'output stdout contiene `uEMSC` con valore pari a **1.0**
     (o molto vicino, tolleranza 1e-6).
  **Logica del test:** training set = test set + massima fedeltà del modello →
  le probabilità del modello M(t) devono coincidere con le frequenze empiriche L(t)
  per ogni traccia distinta → `max(L(t) - M(t), 0) = 0` per ogni t → uEMSC = 1.
  **Implementazione:** per catturare stdout, usare `System.setOut()` con un
  `ByteArrayOutputStream`, poi ripristinare. Per evitare `System.exit()` nel main,
  wrappare in un SecurityManager custom o usare un approccio con ProcessBuilder
  che lancia il JAR come processo separato e cattura stdout.
- **Perche':** È la validazione definitiva che il pipeline completo (CSV parsing →
  suffix array → VLMC fitting → likelihood → uEMSC) produce risultati corretti.
  Se uEMSC ≠ 1 con training=test e alfa=1, c'è un bug nel pipeline.
- **Dipende da:** Task 1, Task 2
- **Criteri:** Il test passa. uEMSC = 1.0 (±1e-6). Il test è deterministico
  (il CSV è generato programmaticamente, non random).
- **Verifica:** `mvn test -Dtest=UemscEndToEndTest -q`

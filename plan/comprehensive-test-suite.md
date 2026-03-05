# Piano: Test suite completa per VLMC learning pipeline

- **Moduli:** vlmc, fitvlmc, suffixarray, test
- **Stima:** 8 task, ~6 file
- **Data:** 2026-03-05

## Contesto

Il coverage attuale dei test e' molto basso. I test esistenti sono:
- `SaTest.java` / `SaTestInt.java` — test manuali (non JUnit) con path hardcoded
- `CsvEventLogReaderTest.java` — 10 test JUnit per il CSV reader
- `LikelihoodTest.java` — 7 test JUnit su VLMC costruita a mano

Mancano completamente test per:
- Suffix array con JUnit (count, first, last)
- `createNextSymbolDistribution` (normalizzazione, copertura)
- `KullbackLeibler` (NaN/Infinity guard, correttezza)
- Pruning (`prune()`, interazione con cutoff e k)
- `getState` (navigazione albero, longest prefix match)
- Serializzazione/deserializzazione VLMC (round-trip)
- Pipeline end-to-end (CSV -> fitting -> likelihood -> uEMSC)
- `VlmcRoot.createNextSymbolDistribution` (duplicato non normalizzato — BUG da fixare)

### Bug trovato durante analisi

`VlmcRoot.createNextSymbolDistribution()` (riga 292) e' una copia del metodo
di `EcfNavigator` ma **senza la normalizzazione** appena aggiunta nel Task 3 del
piano fix-vlmc-learning-bugs. Usata da `simulateSA()`. Va fixata per consistenza.

## Task 1: Fix normalizzazione duplicata in VlmcRoot.createNextSymbolDistribution

- **Package:** vlmc
- **File:** src/main/java/vlmc/VlmcRoot.java
- **Cosa:** Applicare lo stesso fix di normalizzazione del Task 3 di fix-vlmc-learning-bugs
  al metodo `createNextSymbolDistribution(ArrayList<String> ctx, fitVlmc learner)`
  (riga 292). Il pattern e' identico:
  - Accumulare `usedCtx` invece di dividere per `totalCtx`
  - Dopo il loop, normalizzare dividendo per `usedCtx`
  - Aggiornare `dist.totalCtx = usedCtx`
- **Perche':** E' una copia del codice in EcfNavigator che non ha ricevuto il fix.
  Usata dalla simulazione (`simulateSA`). Senza fix, le probabilita' di simulazione
  sono inconsistenti con quelle del modello.
- **Dipende da:** nessuno
- **Criteri:** Il metodo produce distribuzioni normalizzate (somma = 1.0).
- **Verifica:** `mvn compile -q`

## Task 2: Test JUnit per SuffixArray

- **Package:** test
- **File:** src/test/java/test/SuffixArrayTest.java
- **Cosa:** Creare test JUnit per `SuffixArray` che verifichino:
  1. **count() con pattern presente** — `sa.count("A B")[1]` ritorna il numero corretto
     di occorrenze nel testo
  2. **count() con pattern assente** — `sa.count("X Y")[1]` ritorna -1 o 0
  3. **count() con pattern singolo** — un solo token
  4. **count() consistenza** — per un testo noto, verificare che
     `count("A B C")[1] <= count("A B")[1]` (un pattern piu' lungo non puo' avere
     piu' occorrenze)
  5. **first() e last()** — indici coerenti con count
  6. **Testo con tracce multiple** — formato `"A B end$ C D end$"`, verificare che
     `count("A B")[1]` conta solo dentro la prima traccia e non attraversa `end$`

  Costruire il suffix array su stringhe note, calcolare i risultati attesi a mano.
  Esempio testo: `"A B C end$ A B D end$ A C end$"`
  - `count("A B")` = 2 (prima e seconda traccia)
  - `count("A C")` = 1 (terza traccia)
  - `count("A B C")` = 1 (solo prima traccia)
  - `count("B C")` = 1
  - `count("X")` = -1 o 0
- **Perche':** Il suffix array e' il fondamento di tutto il learning. Se count() e'
  sbagliato, le probabilita' saranno sbagliate. Non ha un singolo test JUnit.
- **Dipende da:** nessuno
- **Criteri:** Tutti i test passano. Valori attesi calcolati a mano.
- **Verifica:** `mvn test -Dtest=SuffixArrayTest -q`

## Task 3: Test JUnit per NextSymbolsDistribution

- **Package:** test
- **File:** src/test/java/test/NextSymbolsDistributionTest.java
- **Cosa:** Creare test JUnit per `NextSymbolsDistribution`:
  1. **getProbBySymbol() — simbolo presente** — ritorna la probabilita' corretta
  2. **getProbBySymbol() — simbolo assente** — ritorna `null`
  3. **Distribuzione normalizzata** — somma delle probabilita' = 1.0 (entro 1e-10)
  4. **toString() e round-trip** — serializzare la distribuzione con `toString()`,
     poi parsarla con `VlmcRoot.parseNextSymbolDistribution()` e verificare che
     simboli e probabilita' sono preservati
  5. **Distribuzione vuota** — nessun simbolo, getProbBySymbol ritorna null per tutto
  6. **Distribuzione con un solo simbolo** — P = 1.0
- **Perche':** La distribuzione e' usata ovunque (learning, likelihood, simulazione,
  pruning). Bug nella serializzazione possono causare modelli corrotti dopo save/load.
- **Dipende da:** nessuno
- **Criteri:** Tutti i test passano.
- **Verifica:** `mvn test -Dtest=NextSymbolsDistributionTest -q`

## Task 4: Test JUnit per KullbackLeibler e pruning

- **Package:** test
- **File:** src/test/java/test/PruningTest.java
- **Cosa:** Creare test JUnit per `VlmcNode.KullbackLeibler()` e `VlmcNode.prune()`:
  1. **KL con distribuzioni identiche** — figlio e padre hanno la stessa distribuzione
     -> KL = 0.0
  2. **KL con distribuzioni diverse** — valore calcolato a mano
     Es: figlio P(A)=0.8, P(B)=0.2; padre P(A)=0.5, P(B)=0.5
     KL = 0.8*ln(0.8/0.5) + 0.2*ln(0.2/0.5) = 0.8*0.47 + 0.2*(-0.916) = 0.193
  3. **KL con simbolo a probabilita' 0 nel figlio** — il termine e' skippato, no NaN
  4. **KL con simbolo assente nel padre** — il termine e' skippato, no NaN/Infinity
  5. **KL non ritorna mai NaN o Infinity** — fuzz test con distribuzioni random
  6. **prune() rimuove nodo quando KL*totalCtx <= cutoff** — costruire parent+child
     con KL basso e cutoff alto, verificare che il nodo viene rimosso
  7. **prune() mantiene nodo quando KL*totalCtx > cutoff** — distribuzioni molto diverse,
     cutoff basso, nodo non viene rimosso
  8. **prune() non rimuove figli della root** — nodi con parent=root non vengono mai prunati

  Per testare prune(), bisogna settare i campi statici `fitVlmc.cutoff` e `fitVlmc.k`
  prima di ogni test e ripristinarli dopo (usare `@BeforeEach`/`@AfterEach`).
- **Perche':** Il pruning determina la struttura dell'albero. Bug nel pruning (come
  il confronto stringa con `!=` appena fixato) hanno impatto diretto sulla qualita'
  del modello.
- **Dipende da:** nessuno
- **Criteri:** Tutti i test passano. Nessun test produce NaN o Infinity.
- **Verifica:** `mvn test -Dtest=PruningTest -q`

## Task 5: Test JUnit per getState (navigazione albero)

- **Package:** test
- **File:** src/test/java/test/VlmcNavigationTest.java
- **Cosa:** Creare test JUnit per `VlmcRoot.getState()` e `getLikelihood()`:
  1. **getState con contesto esatto** — albero con root -> A -> B, ctx=[A,B]
     -> ritorna nodo B
  2. **getState con contesto parziale** — albero con root -> A (no figli),
     ctx=[A,B] -> ritorna nodo A (longest prefix match)
  3. **getState con contesto sconosciuto** — ctx=[X] -> ritorna root
  4. **getState con contesto lungo** — albero profondo 3 livelli,
     ctx=[A,B,C] -> ritorna nodo piu' profondo raggiungibile
  5. **getLikelihood coerenza con getState** — per una traccia nota, verificare che
     la likelihood calcolata manualmente (navigando getState + getProbBySymbol)
     corrisponde al risultato di getLikelihood()
  6. **getLikelihood con simbolo non nel modello** — p=0, break anticipato

  Costruire VLMC a mano (stesso pattern di LikelihoodTest.java) ma con albero
  piu' profondo (3 livelli) per testare il longest prefix match.

  Albero di test:
  ```
  root
  +-- "A" P(B)=0.7, P(end$)=0.3
  |   +-- "B" P(A)=0.5, P(end$)=0.5   (contesto: dopo B->A, cioe' ctx=[A,B])
  +-- "B" P(A)=0.6, P(end$)=0.4
  ```
  Traccia: [A, B, A, end$]
  - Step 0: state=getState([A])=nodo A, P(B|A)=0.7
  - Step 1: state=getState([A,B])=nodo A->B, P(A|A->B)=0.5
  - Step 2: state=getState([A,B,A])=?? longest match -> dipende dalla struttura

- **Perche':** `getState` e' il cuore della likelihood e della predizione. Se il
  longest prefix match non funziona, tutte le probabilita' downstream sono sbagliate.
- **Dipende da:** nessuno
- **Criteri:** Tutti i test passano. La navigazione segue il longest prefix match.
- **Verifica:** `mvn test -Dtest=VlmcNavigationTest -q`

## Task 6: Test serializzazione/deserializzazione VLMC round-trip

- **Package:** test
- **File:** src/test/java/test/VlmcSerializationTest.java
- **Cosa:** Creare test JUnit che verifichino il round-trip di serializzazione:
  1. **Round-trip base** — costruire VLMC a mano, serializzare con `toString()`,
     scrivere su file temporaneo, deserializzare con `parseVLMC()`, confrontare
     struttura (numero nodi, label, distribuzioni)
  2. **Round-trip con albero profondo** — 3 livelli di profondita'
  3. **Round-trip preserva probabilita'** — verificare che le probabilita' sono
     identiche (entro 1e-6, considerando la formattazione %f)
  4. **Round-trip preserva totalCtx** — verificare che totalCtx e' preservato
  5. **Likelihood invariante al round-trip** — calcolare likelihood su VLMC originale
     e su VLMC dopo round-trip, devono essere identiche

  Usare `@TempDir` per i file temporanei.
  Nota: `toString()` del VlmcRoot scrive il formato testuale. Verificare che
  `parseVLMC()` lo rilegge correttamente.
- **Perche':** Il pipeline reale salva il modello su disco e lo ricarica per la
  likelihood. Se la serializzazione perde precisione o corrompe la struttura, i
  risultati sono sbagliati. Il test sulla uEMSC=0.991 potrebbe essere impattato
  da perdita di precisione nel round-trip.
- **Dipende da:** nessuno
- **Criteri:** Tutti i test passano. Le likelihood pre e post round-trip coincidono.
- **Verifica:** `mvn test -Dtest=VlmcSerializationTest -q`

## Task 7: Test end-to-end sintetico (CSV -> fit -> likelihood -> uEMSC = 1)

- **Package:** test
- **File:** src/test/java/test/EndToEndTest.java
- **Cosa:** Test JUnit che valida l'intero pipeline:
  1. **Genera un CSV sintetico** con 1000 tracce. Il CSV ha formato process mining
     (`case_id,activity,timestamp`). Le tracce sono generate da un alfabeto piccolo
     (3 attivita': A, B, C) con 5 pattern distinti e frequenze note:
     ```
     A B C      400 tracce (40%)
     A C        300 tracce (30%)
     B A C      150 tracce (15%)
     B C        100 tracce (10%)
     A B A C     50 tracce  (5%)
     ```
     Profondita' massima = 4, ben sotto maxdepth=25.
     I nomi attivita' sono senza underscore (evitare il problema label2Ctx).
     Usare `@TempDir` per tutti i file temporanei.
  2. **Esegui il fitting** invocando il main con:
     `--infile <csv> --csv-case case_id --csv-activity activity --csv-timestamp timestamp
      --vlmcfile <tmp.vlmc> --alfa 1 --nsim 1`
     Catturare stdout con `System.setOut()`.
  3. **Esegui la likelihood + uEMSC** invocando il main con:
     `--vlmc <tmp.vlmc> --lik <csv> --csv-case case_id --csv-activity activity
      --csv-timestamp timestamp`
     Catturare stdout.
  4. **Verifica** che:
     - Il fitting completa senza errori
     - L'output contiene "uEMSC" con valore numerico
     - uEMSC >= 0.99 (tolleranza per effetti di contesto e k=1)
     - Se uEMSC < 0.99, stampare diagnostica dettagliata (quali tracce hanno
       M(t) != L(t)) per aiutare il debug

  **ATTENZIONE al main:** il main di fitVlmc usa `System.exit()` in alcuni error
  path e modifica stato statico (`fitVlmc.cutoff`, `fitVlmc.k`, etc.). Per evitare
  problemi:
  - Salvare e ripristinare tutti i campi statici con `@BeforeEach`/`@AfterEach`
  - Usare `System.setOut()`/`System.setErr()` per catturare output
  - Non aspettarsi che `System.exit()` venga chiamato nei path normali

  **Se il main non e' facilmente invocabile** (classpath, System.exit, stato globale),
  usare un approccio alternativo:
  - Costruire manualmente le componenti: CsvEventLogReader -> content string ->
    SuffixArray + Trace2EcfIntegrator -> EcfNavigator -> VLMC -> getLikelihood
  - Questo testa le stesse componenti senza dipendere dal CLI parsing
- **Perche':** E' la validazione definitiva che il pipeline completo produce
  risultati corretti. Se uEMSC != 1 con training=test e alfa=1, c'e' un bug.
- **Dipende da:** Task 1 (normalizzazione VlmcRoot)
- **Criteri:** Il test passa. uEMSC >= 0.99.
- **Verifica:** `mvn test -Dtest=EndToEndTest -q`

## Task 8: Test uEMSC formula isolata

- **Package:** test
- **File:** src/test/java/test/UemscTest.java
- **Cosa:** Test JUnit che verifica il calcolo uEMSC su VLMC costruita a mano:
  1. **Conformance perfetta** — log con tracce le cui frequenze corrispondono
     esattamente alle probabilita' del modello -> uEMSC = 1.0
     Modello: P(A B end$) = 0.6, P(A C end$) = 0.4
     Log: 600 tracce "A B end$", 400 tracce "A C end$"
     L(t) = M(t) per ogni t -> surplus = 0 -> uEMSC = 1.0
  2. **Conformance parziale** — log con alcune tracce piu' frequenti del modello
     Modello: P(A B end$) = 0.6, P(A C end$) = 0.4
     Log: 800 "A B end$", 200 "A C end$"
     L("A B end$") - M("A B end$") = 0.8 - 0.6 = 0.2
     L("A C end$") - M("A C end$") = 0.2 - 0.4 = -0.2 (clamped a 0)
     surplus = 0.2, uEMSC = 0.8
  3. **Traccia sconosciuta** — log con traccia non nel modello (M(t)=0)
     Log: 500 "A B end$", 500 "X Y end$"
     L("A B end$") - M("A B end$") = 0.5 - 0.6 = -0.1 (clamped)
     L("X Y end$") - M("X Y end$") = 0.5 - 0 = 0.5
     surplus = 0.5, uEMSC = 0.5
  4. **Log con una sola traccia distinta** — caso degenere
  5. **uEMSC sempre in [0, 1]** — verifica bounds

  Per calcolare M(t), usare `vlmc.getLikelihood(trace)` sulla VLMC costruita a mano
  (stessa tecnica di LikelihoodTest.java).

  Estrarre la logica uEMSC dal blocco --lik di fitVlmc.java in un metodo statico
  utility (es. `UemscCalculator.compute(VlmcRoot vlmc, List<List<String>> traces)`)
  per poterlo testare unitariamente. Aggiornare fitVlmc.java per usare il nuovo
  metodo.
- **Perche':** La uEMSC coinvolge raggruppamento, frequenze, e confronto con
  probabilita' del modello. Errori numerici devono essere catturati.
- **Dipende da:** nessuno
- **Criteri:** Tutti i test passano. Valori attesi calcolati a mano.
- **Verifica:** `mvn test -Dtest=UemscTest -q`

# Piano: STA — Suffix Tree Attention PoC

- **Moduli:** jfitvlmc (nuovo package `sta`), test
- **Stima:** 10 task, ~12 file nuovi + 0 file modificati nel core
- **Data:** 2026-03-05
- **Branch:** `feat/sta-suffix-tree-attention`

## Contesto e Motivazione

### Problema
La VLMC classica usa un singolo contesto (longest match) per l'inferenza.
Se il contesto esatto non e' stato visto in training, cade a contesti molto corti
perdendo informazione parziale disponibile nei contesti intermedi.

### Soluzione: STA (Suffix Tree Attention)
Combinare TUTTI i contesti matchati lungo il cammino nell'albero VLMC,
pesandoli con una funzione information-theoretic. Questo aumenta il potere
di generalizzazione senza modificare il training.

### Formalizzazione

Sia h = (h_1, ..., h_t) la storia osservata. Il cammino nel context tree
produce i contesti matchati C(h) = {c_0, c_1, ..., c_k} dove:
- c_0 = root (contesto vuoto)
- c_k = longest match (quello che usa la VLMC classica)
- c_i ha profondita' i nell'albero

La distribuzione STA e':
```
P_sta(next | h) = SUM_i  w(c_i, h) * P(next | c_i)
con vincolo: SUM_i w(c_i, h) = 1,  w(c_i, h) >= 0
```

Funzione peso (variante alpha, KL-based):
```
w(c_i) = softmax( beta * I(c_i) )

dove I(c_i) = log(n_ci) * KL(P_ci || P_parent(ci))
     I(c_0) = 0  (root, baseline)

softmax: w(c_i) = exp(beta * I(c_i)) / SUM_j exp(beta * I(c_j))
```

Proprieta':
- beta -> infinito: converge a VLMC classica (winner-take-all)
- beta -> 0: media uniforme su tutti i contesti
- beta finito: interpolazione smooth information-theoretic

### Target applicativo
Log-based anomaly detection (confronto con DeepLog, CCS 2017).
Il PoC usa un toy example cybersecurity per validare il meccanismo.

---

## Fase 1: Data model e strutture dati STA

### Task 1: Creare StaResult — risultato dell'inferenza STA
- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/StaResult.java`
- **Cosa:** Classe che rappresenta il risultato di un'inferenza STA. Contiene:
  - `NextSymbolsDistribution mixedDistribution` — distribuzione combinata pesata
  - `List<ContextContribution> contributions` — lista dei contesti con i loro pesi
  - Metodo `getAnomalyScore(String observedSymbol)` — ritorna -log(P_sta(symbol))
  - Metodo `getContextRelevanceMap()` — ritorna la mappa simbolo -> peso per visualizzazione
- **Perche':** Separare il risultato dalla logica permette di testare indipendentemente e di estendere (es. aggiungere metriche senza toccare il predictor).
- **Dipende da:** nessuno
- **Criteri:** Classe compilabile, toString() leggibile, getters funzionanti
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 2: Creare ContextContribution — singolo contesto pesato
- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/ContextContribution.java`
- **Cosa:** Record-like class che rappresenta il contributo di un singolo contesto:
  - `List<String> context` — la sequenza di simboli del contesto (es. ["SF", "SS", "SU"])
  - `int depth` — profondita' nel tree
  - `double weight` — peso normalizzato w(c_i) dopo softmax
  - `double rawScore` — score prima del softmax (I(c_i) = log(n) * KL)
  - `double kl` — KL divergence dal parent
  - `double totalCtx` — n_c (osservazioni)
  - `NextSymbolsDistribution distribution` — distribuzione di questo contesto
- **Perche':** Questa classe e' il cuore dell'explainability — ogni contributo e' ispezionabile.
- **Dipende da:** nessuno
- **Criteri:** Classe compilabile, campi accessibili
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 3: Creare StaWeightFunction — interfaccia per funzioni peso
- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/StaWeightFunction.java`
- **Cosa:** Interfaccia funzionale per calcolare i pesi dei contesti:
  ```java
  @FunctionalInterface
  public interface StaWeightFunction {
      double score(VlmcNode node, VlmcNode parent);
  }
  ```
  Piu' una implementazione statica factory per la variante alpha:
  ```java
  static StaWeightFunction klBased() {
      return (node, parent) -> {
          if (parent == null || parent instanceof VlmcRoot) return 0.0;
          double kl = node.KullbackLeibler();
          double n = node.getDist().totalCtx;
          return Math.log(Math.max(1, n)) * kl;
      };
  }
  ```
- **Perche':** Interfaccia funzionale permette di swappare facilmente tra varianti alpha/beta/gamma senza modificare StaPredictor. Future varianti: aggiungere solo un nuovo factory method.
- **Dipende da:** nessuno (usa tipi esistenti VlmcNode, VlmcRoot)
- **Criteri:** Interfaccia compilabile, factory method klBased() ritorna istanza valida
- **Verifica:** `mvn compile -pl jfitvlmc -q`

---

## Fase 2: Core STA — il predictor

### Task 4: Creare StaPredictor — cuore dell'inferenza STA
- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/StaPredictor.java`
- **Cosa:** Classe principale che implementa l'inferenza STA. Responsabilita':
  1. `collectMatchedContexts(VlmcRoot tree, List<String> history)` — cammina il tree come `getState()` ma raccoglie TUTTI i nodi lungo il cammino (non solo il longest match)
  2. `computeWeights(List<VlmcNode> contexts, double beta, StaWeightFunction fn)` — calcola i pesi softmax
  3. `mixDistributions(List<VlmcNode> contexts, double[] weights)` — combina le distribuzioni pesate
  4. `predict(VlmcRoot tree, List<String> history)` — metodo pubblico che orchestra 1-2-3 e ritorna StaResult

  Dettaglio `mixDistributions`:
  - Raccoglie l'unione di tutti i simboli presenti in almeno una distribuzione
  - Per ogni simbolo: P_mix(s) = SUM_i w_i * P_i(s), dove P_i(s) = 0 se il simbolo non e' nel supporto di c_i
  - Normalizza (dovrebbe gia' sommare a 1, ma safety check)

  Parametri configurabili:
  - `double beta` — temperatura softmax (default: 1.0)
  - `StaWeightFunction weightFunction` — funzione peso (default: klBased)
  - `boolean includeRoot` — se includere il root nella mixture (default: true)

- **Perche':** Questo e' l'unico file che implementa logica nuova. Tutto il resto e' strutture dati e test.
- **Dipende da:** Task 1, 2, 3
- **Criteri:**
  - Con beta molto grande, il risultato converge a quello di `getState()` (VLMC classica)
  - La distribuzione mixata e' normalizzata (somma = 1.0 ± epsilon)
  - La lista contributions ha pesi che sommano a 1.0
  - Nessun NaN o Infinity nei pesi
- **Verifica:** `mvn compile -pl jfitvlmc -q`

---

## Fase 3: Unit test STA

### Task 5: Test StaPredictor — casi base e proprieta' matematiche
- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/StaPredictorTest.java`
- **Cosa:** Test JUnit 5 per StaPredictor. Costruisce VLMC a mano (come in LikelihoodTest/PruningTest) e verifica:
  1. **Convergenza a VLMC classica:** con beta=100, P_sta == P_vlmc_classica (entro epsilon)
  2. **Media uniforme:** con beta=0, tutti i pesi sono uguali (1/k)
  3. **Normalizzazione:** per ogni predizione, SUM P_sta(s) == 1.0
  4. **Pesi normalizzati:** SUM w_i == 1.0
  5. **Root ha peso:** il root contribuisce alla mixture (con score=0 ha peso base)
  6. **Contesti raccolti:** per un tree di profondita' 3, collectMatchedContexts ritorna 3-4 nodi
  7. **Simbolo non nel supporto:** se un simbolo appare solo in un contesto intermedio, ha probabilita' > 0 nella mixture (generalizzazione!)
  8. **Monotonicita':** contesto con KL alto e n alto ha peso >= contesto con KL basso e n basso

  Strategia: costruire un VlmcRoot con 2-3 livelli di profondita', distribuzioni note,
  e verificare le proprieta' analiticamente.

- **Perche':** Questi test validano le proprieta' matematiche di STA prima del toy example.
- **Dipende da:** Task 4
- **Criteri:** Tutti i test passano, nessun test flaky
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=StaPredictorTest -q`

### Task 6: Test StaWeightFunction — verifica funzioni peso
- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/StaWeightFunctionTest.java`
- **Cosa:** Test per le funzioni peso:
  1. **klBased su root:** ritorna 0.0
  2. **klBased su nodo con KL=0:** ritorna 0.0 (distribuzioni identiche al parent)
  3. **klBased su nodo con KL>0 e n>0:** ritorna valore positivo
  4. **Ordinamento:** nodo con KL alto e n alto ha score > nodo con KL basso
  5. **Nodo senza parent:** gestito senza exception

- **Perche':** La funzione peso e' il cuore della novita'. Deve essere testata in isolamento.
- **Dipende da:** Task 3
- **Criteri:** Tutti i test passano
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=StaWeightFunctionTest -q`

---

## Fase 4: Toy Example Cybersecurity

### Task 7: Generatore dataset sintetico cyber
- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/CyberDatasetGenerator.java`
- **Cosa:** Classe helper (usata dai test, non dal main) che genera tracce sintetiche.
  Alfabeto: SF, SS, SU, FR, FW, NC, PS, LO, CR, UP, BK, NR (12 simboli).

  **Dimensionamento dataset:**
  - 4000 tracce normali + 400 attacco per training (albero VLMC profondo 5-6 livelli)
  - 1000 tracce normali held-out per test (false positive rate)
  - 100 tracce varianti attacco per test (mai viste, generalizzazione)
  - Totale: ~5500 tracce. Necessario per avere >=10 osservazioni per contesti a profondita' 5+.

  **Pattern normali con variazioni naturali (4000 tracce training):**
  Ogni tipo base ha 3-4 varianti per creare un albero ricco con contesti
  a diversa profondita' e confidenza.

  Tipo 1 — Admin routine (25% = 1000 tracce, distribuite tra varianti):
  - SS SU UP BK LO                    base (40%)
  - SS SU UP UP BK LO                 doppio update (25%)
  - SS NR SU UP BK LO                 check pre-sudo (20%)
  - SS SU UP BK BK LO                 doppio backup (15%)

  Tipo 2 — Admin files (25% = 1000 tracce):
  - SS SU FR FW LO                    base (40%)
  - SS SU FR FW FW LO                 multi-write (25%)
  - SS SU FR FR FW LO                 multi-read (20%)
  - SS NR SU FR FW LO                 check pre-sudo (15%)

  Tipo 3 — User normale (30% = 1200 tracce):
  - SS NR NR NR LO                    base (35%)
  - SS NR NR NR NR LO                 sessione lunga (25%)
  - SS NR NR LO                       sessione corta (25%)
  - SS NR NR NR NR NR LO              sessione molto lunga (15%)

  Tipo 4 — Cron job (10% = 400 tracce):
  - CR BK LO                           base (50%)
  - CR BK BK LO                       multi-backup (30%)
  - CR UP LO                          cron-update (20%)

  Tipo 5 — Dev deployment (10% = 400 tracce):
  - SS SU PS FW FW LO                  base (40%)
  - SS SU PS FW FW FW LO              multi-file (30%)
  - SS SU PS PS FW FW LO              multi-process (30%)

  **Pattern attacco con variazioni (400 tracce training):**
  Tipo A — Brute force+exfil (40% = 160 tracce):
  - SF SF SF SF SS SU FR NC LO         base (50%)
  - SF SF SF SF SF SS SU FR NC LO      5 fail (25%)
  - SF SF SF SF SS SU FR FR NC LO      multi-read exfil (25%)

  Tipo B — Brute force lento (20% = 80 tracce):
  - SF SS SF SS SF SF SS SU FR NC LO   base (100%)

  Tipo C — Insider threat (25% = 100 tracce):
  - SS SU FR FR FR NC LO               base (60%)
  - SS SU FR FR FR FR NC LO            4 read (40%)

  Tipo D — Lateral movement (15% = 60 tracce):
  - SS PS NC PS NC LO                  base (50%)
  - SS PS NC PS NC PS NC LO            3 hop (50%)

  **Test set — tracce normali held-out (1000 tracce):**
  Stessi pattern e proporzioni delle normali training.
  Servono per misurare false positive rate.

  **Test set — varianti attacco MAI viste (100 tracce):**
  - V1: SF SF SS SU FR NC LO              brute 2-fail (~20)
  - V2: SF SF SF SF SF SF SS SU FR NC LO  brute 6-fail (~15)
  - V3: SS SU FR NC LO                    insider semplificato (~20)
  - V4: SF SF SS SU PS NC LO              mix brute+lateral (~15)
  - V5: SS PS NC PS NC PS NC PS NC LO     lateral 4-hop (~15)
  - V6: SF SF SF SF SS SU FR FW NC LO     brute+write+exfil (~15)

  **Metodi pubblici:**
  - `generateTrainingNormal()` → List<List<String>> (4000 tracce)
  - `generateTrainingAttack()` → List<List<String>> (400 tracce)
  - `generateTestNormal()` → List<List<String>> (1000 tracce)
  - `generateTestAttackVariants()` → List<List<String>> (100 tracce, con label variante)
  - `writeTracesFile(List<List<String>> traces, File output)` → scrive formato jfitVLMC
  - Seed fisso (`new Random(42)`) per riproducibilita'

  Output: formato tracce jfitVLMC (spazi, terminato da "end$", una traccia per riga).

- **Perche':** Dataset controllato e dimensionato per produrre un albero VLMC
  profondo con contesti a diversi livelli di confidenza. Le variazioni naturali
  nelle tracce normali creano contesti intermedi dove STA puo' dimostrare
  il suo vantaggio. Le varianti di attacco testano la generalizzazione.
- **Dipende da:** nessuno
- **Criteri:** Genera file parsabile dal nostro pipeline. Tracce corrette. Numeri rispettano le proporzioni (±5%).
- **Verifica:** `mvn compile -pl jfitvlmc -q` (e' una classe test helper)

### Task 8: Test E2E comparativo VLMC vs STA su dataset cyber
- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/StaCyberTest.java`
- **Cosa:** Test end-to-end che:
  1. Genera dataset con CyberDatasetGenerator:
     - Training: 4000 normali + 400 attacco → file tracce
     - Test normali: 1000 tracce held-out
     - Test varianti: 100 tracce attacco mai viste (6 varianti)
  2. Addestra VLMC con il pipeline esistente (fitVlmc/EcfNavigator) sulle 4400 tracce training
  3. Per ogni traccia di test (normali + varianti attacco):
     a. Calcola anomaly score con VLMC classica: -log(P_vlmc(trace))
     b. Calcola anomaly score con STA: -log(P_sta(trace))
     c. Per le varianti attacco: calcola il context relevance map con STA
  4. Calcola metriche aggregate:
     a. Mean anomaly score normali (VLMC vs STA) — STA dovrebbe essere piu' basso
     b. Mean anomaly score varianti attacco (VLMC vs STA) — STA dovrebbe essere piu' alto
     c. Separation ratio: mean_attack / mean_normal — STA dovrebbe avere ratio piu' alto
     d. Sweep su beta in [0.1, 0.5, 1.0, 2.0, 5.0, 10.0] per trovare il migliore
  5. Stampa report comparativo su stdout (per analisi manuale)

  NOTA: Questo test NON usa il JAR (non e2e via CLI). Usa direttamente le classi
  Java per costruire il tree e testare STA. Cosi' gira come unit test senza fat JAR.

  Strategia di costruzione VLMC nel test:
  - Scrivere tracce training (normali + attacco) su file temporaneo (formato interno: spazi + end$)
  - Usare Trace2EcfIntegrator per generare ECF dalle tracce
  - Usare fitVlmc pipeline (SuffixArray + EcfNavigator) per costruire il tree
  - Passare il VlmcRoot a StaPredictor per inference sulle tracce di test
  - Usare alfa basso (0.01) per avere albero profondo (poco pruning)

- **Perche':** Questo e' IL test che dimostra il valore di STA. Se STA non separa
  meglio le varianti di attacco dalle tracce normali, l'idea non funziona.
  Il dataset e' dimensionato per avere abbastanza contesti a profondita' 5+.
- **Dipende da:** Task 4, 7
- **Criteri:**
  - STA ha separation ratio >= VLMC classica per almeno un valore di beta
  - Il miglior beta e' nel range [0.5, 10] (non degenera agli estremi)
  - Le context relevance map delle varianti attacco mostrano contesti interpretabili
    (es. per V1 "brute 2-fail": il contesto [SF SF SS] ha peso significativo)
  - Test robusto (non flaky, seed fisso per RNG in CyberDatasetGenerator)
  - Tempo di esecuzione < 60 secondi (4400 tracce training + 1100 test)
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=StaCyberTest -q`

---

## Fase 5: Utility e output

### Task 9: Anomaly scoring e output formattato
- **Package:** sta
- **File:** `jfitvlmc/src/main/java/sta/StaAnomalyScorer.java`
- **Cosa:** Classe utility che dato un VlmcRoot e una lista di tracce:
  1. Calcola per ogni traccia: anomaly score STA (per-trace e per-prefix)
  2. Calcola per ogni traccia: context relevance map (quali eventi passati pesano)
  3. Formatta output leggibile:
     ```
     Trace: SF SF SS SU FR NC LO
     Anomaly score (STA):  4.23
     Anomaly score (VLMC): 3.01
     Context relevance at t=5 (FR):
       [SU FR]         w=0.35  KL=2.1  n=45
       [SS SU FR]      w=0.40  KL=2.8  n=30
       [SF SF SS SU FR] w=0.15  KL=3.5  n=5
       [FR]            w=0.10  KL=0.3  n=200
     ```
  4. Opzionale: output CSV per analisi esterna

- **Perche':** Senza un output leggibile il PoC non e' dimostrabile.
  Questa classe sara' usata dal test e2e e in futuro dal CLI.
- **Dipende da:** Task 4
- **Criteri:** Output leggibile e corretto. CSV parsabile.
- **Verifica:** `mvn compile -pl jfitvlmc -q`

### Task 10: Documentazione interna e quality gates
- **Package:** sta, build
- **File:** Nessun file nuovo. Aggiornamenti:
  - `spotbugs-exclude.xml` (root e jfitvlmc): aggiungere esclusioni se necessario per il package sta
  - Verificare che Spotless formatta correttamente i nuovi file
  - Verificare che JaCoCo copre il nuovo package
- **Cosa:**
  1. Eseguire `mvn clean verify` e verificare che tutti i quality gates passano
  2. Verificare coverage del package `sta` (target: >= 80% line coverage per il nuovo codice)
  3. Fix eventuali warning SpotBugs nei file nuovi
  4. Verificare che i test totali (unit + e2e) passano tutti

- **Perche':** Il nuovo codice deve rispettare gli stessi standard del codebase esistente.
- **Dipende da:** Task 1-9
- **Criteri:** `mvn clean verify` passa senza errori. Nessun warning SpotBugs nel package sta.
- **Verifica:** `mvn clean verify`

---

## Riepilogo file nuovi

| File | Tipo | Package |
|------|------|---------|
| `jfitvlmc/src/main/java/sta/StaResult.java` | Data class | sta |
| `jfitvlmc/src/main/java/sta/ContextContribution.java` | Data class | sta |
| `jfitvlmc/src/main/java/sta/StaWeightFunction.java` | Interface + factory | sta |
| `jfitvlmc/src/main/java/sta/StaPredictor.java` | Core logic | sta |
| `jfitvlmc/src/main/java/sta/StaAnomalyScorer.java` | Utility | sta |
| `jfitvlmc/src/test/java/test/StaPredictorTest.java` | Unit test | test |
| `jfitvlmc/src/test/java/test/StaWeightFunctionTest.java` | Unit test | test |
| `jfitvlmc/src/test/java/test/CyberDatasetGenerator.java` | Test helper | test |
| `jfitvlmc/src/test/java/test/StaCyberTest.java` | Integration test | test |

## File core NON modificati

Nessun file in `vlmc/`, `fitvlmc/`, `suffixarray/` viene modificato.
STA e' puramente additivo — usa il VlmcRoot esistente in sola lettura.

## Dipendenze esterne nuove

Nessuna. Tutto il codice usa solo:
- `vlmc.VlmcRoot`, `vlmc.VlmcNode`, `vlmc.NextSymbolsDistribution` (esistenti)
- `java.util.*`, `java.lang.Math` (stdlib)

## Rischi e mitigazioni

| Rischio | Probabilita' | Impatto | Mitigazione |
|---------|-------------|---------|-------------|
| KL = 0 per tutti i contesti (tree troppo pruned) | Media | STA degenera a media uniforme | Alfa basso (0.01) per avere albero profondo |
| Distribuzione mixata ha P=0 per simboli validi | Media | Falsi negativi | Root (distribuzione globale) incluso nella mixture come fallback |
| beta difficile da calibrare | Alta | Risultati sensibili al parametro | Sweep su beta in [0.1, 0.5, 1, 2, 5, 10] nel Task 8 |
| getParent() ritorna null per figli di root | Bassa | NPE nel calcolo KL | Check esplicito in StaWeightFunction |
| Performance: loop su contesti aggiunge overhead | Bassa | Inference piu' lenta | Max profondita' albero ~25, overhead trascurabile |
| Training su 4400 tracce troppo lento | Bassa | Test > 60s, CI timeout | Suffix array e' O(n log n), 4400 tracce corte dovrebbero completare in <10s |
| Contesti profondi hanno poche osservazioni anche con 4400 tracce | Media | STA non riesce a pesare bene | Le variazioni naturali nei pattern aumentano la copertura dei contesti intermedi |

## Ordine di esecuzione

```
Task 1, 2, 3 (paralleli) → Task 4 → Task 5, 6 (paralleli) → Task 7 → Task 8 → Task 9 → Task 10
```

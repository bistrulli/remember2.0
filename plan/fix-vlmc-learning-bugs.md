# Piano: Fix bug nell'algoritmo di learning VLMC

- **Moduli:** vlmc, fitvlmc
- **Stima:** 4 task, ~2 file
- **Data:** 2026-03-05

## Contesto

Code review dell'algoritmo di learning ha identificato 4 bug/issue:
1. Confronto stringa con `!=` nel pruning (può saltare il pruning)
2. `maxNavigationDepth` mai verificato nella ricorsione (rischio stack overflow su ECF ciclici)
3. Probabilità non normalizzate (sottostima likelihood e uEMSC)
4. KL-divergence produce NaN/Infinity con probabilità 0 (pruning imprevedibile)

## Task 1: Fix confronto stringa nel pruning

- **Package:** vlmc
- **File:** src/main/java/vlmc/VlmcNode.java
- **Cosa:** Alla riga 163, sostituire:
  ```java
  if (this.parent.getLabel() != "root"
  ```
  con:
  ```java
  if (!this.parent.getLabel().equals("root")
  ```
- **Perche':** `!=` confronta i riferimenti, non il contenuto. Funziona "per caso"
  con string literal interned, ma fallisce se il label viene costruito dinamicamente
  (es. da `String.valueOf()`, parsing da file). Se fallisce, il nodo non viene mai
  valutato per il pruning → albero inutilmente grande.
- **Dipende da:** nessuno
- **Criteri:** Il confronto usa `.equals()`. Compilazione OK.
- **Verifica:** `mvn compile -q`

## Task 2: Implementare il check maxNavigationDepth nella ricorsione

- **Package:** fitvlmc
- **File:** src/main/java/fitvlmc/EcfNavigator.java
- **Cosa:** Nel metodo `visit(Edge e, VlmcNode parent, ArrayList<String> ctx, int depth)`
  (riga 74), aggiungere all'inizio del metodo, dopo la creazione del nodo e della
  distribuzione (dopo riga 87), un check:
  ```
  if (depth >= maxNavigationDepth) return vn;
  ```
  Questo ferma la ricorsione restituendo il nodo come foglia (senza figli),
  prima del blocco che lancia la visita ricorsiva sui predecessori (righe 92-100).
  Il nodo avrà comunque la sua distribuzione calcolata, ma non esplorerà ulteriori
  contesti all'indietro.
- **Perche':** Il parametro `maxNavigationDepth` esiste ma non viene mai verificato.
  Per ECF ciclici, la ricorsione si ferma solo quando `checkIfObserved()` ritorna false
  (cioè il contesto non è nel suffix array). Se le tracce sono lunghe e ripetitive,
  questo potrebbe generare alberi enormi o stack overflow.
- **Dipende da:** nessuno
- **Criteri:** Con `--maxdepth 5`, l'albero VLMC non ha nodi a profondità > 5.
- **Verifica:** `mvn compile -q`

## Task 3: Normalizzare le probabilità nella distribuzione

- **Package:** fitvlmc
- **File:** src/main/java/fitvlmc/EcfNavigator.java
- **Cosa:** Nel metodo `createNextSymbolDistribution()` (riga 166), dopo il loop
  che calcola le probabilità (dopo riga 195), aggiungere un blocco di normalizzazione:
  calcolare la somma delle probabilità, e se la somma è > 0 e diversa da 1.0,
  dividere ogni probabilità per la somma.
  ```
  double sum = somma di tutte le probabilità
  if (sum > 0 && Math.abs(sum - 1.0) > 1e-10) {
      per ogni probabilità: p[i] = p[i] / sum
  }
  ```
  Inoltre, aggiornare `totalCtx` per riflettere solo i conteggi effettivamente usati
  (somma dei count dei simboli presenti), non il totalCtx originale che include
  transizioni non nel grafo ECF.
- **Perche':** Quando simboli successori nell'ECF hanno count=0 nel contesto corrente,
  vengono skippati ma `totalCtx` resta invariato. Risultato: `Σ P(next|ctx) < 1`.
  Questo sottostima la likelihood di tutte le tracce e impatta la uEMSC.
  La simulazione funziona perché `EnumeratedDistribution` normalizza internamente,
  mascherando il problema.
- **Dipende da:** nessuno
- **Criteri:** Dopo il fix, per ogni nodo VLMC la somma delle probabilità della
  distribuzione è 1.0 (entro tolleranza numerica).
- **Verifica:** `mvn compile -q`

## Task 4: Proteggere KL-divergence da NaN e Infinity

- **Package:** vlmc
- **File:** src/main/java/vlmc/VlmcNode.java
- **Cosa:** Nel metodo `KullbackLeibler()` (riga 143), aggiungere guard per i casi edge:
  - Se `P_child(symbol) == 0`: skip il termine (contributo = 0 per convenzione, `0 * log(0/y) = 0`)
  - Se `P_parent(symbol) == null` o `== 0`: skip il termine (il simbolo non esiste nel padre,
    non può contribuire alla divergenza)
  Il loop diventa:
  ```
  for symbol in this.dist.symbols:
      p_child = this.dist.getProbBySymbol(symbol)
      p_parent = this.parent.getDist().getProbBySymbol(symbol)
      if p_child == null || p_child == 0 || p_parent == null || p_parent == 0:
          continue
      KL += p_child * Math.log(p_child / p_parent)
  ```
- **Perche':** Senza guard, `Math.log(x/0)` = `+Infinity` e `0 * Math.log(0/y)` = `NaN`.
  In entrambi i casi il confronto `KL * totalCtx <= cutoff` diventa imprevedibile
  (NaN è sempre false per qualsiasi confronto, Infinity è sempre > cutoff).
  Con NaN: il nodo non viene mai pruned → albero più grande del necessario.
  Con Infinity: il nodo non viene mai pruned → stessa conseguenza.
- **Dipende da:** nessuno
- **Criteri:** `KullbackLeibler()` non ritorna mai NaN o Infinity.
- **Verifica:** `mvn compile -q`

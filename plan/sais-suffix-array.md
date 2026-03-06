# Piano: SA-IS Suffix Array Construction

- **Moduli:** suffixarray, fitvlmc, test
- **Stima:** 8 task, ~5 file
- **Data:** 2026-03-06
- **Branch:** `feat/sais-suffix-array`

## Contesto

Il costruttore `SuffixArray(String)` attuale usa `Arrays.sort()` con confronto character-by-character:
- Complessita': **O(n^2 log n)** nel caso peggiore (n confronti x n char ciascuno x log n sorting)
- Su BGL dataset (~milioni di caratteri), il training impiega minuti solo per il sorting
- Il `jstack` conferma: 100% CPU in `Suffix.compareTo()` durante `Arrays.sort()`

L'algoritmo SA-IS (Suffix Array Induced Sorting, Nong-Zhang-Chan 2009) costruisce il suffix array in **O(n)** tempo e O(n) spazio. Speedup atteso: 100-1000x su input grandi.

## Vincoli fondamentali

1. **L'API pubblica di `SuffixArray` NON deve cambiare** — il PST (EcfNavigator) usa solo:
   - `new SuffixArray(String text)` — costruttore
   - `count(String pat)` → `int[] {firstIndex, count}` — conteggio occorrenze
   - `first(int low, int high, String pat)` → `int` — prima occorrenza
   - `last(int low, int high, String pat)` → `int` — ultima occorrenza
   - `length()` → `int`
2. **La logica del PST in `EcfNavigator` e `fitVlmc` NON viene toccata**
3. **I test esistenti `SuffixArrayTest` (9 test) devono passare senza modifiche**
4. Il campo `Suffix[] suffixes` viene sostituito da `int[] sa` (array di indici), ma i metodi di ricerca (`count`, `first`, `last`) continuano a funzionare identicamente

## Strategia implementativa

L'implementazione segue il paper originale con queste scelte:
- Input: `char[]` estratto dalla stringa
- Output: `int[] sa` dove `sa[i]` = indice nel testo del i-esimo suffisso in ordine lessicografico
- La classe `Suffix` viene eliminata internamente (non era pubblica nel contract API)
- I metodi `first`/`last`/`count` vengono riscritti per lavorare su `int[] sa` + `char[] text` invece di `Suffix[]`
- Il metodo `compareTo` non serve piu' (il sorting e' fatto da SA-IS, non da comparison sort)

## Task 1: Implementare il core SA-IS come metodo statico privato

- **Package:** suffixarray
- **File:** `jfitvlmc/src/main/java/suffixarray/SuffixArray.java`
- **Cosa:** Aggiungere metodo `private static int[] buildSuffixArraySAIS(char[] text, int n)` che implementa l'algoritmo SA-IS:
  1. **Classificazione S/L**: scansione destra-sinistra, ogni posizione e' S-type (suffisso lessicograficamente minore del successivo) o L-type
  2. **Identificazione LMS**: posizioni S-type precedute da L-type (Left-Most S-type)
  3. **Bucket sort iniziale**: metti i suffissi LMS nei bucket corretti (basati sul primo carattere)
  4. **Induzione L-type**: scansione sinistra-destra, induci le posizioni L-type
  5. **Induzione S-type**: scansione destra-sinistra, induci le posizioni S-type
  6. **Riduzione ricorsiva**: se i suffissi LMS non sono tutti unici, crea stringa ridotta e richiama ricorsivamente
  7. **Ricostruzione**: usa l'ordine dei suffissi LMS dalla ricorsione per costruire il SA finale (ripetendo passi 3-5)

  Dettagli implementativi critici:
  - L'alfabeto deve gestire tutti i char possibili (0-65535) per sicurezza, ma in pratica le tracce VLMC usano un sottoinsieme piccolo
  - I bucket sono definiti dai caratteri: `bucketStart[c]` e `bucketEnd[c]` calcolati dall'istogramma dei caratteri
  - Il sentinel (fine stringa) e' implicitamente il carattere piu' piccolo — gestire con `text[n]` come valore speciale o aggiungere un sentinella '\0'
  - La ricorsione termina quando tutti i suffissi LMS sono unici (base case)

  **Pseudo-codice di riferimento:**
  ```
  function SAIS(T, n, sigma):
      // Step 1: classify each position as S or L type
      type[n-1] = S
      for i = n-2 downto 0:
          if T[i] > T[i+1]: type[i] = L
          elif T[i] < T[i+1]: type[i] = S
          else: type[i] = type[i+1]

      // Step 2: find LMS positions
      LMS = [i for i in 1..n-1 if type[i]==S and type[i-1]==L]

      // Step 3: induced sort with LMS seeds
      SA = inducedSort(T, LMS, type, buckets)

      // Step 4: check if LMS substrings are all unique
      // If not, create reduced problem and recurse
      // If yes, SA is correct
  ```

- **Perche':** Core dell'ottimizzazione — O(n) vs O(n^2 log n). Senza questo tutto il resto e' inutile.
- **Dipende da:** nessuno
- **Criteri:**
  1. `buildSuffixArraySAIS("abracadabra\0".toCharArray(), 12)` produce lo stesso suffix array dell'esempio Princeton
  2. Il metodo gestisce input con caratteri ripetuti, stringhe vuote, singolo carattere
  3. Il metodo termina in tempo lineare (nessun comparison sort)
- **Verifica:** `mvn compile -pl jfitvlmc -q` (solo compilazione, i test vengono al Task 3)

## Task 2: Riscrivere il costruttore e i metodi di ricerca

- **Package:** suffixarray
- **File:** `jfitvlmc/src/main/java/suffixarray/SuffixArray.java`
- **Cosa:** Modificare la struttura interna della classe:
  1. Sostituire `Suffix[] suffixes` con `int[] sa` (array di indici) e `char[] text` (il testo originale)
  2. Riscrivere il costruttore:
     ```java
     public SuffixArray(String text) {
         this.text = (text + '\0').toCharArray();  // sentinella
         this.n = this.text.length;
         this.sa = buildSuffixArraySAIS(this.text, this.n);
     }
     ```
  3. Riscrivere `count(String pat)`: usa `first()` e `last()` su `int[] sa` con confronto diretto su `char[]`
  4. Riscrivere `first(int low, int high, String pat)`: binary search su `sa[]`, confrontando `pat` con `text[sa[mid]..]`
  5. Riscrivere `last(int low, int high, String pat)`: analogo
  6. Riscrivere `length()`: ritorna `sa.length` (o `n`)
  7. Mantenere i metodi `index(int)`, `select(int)`, `rank(String)`, `lcp(int)` per backward compatibility — riscriverli per usare `sa[]` + `text[]`
  8. **Rimuovere** lo shuffle pre-sort (non serve piu')
  9. **Rimuovere** la classe `Suffix` interna (o deprecarla) — i metodi `getSuffixes()`, `myCompare(String, Suffix)`, `myCompare(String, Suffix, int)` vengono sostituiti
  10. Per `getSuffixes()`: se e' usato nei test, mantenerlo creando oggetti `Suffix` on-demand; altrimenti rimuoverlo

  **Funzione di confronto per binary search:**
  ```java
  private int comparePatternToSuffix(String pat, int suffixStart) {
      int patLen = pat.length();
      int suffixLen = n - suffixStart;
      int limit = Math.min(patLen, suffixLen);
      for (int i = 0; i < limit; i++) {
          char pc = pat.charAt(i);
          char sc = text[suffixStart + i];
          if (pc < sc) return -1;
          if (pc > sc) return +1;
      }
      // Per count/first/last: se il pattern e' prefisso del suffisso, e' un match (return 0)
      // Questo corrisponde al comportamento di myCompare attuale
      if (patLen <= suffixLen) return 0;
      return 1; // pattern piu' lungo del suffisso
  }
  ```

- **Perche':** Adatta l'interfaccia pubblica alla nuova rappresentazione interna. Il PST (EcfNavigator) chiama `count()` con pattern String — questa interfaccia resta invariata.
- **Dipende da:** Task 1
- **Criteri:**
  1. Tutti i metodi pubblici hanno la stessa firma di prima
  2. `count("A B")` su un testo noto ritorna lo stesso risultato di prima
  3. La classe non usa piu' `Arrays.sort` per il sorting dei suffissi
  4. `getSuffixes()` rimosso o adattato (verificare che nessun codice di produzione lo chiami)
- **Verifica:** `mvn compile -pl jfitvlmc -q`

## Task 3: Verificare che i test esistenti passano

- **Package:** test
- **File:** nessun file modificato — solo esecuzione
- **Cosa:** Eseguire tutti i test esistenti senza modifiche:
  1. `SuffixArrayTest` (9 test) — verifica che count, first, last funzionano come prima
  2. `EcfNavigatorTest` (3 test) — verifica che la costruzione VLMC via suffix array produce lo stesso albero
  3. `EndToEndTest` (4 test, e2e) — verifica pipeline completa learning-to-prediction
  4. `PruningTest` (11 test) — verifica che il pruning KL funziona (dipende indirettamente da SA per le distribuzioni)
  5. Full test suite: `mvn test` — nessuna regressione
- **Perche':** Il contratto e' che il PST non cambia. Se i test esistenti passano, il contratto e' rispettato.
- **Dipende da:** Task 2
- **Criteri:** Tutti i 154 test passano senza modifiche ai file test
- **Verifica:** `mvn test -q`

## Task 4: Test specifici per correttezza SA-IS

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/SaisCorrectnessTest.java`
- **Cosa:** Creare test class dedicata che verifica la correttezza dell'implementazione SA-IS:
  1. `testAbracadabra` — l'esempio classico: "abracadabra", verifica che gli indici del SA siano corretti confrontandoli con il risultato noto (10, 7, 0, 3, 5, 8, 1, 4, 6, 9, 2)
  2. `testAllSameCharacters` — "aaaaaaa": tutti i suffissi hanno lo stesso primo carattere, testa la ricorsione
  3. `testAlreadySorted` — "abcdefg": input gia' ordinato
  4. `testReverseSorted` — "gfedcba": worst case per alcuni algoritmi
  5. `testSingleCharacter` — "a": caso base
  6. `testTwoCharacters` — "ba": caso minimo non banale
  7. `testRepeatingPattern` — "abcabcabc": pattern ripetuto, forza ricorsione SA-IS
  8. `testVlmcTraceFormat` — usa un testo nel formato reale delle tracce VLMC ("A B C end$ A B D end$") e verifica che `count()` produce gli stessi risultati del vecchio algoritmo. Questo test costruisce sia il vecchio (se possibile) che il nuovo SA e confronta i risultati.
  9. `testLargeInput` — genera una stringa di 100K+ caratteri random e verifica che il SA e' valido: `sa[]` e' una permutazione di [0..n-1] e `text[sa[i]..] < text[sa[i+1]..]` per ogni i
  10. `testSuffixArrayIsSorted` — per ogni input, verifica che i suffissi sono effettivamente in ordine lessicografico: `text[sa[i]..] <= text[sa[i+1]..]`
- **Perche':** SA-IS e' un algoritmo complesso con molti edge case (caratteri ripetuti, ricorsione profonda, gestione sentinella). Serve coverage dedicata.
- **Dipende da:** Task 2
- **Criteri:** Tutti i 10 test passano. Nessun test dipende da infrastruttura esterna.
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=SaisCorrectnessTest -q`

## Task 5: Test di performance (non-regression)

- **Package:** test
- **File:** `jfitvlmc/src/test/java/test/SuffixArrayPerfTest.java`
- **Cosa:** Creare test che verifica che la costruzione e' significativamente piu' veloce:
  1. `testConstructionScalesLinearly` — costruisci SA per stringhe di dimensione 10K, 50K, 100K, 500K. Verifica che il tempo non cresce piu' che linearmente (t(500K)/t(50K) < 15, con margine). Usa `@Tag("e2e")` per non rallentare la suite unit.
  2. `testConstructionUnder1SecondFor1M` — una stringa da 1M caratteri deve completare in < 1 secondo. Questo e' il benchmark di accettazione. Usa `@Tag("e2e")`.
  3. `testCountPerformance` — dopo la costruzione, 10000 chiamate a `count()` con pattern di lunghezza variabile devono completare in < 1 secondo (verifica che la binary search non e' regredita).
- **Perche':** Il punto dell'ottimizzazione e' la performance. Senza un test di performance, potremmo avere un'implementazione SA-IS corretta ma lenta per bug implementativi.
- **Dipende da:** Task 4
- **Criteri:** I test di performance passano. Il tempo di costruzione per 1M caratteri e' < 1s.
- **Verifica:** `mvn verify -pl jfitvlmc -Dtest=SuffixArrayPerfTest -q`

## Task 6: Applicare la stessa ottimizzazione a SuffixArrayInt (opzionale)

- **Package:** suffixarray
- **File:** `jfitvlmc/src/main/java/suffixarray/SuffixArrayInt.java`
- **Cosa:** Adattare SA-IS per `SuffixArrayInt` che lavora su `ArrayList<Integer>` invece di `String`:
  1. Aggiungere `private static int[] buildSuffixArraySAIS(int[] data, int n, int alphabetSize)` — versione generica che accetta un array di interi e la dimensione dell'alfabeto
  2. Nel costruttore, convertire `ArrayList<Integer>` in `int[]` + aggiungere sentinella 0
  3. Riscrivere i metodi di ricerca analogamente al Task 2
  4. **NOTA:** `SuffixArrayInt` al momento non e' usato in produzione (`fitVlmc.java:486` ha la riga commentata `// this.sa = new SuffixArrayInt(content)`). Questo task e' opzionale ma previene bitrot.
- **Perche':** Mantiene le due varianti allineate. Se in futuro si torna a usare `SuffixArrayInt` (es. per evitare la conversione string↔integer), l'ottimizzazione e' gia' pronta.
- **Dipende da:** Task 1
- **Criteri:**
  1. `SuffixArrayIntTest` (8 test) passano senza modifiche
  2. Il costruttore usa SA-IS invece di `Arrays.sort`
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=SuffixArrayIntTest -q`

## Task 7: Fix Trace2EcfIntegrator stacktrace spam su label non numeriche

- **Package:** fitvlmc
- **File:** `jfitvlmc/src/main/java/fitvlmc/Trace2EcfIntegrator.java`
- **Cosa:** Il secondo blocco catch (riga 59-62) per `Integer.valueOf` nel ramo "stati successivi" stampa `System.err.println(s)` + `excep.printStackTrace()` per ogni label non numerica. Con dataset come BGL dove TUTTI gli eventi sono stringhe (KERNEL_INFO, APP_ERROR, etc.), questo produce migliaia di stacktrace su stderr.

  Fix: allineare il comportamento del secondo catch a quello del primo (riga 43-45):
  ```java
  // PRIMA (riga 57-62):
  try {
      p = s.split("_");
      e.setCost(Integer.valueOf(p[p.length - 1].replace("$", "")));
  } catch (java.lang.NumberFormatException excep) {
      System.err.println(s);       // ← SPAM
      excep.printStackTrace();     // ← SPAM
  }

  // DOPO:
  try {
      p = s.split("_");
      e.setCost(Integer.valueOf(p[p.length - 1].replace("$", "")));
  } catch (NumberFormatException excep) {
      // Non-numeric suffix (e.g. activity names) — cost stays 0
  }
  ```

  Inoltre, spostare il `p = s.split("_")` fuori dal try — non puo' lanciare NumberFormatException, e averlo dentro oscura il vero scopo del catch.

- **Perche':** Su dataset BGL (4.7M eventi), il catch attuale produce ~4.7M stacktrace su stderr, rendendo i log illeggibili e rallentando l'I/O. Il primo catch (riga 43-45) gestisce gia' lo stesso caso correttamente — sono due branch dello stesso if/else con handling inconsistente.
- **Dipende da:** nessuno
- **Criteri:**
  1. Con eventi stringa puri (es. "KERNEL_INFO APP_ERROR"), nessun output su stderr
  2. Con eventi con suffisso numerico (es. "state_42"), il cost viene settato correttamente
  3. I test esistenti `Trace2EcfIntegratorTest` (9 test) passano senza modifiche
- **Verifica:** `mvn test -pl jfitvlmc -Dtest=Trace2EcfIntegratorTest -q`

## Task 8: Quality gates finali

- **Package:** tutti
- **File:** tutti i file modificati nei task precedenti
- **Cosa:**
  1. `mvn spotless:apply` — formattare tutti i file modificati
  2. `mvn compile -q` — verifica compilazione
  3. `mvn test -q` — tutti i test unitari (154+ test)
  4. `mvn compile spotbugs:check -pl jfitvlmc -q` — static analysis
  5. `mvn verify -q` — coverage JaCoCo + test e2e
  6. Se SpotBugs segnala problemi (es. array bounds, null checks), fixarli
- **Perche':** Quality gates devono passare prima del merge.
- **Dipende da:** Task 1, 2, 3, 4, 5, 6, 7
- **Criteri:**
  1. Spotless: nessun file non formattato
  2. SpotBugs: nessun bug High
  3. Test: tutti passano
  4. Coverage: >= 35%
- **Verifica:** `mvn clean verify -q`

## Riferimenti

- Nong, G., Zhang, S., Chan, W.H. (2009). "Two Efficient Algorithms for Linear Time Suffix Array Construction". IEEE Transactions on Computers.
- Implementazione reference: [sais-lite](https://sites.google.com/site/yaborchemistry/sais) (C, ~200 righe)
- Sedgewick & Wayne, Algorithms 4th Ed., Section 6.3 — suffix array con comparison sort (implementazione attuale)

# Probabilistic Models Skill — jfitVLMC

Conoscenza specializzata su VLMC, catene di Markov a ordine variabile, e modelli probabilistici applicati al process mining.

## 1. Fondamenti Teorici

### 1.1 Catene di Markov a Ordine Variabile (VLMC)

Una **Variable Length Markov Chain** e' un processo stocastico dove la lunghezza del contesto (memoria) dipende dalla storia osservata, non e' fissa.

**Definizione formale:**
- Alfabeto finito A = {a_1, ..., a_m}
- Albero dei contesti T: insieme suffix-free di stringhe su A
- Per ogni contesto c in T: distribuzione condizionale P(·|c) su A
- La profondita' dell'albero determina l'ordine massimo del modello

**Differenza con MC a ordine fisso:**
- MC ordine k: usa sempre gli ultimi k simboli come contesto
- VLMC: usa il suffisso piu' lungo nel context tree che matcha la storia
- Vantaggio: parsimonia — contesti lunghi solo dove servono

**Riferimenti fondamentali:**
- Rissanen (1983) — Context tree come universal code
- Ron, Singer, Tishby (1996) — Prediction Suffix Trees (PST)
- Buhlmann & Wyner (1999) — VLMC con pruning basato su chi-square

### 1.2 Suffix Array e Conteggio Contesti

Il suffix array permette di contare efficientemente le occorrenze di un contesto nella sequenza di training.

- `count(ctx)` ritorna il numero di volte che il contesto `ctx` appare
- Complessita': O(|ctx| * log n) per query
- Usato per stimare P(symbol|ctx) = count(ctx + symbol) / count(ctx)

### 1.3 ECF (Electronic Control Flow)

L'ECF definisce la struttura del grafo di controllo:
- **Nodi** (Edge): stati del processo
- **Archi** (in/out): transizioni possibili
- Vincola quali transizioni la VLMC puo' modellare
- Puo' essere fornito o generato automaticamente dalle tracce

## 2. Algoritmo di Learning jfitVLMC

### 2.1 Pipeline

```
Tracce → [ECF generation] → SuffixArray → EcfNavigator.visit() → Pruning KL → VLMC Tree
```

### 2.2 Costruzione Albero (EcfNavigator)

Per ogni edge dell'ECF:
1. Crea nodo VLMC con label = edge
2. Estendi contesto all'indietro: ctx_new = [edge] + ctx
3. Calcola distribuzione next symbol via suffix array
4. Per ogni incoming edge: se `count(toVisit) >= k`, aggiungi child ricorsivo
5. Depth limit a `maxNavigationDepth` (default 25) per ECF ciclici

**Stima distribuzione** (`createNextSymbolDistribution`):
- P(symbol|ctx) = count(ctx + symbol) / sum_symbols(count(ctx + symbol))
- Stimatore: **Maximum Likelihood (MLE)**
- Fallback: distribuzione uniforme se nessuna osservazione

### 2.3 Pruning (VlmcNode.prune)

**Criterio attuale:**
```
Rimuovi nodo se: KL(child || parent) * n_child <= cutoff  OR  n_child < k
```

dove:
- KL = sum_s p_child(s) * log(p_child(s) / p_parent(s))
- n_child = totalCtx (conteggio osservazioni del contesto figlio)
- cutoff = chi2.inverseCumulativeProbability(alfa) / 2
- df del chi-square = max(0.1, |edges_totali| - 1)

**Interpretazione:** KL*n approssima la statistica del likelihood ratio test.
Il vero test chi-square sarebbe 2*n*KL ~ chi2(|symbols|-1), ma:
- Il fattore 2 e' assorbito dal `/2` nel cutoff
- I gradi di liberta' usano |edges| globali invece di |symbols_locali|-1

### 2.4 Parametri Chiave

| Parametro | CLI | Default | Ruolo |
|-----------|-----|---------|-------|
| alfa | `--alfa` | (required) | Soglia p-value per pruning (basso = albero profondo) |
| k | `--ntime` | 1 | Minimo osservazioni per considerare un contesto |
| maxDepth | `--maxdepth` | 25 | Profondita' massima navigazione ECF |

## 3. Limitazioni Note e Aree di Miglioramento

### 3.1 Pruning — Correttezza Formale

**Stato:** Il criterio `KL*n <= chi2(alfa, df_globale)/2` funziona in pratica ma non e' un test statistico formale.

**Problema dei gradi di liberta':**
- Attuale: df = |edges_totali| - 1 (globale, uguale per tutti i nodi)
- Corretto: df = |symbols_nel_nodo| - 1 (locale, specifico per ogni distribuzione)
- Impatto: pruning troppo conservativo per nodi con pochi simboli, troppo aggressivo per nodi con molti

**Alternativa BIC (Bayesian Information Criterion):**
```
BIC = -2 * log_likelihood + k * log(n)
dove k = numero parametri, n = osservazioni
```
- Bilancia fit vs complessita' del modello
- Fondamento teorico solido (Schwarz 1978)
- Usato in VLMC da Csiszar & Talata (2006)

**Alternativa Context Tree Weighting (CTW):**
- Willems, Shtarkov, Tjalkens (1995)
- Bayesiano: media pesata su tutti gli ordini possibili
- Ottimale in senso minimax

### 3.2 Stima Probabilita' — Smoothing

**Stato:** MLE puro — P(s|ctx) = 0 se combinazione non osservata.

**Problema:** Likelihood = 0 per qualsiasi traccia con una transizione non vista.

**Alternative:**
- **Laplace (add-1):** P(s|ctx) = (count + 1) / (total + |A|)
  - Pro: semplice, nessun zero
  - Contro: bias verso uniforme, non ottimale per alfabeti grandi
- **Kneser-Ney:** Smoothing con discount + distribuzione di backoff
  - Pro: stato dell'arte in NLP
  - Contro: complesso, richiede tuning del discount
- **Dirichlet prior:** P(s|ctx) = (count + alpha_s) / (total + sum(alpha))
  - Pro: generalizza Laplace, prior informativo possibile
  - Contro: scelta degli iperparametri

### 3.3 Likelihood — Stabilita' Numerica

**Stato:** Prodotto di probabilita' in dominio lineare.

**Problema:** Underflow per tracce lunghe (prodotto di molti numeri < 1).

**Fix:** Calcolare in log-domain:
```
log P(trace) = sum_i log P(s_i | ctx_i)
```

### 3.4 Backoff Strategy

**Stato:** Se il contesto non ha match nel tree, usa la distribuzione del nodo piu' vicino (longest suffix match). Ma se un simbolo non e' nel supporto della distribuzione, P = 0.

**Miglioramento:** Interpolated backoff:
```
P(s|ctx) = lambda * P_tree(s|ctx) + (1-lambda) * P_backoff(s|shorter_ctx)
```

### 3.5 Model Selection

**Stato:** Nessun criterio di selezione automatica di alfa/k.

**Possibilita':**
- Cross-validation sulla likelihood
- BIC/AIC per selezione automatica della profondita'
- Held-out log-likelihood

### 3.6 Riproducibilita'

**Stato:** `MersenneTwister` senza seed esplicito nella simulazione.

**Fix:** Parametro `--seed` per RNG deterministico.

## 4. Applicazioni e Punti di Forza

### 4.1 Dove VLMC Eccelle

- **Process mining:** Modella processi con dipendenze contestuali variabili
- **Sequenze biologiche:** Proteine, DNA — contesti locali significativi
- **Anomaly detection:** Tracce con bassa likelihood = comportamento anomalo
- **Predizione next-event:** Contesto adattivo batte MC ordine fisso
- **Stochastic conformance checking:** uEMSC misura distanza modello-log

### 4.2 Dove VLMC ha Limiti

- **Dati scarsi:** MLE senza smoothing degrada rapidamente
- **Processi altamente paralleli:** VLMC assume sequenzialita'
- **Drift temporale:** Assume stazionarieta' (distribuzione costante nel tempo)
- **Alfabeti enormi:** Suffix array scala bene, ma albero VLMC puo' esplodere

### 4.3 Confronto con Alternative

| Modello | Memory | Parametri | Interpretabilita' | Training |
|---------|--------|-----------|-------------------|----------|
| MC ordine k | Fissa (k) | O(|A|^k) | Alta | O(n) |
| **VLMC** | **Variabile** | **O(|T| * |A|)** | **Alta** | **O(n log n)** |
| HMM | Latente | O(|S|^2) | Bassa | O(n * |S|^2) EM |
| LSTM/RNN | Illimitata | O(10^3-10^6) | Nulla | O(n * epochs) |

## 5. Checklist Review Probabilistico

Quando si modifica codice in `vlmc/`, `fitvlmc/EcfNavigator`, o `fitvlmc/fitVlmc`:

- [ ] Distribuzioni normalizzate (somma = 1.0)?
- [ ] Gestione P=0 esplicita (log(0), divisione per zero)?
- [ ] Gradi di liberta' corretti per test statistici?
- [ ] Stimatore consistente (converge al vero valore con n → inf)?
- [ ] Assunzione di stazionarieta' ragionevole per il caso d'uso?
- [ ] Stabilita' numerica (log-domain per prodotti, overflow/underflow)?
- [ ] Riproducibilita' (seed RNG controllato)?
- [ ] Conteggi sufficienti per affidabilita' delle stime?

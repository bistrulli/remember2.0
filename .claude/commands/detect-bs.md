# LLM Bullshit Detector — jfitVLMC

Analizza le modifiche correnti (o file specifici) per scovare pattern tipici del codice generato da LLM: scaffolding vuoto, fallback silenziosi, over-engineering, codice morto e scope creep non richiesto. **MAI auto-fixare — solo segnalare.**

## Input

$ARGUMENTS

Argomenti opzionali:
- `<file1> <file2> ...` — analizza solo questi file
- `--task "<descrizione>"` — descrizione del task corrente (per analisi semantica CAT-3/CAT-5)

Se nessun argomento: analizza tutti i file `.java` modificati di recente.

---

## Istruzioni

### 1. Raccogli le modifiche

Leggi i file indicati, oppure identifica i file Java modificati di recente nel progetto.

### 2. Fase A — Detection deterministica

Analizza i file sorgente. Per ogni file, applica i pattern seguenti.

#### CAT-1: Scaffolding vuoto (BLOCKER)

Pattern da cercare nei file **Java**:
- Metodo il cui body e' solo `throw new UnsupportedOperationException()`
- Metodo il cui body e' solo `return null;` / `return 0;` / `return false;` senza logica
- Classi il cui body e' vuoto `{}`
- Commenti placeholder: `// TODO`, `// FIXME`, `// HACK`, `// XXX`
- Metodi con `throw new RuntimeException("Not implemented")`

**Eccezioni legittime:**
- `throw new UnsupportedOperationException()` in override di metodi che il design non richiede
- `// TODO` in un task che *e'* il TODO (il task dice "crea placeholder per X")

#### CAT-2: Fallback silenziosi (BLOCKER)

Pattern da cercare nei file **Java**:
- `catch (Exception e) {}` — catch block vuoto
- `catch (Exception e) { /* ignore */ }` — catch con solo commento
- `catch` block che fa solo `return null` / `return new ArrayList<>()` / `return ""` senza logging
- `catch (Exception e) { e.printStackTrace(); }` come unica gestione (in produzione, non in test/debug)

**Eccezioni legittime:**
- `catch (InterruptedException e) { Thread.currentThread().interrupt(); }` — pattern standard
- `catch (SpecificException e) { return defaultValue; }` con commento esplicativo

#### CAT-4: Codice morto e cosmetico (WARNING)

Pattern da cercare:
- Commenti dead-code: `// removed`, `// deprecated`, `// old implementation`, `// no longer needed`
- `@SuppressWarnings` senza motivo documentato
- Import non usati (il compilatore li segnala come warning)
- Javadoc che ripete solo il nome del metodo senza aggiungere informazione
- Variabili assegnate e mai lette
- Commenti ovvi che descrivono cosa fa la riga sotto

#### CAT-6: Test che non testano nulla (BLOCKER)

Pattern da cercare nei file **test**:
- **Assert triviali**: `assertTrue(true)`, `assertEquals(1, 1)`, `assertNotNull(result)` come unico assert
- **Test senza assert**: metodi `@Test` che eseguono codice ma non contengono nessun `assert*`, `@Expected`, o verifica
- **Test che verificano solo il tipo**: `assertInstanceOf(Map.class, result)` senza verificare il contenuto
- **Test che verificano solo che non crashi**: `new MyClass()` senza assert — "funziona se non esplode" non e' un test

#### CAT-7: Workaround e check fragili (BLOCKER)

Pattern da cercare:
- `if (str.contains("error"))` — check su substring di messaggi invece di eccezioni/status strutturati
- `Thread.sleep()` in test come sincronizzazione (quasi sempre maschera una race condition)
- `instanceof` chain lunga al posto di polimorfismo o visitor pattern
- `Object` come tipo parametro quando un tipo specifico funzionerebbe
- `.toString().contains()` come verifica di stato
- Cast difensivi con `if (obj instanceof X) { ((X)obj).method(); }` dove il tipo dovrebbe essere garantito dal design

### 3. Fase B — Analisi semantica (LLM-assisted)

Questa fase richiede giudizio contestuale. Leggi i file completi e (se disponibile) la descrizione del task.

#### CAT-3: Over-engineering (WARNING)

Per ogni modifica, valuta:
- **File nuovi con poca sostanza**: un nuovo file `.java` che contiene meno di 10 righe di logica effettiva (esclusi import, javadoc, getter/setter generati)
- **Helper/utility monouso**: un metodo helper definito e chiamato una sola volta. Se non verra' riusato, poteva essere inline
- **Interfacce con un solo metodo e una sola implementazione**: astrazione prematura (a meno che sia per testabilita')
- **Abstract class con una sola implementazione concreta**: poteva essere una classe concreta
- **Livelli di indirezione inutili**: classe che wrappa un'altra senza aggiungere logica

#### CAT-5: Comportamento diplomatico / Scope creep (WARNING)

Confronta le modifiche con la descrizione del task (se fornita via `--task`):
- **Error handling non richiesto**: try/catch attorno a codice interno che non puo' ragionevolmente fallire
- **Parametri opzionali bonus**: aggiunta di parametri con default a metodi non previsti dal task
- **Javadoc aggiunto a codice non toccato dal task**: se il task modifica il metodo X ma aggiungi javadoc ai metodi Y e Z, e' scope creep
- **Logger aggiunto a classi non coinvolte**: aggiungere logging dove non e' richiesto dal task

### 4. Output strutturato

Per ogni finding, stampa:

#### BLOCKER (CAT-1, CAT-2, CAT-6, CAT-7) — deve essere fixato prima del commit
```
BS-BLOCKER — <file>:<linea> [CAT-N: <nome categoria>]
   Pattern: <cosa ha trovato, con snippet di codice>
   Azione: <cosa deve essere rimosso/riscritto>
```

#### WARNING (CAT-3, CAT-4, CAT-5) — segnalato, non bloccante
```
BS-WARNING — <file>:<linea> [CAT-N: <nome categoria>]
   Pattern: <cosa ha trovato>
   Nota: <perche' e' sospetto e cosa valutare>
```

### 5. Summary finale

```
===================================================
  BS DETECTION REPORT
===================================================

  File analizzati: N
  Righe analizzate: M

  BLOCKER:  N (CAT-1: X, CAT-2: Y, CAT-6: Z, CAT-7: W)
  WARNING:  N (CAT-3: X, CAT-4: Y, CAT-5: Z)

  Verdict: CLEAN | NEEDS_FIX | HAS_WARNINGS
===================================================
```

- **CLEAN**: nessun finding
- **NEEDS_FIX**: almeno un BLOCKER trovato
- **HAS_WARNINGS**: solo warning, nessun blocker

---

## Regole

1. **MAI modificare codice** — solo analizzare e riportare. L'utente decide se fixare.
2. **Sii specifico**: `file:linea`, snippet di codice, non descrizioni vaghe.
3. **No falsi positivi su pattern legittimi**:
   - `throw new UnsupportedOperationException()` in un override che il design non richiede e' OK
   - `catch (InterruptedException e) { Thread.currentThread().interrupt(); }` e' OK
   - `@SuppressWarnings("unchecked")` con commento che spiega il cast e' OK
   - `// TODO` in un task che e' il TODO e' OK
   - `return null` in una funzione che legittimamente puo' non trovare un risultato NON e' scaffolding
4. **Contesto del task**: se la descrizione del task dice esplicitamente di aggiungere error handling, non segnalarlo come CAT-5.
5. **Concentrati sulle AGGIUNTE**, non sul codice pre-esistente.
6. **Un finding per pattern**: se lo stesso pattern si ripete N volte, segnalalo una volta con nota "stesso pattern in N occorrenze".
7. **Non segnalare stile**: naming, formatting, ordering sono compito del formatter, non del BS detector.

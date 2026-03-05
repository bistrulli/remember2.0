# Agente Autonomo — jfitVLMC

Pipeline completo in una sessione per task semplici (1-3 file). Esegue PLAN -> IMPLEMENT -> REVIEW -> TEST inline, con report tra le fasi.

## Input

$ARGUMENTS

## Pre-check

1. Analizza la richiesta e stima il numero di file coinvolti
2. Se **>3 file**: suggerisci `/plan` + `/implement` e fermati
3. Se **<=3 file**: procedi con il pipeline

## Fase 1: PLAN (inline)

- Esplora il codebase per capire dove intervenire
- Identifica i file da modificare/creare
- Definisci i criteri di accettazione
- Stampa un mini-piano:

```
Piano:
  1. <azione> — <file>
  2. <azione> — <file>
  Verifica: <comando>
```

## Fase 2: IMPLEMENT

- Leggi i file PRIMA di modificarli
- Implementa il cambiamento minimale
- Segui le convenzioni del codebase (CamelCase classi, camelCase metodi)
- Dopo ogni file modificato, verifica compilazione: `mvn compile -q`

## Fase 3: REVIEW (self-review)

Verifica le tue modifiche leggendo i file modificati:
- [ ] Correttezza logica
- [ ] Error handling presente dove serve
- [ ] No credenziali esposte
- [ ] Naming coerente con il codebase
- [ ] Import corretti (no wildcard inutili)
- [ ] Compatibilita' Java 17

Se trovi problemi: **fixa subito** prima di procedere.

Report:
```
Review:
  File modificati: N
  Problemi trovati: N (fixati inline)
  Status: CLEAN
```

## Fase 4: TEST

Esegui i test:

```bash
# Compilazione + test
mvn test -q

# Oppure test specifico se indicato
mvn test -Dtest=<TestClass> -q
```

Se mancano test per le funzioni nuove: **scrivili**.

Report:
```
Test:
  Eseguiti: <comandi>
  Risultato: PASS / FAIL
```

## Report finale

```
Auto-complete:
  Task: <descrizione>
  File: <lista file modificati>
  Review: CLEAN
  Test: PASS

  Per verificare: mvn clean test
```

## Regole

- **STOP immediato** se una fase fallisce — non procedere alla successiva
- Se la fase REVIEW trova un MUST FIX, torna a IMPLEMENT
- Se la fase TEST fallisce, diagnostica e fixa, poi ri-testa
- Se il task si rivela piu' complesso del previsto (>3 file), fermati e suggerisci `/plan`

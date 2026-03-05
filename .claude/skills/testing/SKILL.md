# Testing Skill — jfitVLMC

## Comandi rapidi

| Comando | Scope |
|---------|-------|
| `mvn test` | Tutti i test |
| `mvn test -Dtest=SaTest` | Solo SaTest |
| `mvn test -Dtest=SaTestInt` | Solo SaTestInt |
| `mvn compile -q` | Solo compilazione (verifica veloce) |
| `mvn test -Dsurefire.useFile=false` | Test con output in console |

## Test esistenti

| Classe test | Package | Cosa testa |
|------------|---------|------------|
| `SaTest` | `test` | SuffixArray su stringhe (rank, select, LCP) |
| `SaTestInt` | `test` | SuffixArrayInt su array di interi |

**Struttura:** `src/test/java/test/`

## Pattern test consigliati

### Test table-driven (Java style)

```java
@Test
public void testMethod_multipleInputs() {
    Object[][] cases = {
        // {description, input, expected}
        {"happy path", "valid_input", "expected_output"},
        {"empty input", "", null},
        {"edge case", "boundary", "edge_result"},
    };
    for (Object[] c : cases) {
        String desc = (String) c[0];
        String input = (String) c[1];
        String expected = (String) c[2];
        assertEquals(desc, expected, methodUnderTest(input));
    }
}
```

### Test con eccezioni attese

```java
@Test(expected = IllegalArgumentException.class)
public void testMethod_invalidInput_throwsException() {
    methodUnderTest(invalidInput);
}
```

### Test VLMC specifici

```java
// Test serializzazione/deserializzazione modello
@Test
public void testVlmcSerialization_roundTrip() {
    VlmcRoot original = buildTestTree();
    original.saveToFile("test_output.vlmc");
    VlmcRoot loaded = VlmcRoot.loadFromFile("test_output.vlmc");
    // verifica struttura e probabilita'
    assertEquals(original.getAlphabet(), loaded.getAlphabet());
}

// Test predizione con contesto noto
@Test
public void testPrediction_knownContext() {
    VlmcRoot model = buildTrainedModel();
    NextSymbolsDistribution dist = model.predict("state1", "state2");
    assertNotNull(dist);
    assertTrue(dist.getTotalProbability() > 0.99);
}
```

### Test Suffix Array

```java
// Basati sui test esistenti in SaTest.java
@Test
public void testSuffixArray_rank() {
    SuffixArray sa = new SuffixArray("banana");
    // Verifica ranking corretto dei suffissi
    assertTrue(sa.rank("ana") >= 0);
    assertTrue(sa.rank("zzz_not_found") < 0);
}
```

## Convenzioni

- File test: `src/test/java/test/Test<NomeModulo>.java` o `<Nome>Test.java`
- Framework: JUnit 4 (attualmente usato nel progetto)
- Nomi metodi test: `testMetodo_scenario` o `testMetodo_scenario_expectedResult`
- Un test per comportamento, non per metodo
- Assert specifici: `assertEquals` > `assertTrue(a.equals(b))`
- Ogni metodo pubblico nuovo deve avere almeno un test
- Mock I/O esterno (file, HTTP) dove necessario
- Test indipendenti: nessuna dipendenza dall'ordine di esecuzione

## Aree con test mancanti

Le seguenti classi core NON hanno test:
- `fitVlmc` (main, CLI parsing)
- `EcfNavigator` (navigazione ECF, costruzione albero)
- `Trace2EcfIntegrator` (generazione ECF da tracce)
- `VlmcRoot` (serializzazione, likelihood, simulazione)
- `VlmcNode` / `VlmcInternalNode` (struttura nodi)
- `RESTVlmc` (HTTP handler)
- `NextSymbolsDistribution` (distribuzioni probabilita')

Priorita' suggerita: VlmcRoot > EcfNavigator > VlmcNode > NextSymbolsDistribution > altri

# Testing Skill — jfitVLMC

## Comandi rapidi

| Comando | Scope |
|---------|-------|
| `mvn test` | Unit test (Surefire, esclude @Tag("e2e")) |
| `mvn verify` | Tutti i test inclusi E2E (Surefire + Failsafe) |
| `mvn test -Dtest=CsvEventLogReaderTest` | Singola classe test |
| `mvn compile -q` | Solo compilazione (verifica veloce) |
| `mvn test -Dsurefire.useFile=false` | Test con output in console |
| `mvn verify -DskipTests` | Coverage check senza eseguire test |

## Test esistenti

| Classe test | Package | # Test | Cosa testa |
|------------|---------|--------|------------|
| `CsvEventLogReaderTest` | `test` | 10 | CSV parsing, ordering, separators, edge cases |
| `LikelihoodTest` | `test` | 7 | Likelihood computation su VLMC hand-built |
| `UemscTest` | `test` | ~5 | uEMSC stochastic conformance checking |
| `EndToEndTest` | `test` | 1 | Full pipeline E2E: CSV -> fit -> likelihood -> uEMSC (@Tag("e2e")) |
| `PruningTest` | `test` | 11 | Chi-square pruning, KL divergence, edge cases |
| `EcfNavigatorTest` | `test` | ~5 | Cyclic ECF navigation, depth limiting, context tracking |
| `VlmcNavigationTest` | `test` | 7 | VLMC tree navigation, getState, context matching |
| `VlmcSerializationTest` | `test` | ~3 | VLMC save/load round-trip |
| `NextSymbolsDistributionTest` | `test` | ~5 | Probability distributions, sampling |
| `RESTVlmcTest` | `test` | ~3 | REST API handler, malformed requests |
| `SuffixArrayTest` | `test` | ~5 | SuffixArray count, rank, search |
| `SaTest` | `test` | 1 | SuffixArray su stringhe (legacy, hardcoded path) |
| `SaTestInt` | `test` | 1 | SuffixArrayInt su array di interi (legacy) |

**Struttura:** `src/test/java/test/`
**Framework:** JUnit Jupiter 5.10.1 + maven-surefire-plugin 3.2.5

## Test categorization

I test sono separati tramite JUnit 5 `@Tag`:

| Tag | Plugin | Fase Maven | Uso |
|-----|--------|------------|-----|
| (nessun tag) | Surefire | `test` | Unit test veloci |
| `@Tag("e2e")` | Failsafe | `verify` | Integration/E2E (richiede fat JAR) |

```java
import org.junit.jupiter.api.Tag;

@Tag("e2e")
public class EndToEndTest { ... }
```

## Pattern JUnit 5

### @ParameterizedTest con @CsvSource

```java
@ParameterizedTest(name = "{0}")
@CsvSource({
    "happy path, ABC, 3",
    "empty input, '', 0",
    "single char, X, 1"
})
void testLength(String desc, String input, int expected) {
    assertEquals(expected, input.length());
}
```

### @ParameterizedTest con @MethodSource

```java
@ParameterizedTest
@MethodSource("traceProvider")
void testLikelihood(ArrayList<String> trace, double expectedLik) {
    ArrayList<Double> lik = vlmc.getLikelihood(trace);
    assertEquals(expectedLik, lik.get(lik.size() - 1), 1e-6);
}

static Stream<Arguments> traceProvider() {
    return Stream.of(
        Arguments.of(new ArrayList<>(List.of("A", "B", "end$")), 0.4),
        Arguments.of(new ArrayList<>(List.of("A", "C", "end$")), 0.6)
    );
}
```

### @Tag per categorizzazione

```java
@Tag("e2e")   // escluso da mvn test, incluso in mvn verify
@Tag("slow")  // opzionale, per test lenti
```

### @TempDir per file temporanei

```java
@TempDir
File tempDir;

@Test
void testFileOutput() throws IOException {
    File output = new File(tempDir, "model.vlmc");
    // il file viene pulito automaticamente dopo il test
}
```

### assertAll per asserzioni multiple

```java
@Test
void testDistribution() {
    NextSymbolsDistribution dist = createDist();
    assertAll(
        () -> assertEquals(3, dist.getSymbols().size()),
        () -> assertEquals(1.0, dist.getTotalProbability(), 1e-9),
        () -> assertNotNull(dist.getProbBySymbol("A"))
    );
}
```

### assertThrows per eccezioni

```java
@Test
void testInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> {
        new CsvEventLogReader(null, null, null, null);
    });
}
```

### Pattern E2E con ProcessBuilder

```java
@Tag("e2e")
public class EndToEndTest {
    @TempDir File tempDir;

    private String runJar(String... args) throws Exception {
        File jar = new File("target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar");
        Assumptions.assumeTrue(jar.exists(), "Fat JAR not found");
        List<String> cmd = new ArrayList<>(List.of("java", "-jar", jar.getAbsolutePath()));
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        assertEquals(0, proc.waitFor(), "JAR failed:\n" + output);
        return output;
    }
}
```

## Convenzioni

- File test: `src/test/java/test/<Nome>Test.java`
- Framework: JUnit Jupiter 5 (JUnit 5)
- Nomi metodi test: `testMethod_scenario` o `testMethod_scenario_expectedResult`
- Un test per comportamento, non per metodo
- Assert specifici: `assertEquals` > `assertTrue(a.equals(b))`
- Usa `assertAll` per verifiche multiple sullo stesso oggetto
- Usa `assertThrows` per eccezioni (non `@Test(expected=...)`)
- Usa `@TempDir` per file temporanei (non creare in /tmp manualmente)
- Usa `@ParameterizedTest` per test table-driven
- Ogni metodo pubblico nuovo deve avere almeno un test
- Mock I/O esterno (file, HTTP) dove necessario
- Test indipendenti: nessuna dipendenza dall'ordine di esecuzione

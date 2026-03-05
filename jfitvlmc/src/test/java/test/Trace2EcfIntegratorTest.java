package test;

import static org.junit.jupiter.api.Assertions.*;

import ECFEntity.Edge;
import ECFEntity.Flow;
import fitvlmc.Trace2EcfIntegrator;
import org.junit.jupiter.api.Test;

public class Trace2EcfIntegratorTest {

    @Test
    void testCreateEcfFromContent_simpleLinearTrace() {
        Flow flow = Trace2EcfIntegrator.createEcfFromContent("A B C end$");
        // 4 edges: A, B, C, end$
        assertEquals(4, flow.getEdges().size());
        // A -> B connection
        Edge a = flow.getEdges().get("A");
        assertNotNull(a);
        assertTrue(a.getOut().contains(flow.getEdges().get("B")));
        // B -> C connection
        Edge b = flow.getEdges().get("B");
        assertTrue(b.getOut().contains(flow.getEdges().get("C")));
        // C -> end$ connection
        Edge c = flow.getEdges().get("C");
        assertTrue(c.getOut().contains(flow.getEdges().get("end$")));
    }

    @Test
    void testCreateEcfFromContent_multipleTraces() {
        Flow flow = Trace2EcfIntegrator.createEcfFromContent("A B end$ C D end$");
        // 5 edges: A, B, C, D, end$
        assertEquals(5, flow.getEdges().size());
        // A -> B (first trace)
        Edge a = flow.getEdges().get("A");
        assertTrue(a.getOut().contains(flow.getEdges().get("B")));
        // C -> D (second trace, independent)
        Edge cc = flow.getEdges().get("C");
        assertNotNull(cc);
        assertTrue(cc.getOut().contains(flow.getEdges().get("D")));
        // A should NOT connect to C (different traces separated by end$)
        assertFalse(a.getOut().contains(flow.getEdges().get("C")));
    }

    @Test
    void testCreateEcfFromContent_withNumericCost() {
        Flow flow = Trace2EcfIntegrator.createEcfFromContent("state_5 state_3 end$");
        Edge e = flow.getEdges().get("state_5");
        assertNotNull(e);
        assertEquals(5, e.getCost());
        Edge e2 = flow.getEdges().get("state_3");
        assertEquals(3, e2.getCost());
    }

    @Test
    void testCreateEcfFromContent_emptyInput() {
        assertThrows(
                IllegalArgumentException.class, () -> Trace2EcfIntegrator.createEcfFromContent(""));
    }

    @Test
    void testCreateEcfFromContent_nullInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Trace2EcfIntegrator.createEcfFromContent(null));
    }

    @Test
    void testCreateEcfFromContentWithValidation_valid() {
        Flow flow = Trace2EcfIntegrator.createEcfFromContentWithValidation("A B end$");
        assertNotNull(flow);
        assertFalse(flow.getEdges().isEmpty());
        assertTrue(flow.getEdges().containsKey("end$"));
    }

    @Test
    void testCreateEcfFromContent_cyclicTrace() {
        Flow flow = Trace2EcfIntegrator.createEcfFromContent("A B A end$");
        // A, B, end$ = 3 edges
        assertEquals(3, flow.getEdges().size());
        Edge a = flow.getEdges().get("A");
        Edge b = flow.getEdges().get("B");
        // A -> B and B -> A (cycle)
        assertTrue(a.getOut().contains(b));
        assertTrue(b.getOut().contains(a));
    }

    @Test
    void testCreateEcfFromContent_duplicateEdgesNotCreated() {
        // Same edge visited multiple times should not create duplicates
        Flow flow = Trace2EcfIntegrator.createEcfFromContent("A B end$ A B end$");
        assertEquals(3, flow.getEdges().size()); // A, B, end$
    }

    @Test
    void testCreateEcfFromContent_nonNumericSuffix() {
        // Activity names with underscores but non-numeric suffix should not crash
        Flow flow = Trace2EcfIntegrator.createEcfFromContent("check_status review_task end$");
        assertEquals(3, flow.getEdges().size());
        Edge e = flow.getEdges().get("check_status");
        assertNotNull(e);
        assertEquals(0, e.getCost()); // non-numeric suffix, cost stays 0
    }
}

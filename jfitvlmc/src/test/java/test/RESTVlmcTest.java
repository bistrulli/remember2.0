package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import fitvlmc.RESTVlmc;
import vlmc.NextSymbolsDistribution;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class RESTVlmcTest {

	private static HttpServer server;
	private static int port;

	@BeforeAll
	public static void setup() throws IOException {
		VlmcRoot vlmc = new VlmcRoot();
		vlmc.setLabel("root");
		vlmc.setDist(new NextSymbolsDistribution());

		VlmcNode nodeA = new VlmcNode();
		nodeA.setLabel("A");
		NextSymbolsDistribution dist = new NextSymbolsDistribution();
		dist.getSymbols().add("B");
		dist.getSymbols().add("end$");
		dist.getProbability().add(0.7);
		dist.getProbability().add(0.3);
		dist.totalCtx = 10;
		nodeA.setDist(dist);
		nodeA.setParent(vlmc);
		vlmc.getChildren().add(nodeA);

		server = HttpServer.create(new InetSocketAddress(0), 0);
		port = server.getAddress().getPort();
		server.createContext("/", new RESTVlmc(vlmc));
		server.start();
	}

	@AfterAll
	public static void teardown() {
		if (server != null) server.stop(0);
	}

	@Test
	public void testValidRequest() throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/?ctx=A").openConnection();
		assertEquals(200, conn.getResponseCode());
		String body = new String(conn.getInputStream().readAllBytes());
		assertTrue(body.contains("B"), "Response should contain symbol B");
		conn.disconnect();
	}

	@Test
	public void testMissingQuery() throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/").openConnection();
		assertEquals(400, conn.getResponseCode());
		conn.disconnect();
	}

	@Test
	public void testMalformedQuery() throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/?badformat").openConnection();
		assertEquals(400, conn.getResponseCode());
		conn.disconnect();
	}

	@Test
	public void testUnknownContext() throws IOException {
		// Unknown context falls back to root, which has empty dist
		HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/?ctx=UNKNOWN").openConnection();
		int code = conn.getResponseCode();
		// Root has empty dist — should still return 200 with empty distribution
		assertTrue(code == 200 || code == 404, "Should return 200 or 404 for unknown context, got: " + code);
		conn.disconnect();
	}
}

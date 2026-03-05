package fitvlmc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import vlmc.VlmcNode;
import vlmc.VlmcRoot;

public class RESTVlmc implements HttpHandler {
    private VlmcRoot vlmcRoot = null;

    public RESTVlmc(VlmcRoot vlmc) {
        this.vlmcRoot = vlmc;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            sendError(exchange, 400, "Missing query parameter. Use ?ctx=state1-state2-state3");
            return;
        }
        String[] params = query.split("=");
        if (params.length != 2) {
            sendError(exchange, 400, "Invalid query format. Use ?ctx=state1-state2-state3");
            return;
        }
        String[] ctxs = params[1].split("-");
        ArrayList<String> initCtx = new ArrayList<String>();
        for (String s : ctxs) {
            initCtx.add(s);
        }

        VlmcNode node = this.vlmcRoot.getState(initCtx);
        if (node.getDist() == null) {
            sendError(exchange, 404, "No distribution available for context");
            return;
        }
        String response = node.getDist().toString();
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, messageBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(messageBytes);
        os.close();
    }
}

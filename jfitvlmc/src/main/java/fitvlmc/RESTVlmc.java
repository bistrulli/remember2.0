package fitvlmc;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;

import org.apache.commons.lang3.ArrayUtils;

import com.sun.net.httpserver.HttpHandler;

import vlmc.VlmcNode;
import vlmc.VlmcRoot;

import com.sun.net.httpserver.HttpExchange;

class RESTVlmc implements HttpHandler {
	private VlmcRoot vlmcRoot=null;
	public RESTVlmc(VlmcRoot vlmc) {
		this.vlmcRoot=vlmc;
	}
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Send a simple response
        //String response = "Hello, this is a simple HTTP server!";
        
        String query = exchange.getRequestURI().getQuery();
        if(query!=null) {
        	String[] params = query.split("=");
        	if(params.length!=2) {
        		try {
    				throw new Exception("only initial ctx is required");
    			}catch (Exception e) {
    				e.printStackTrace();
    			}
        	}else {
				String[] ctxs = params[1].split("-");
				ArrayList<String> initCtx=new ArrayList<String>();
				for (String s : ctxs) {
					initCtx.add(s);
				}
				
				VlmcNode node = this.vlmcRoot.getState(initCtx);
				String response=node.getDist().toString();
				exchange.sendResponseHeaders(200, response.length());
		        OutputStream os = exchange.getResponseBody();
		        os.write(response.getBytes());
		        os.close();
        	}
        }
    }
}

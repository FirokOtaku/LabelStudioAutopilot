package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import firok.topaz.function.MustCloseable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class ServerWheel implements MustCloseable
{
    private final HttpServer server;
    public ServerWheel(int port) throws IOException
    {
        var localhost = new InetSocketAddress("localhost", port);
        server = HttpServer.create();
        server.createContext("/", this::handleInner);
        server.bind(localhost, 0);
        server.start();
    }

    private static Map<String, String> getQueryParam(URI uri)
    {
        var ret = new HashMap<String, String>();
        var queryRaw = uri.getQuery();
        if(queryRaw != null)
        {
            var queryComponents = queryRaw.split("&");
            for(var queryComponent : queryComponents)
            {
                var kv = queryComponent.split("=");
                if(kv.length == 2)
                {
                    ret.put(kv[0], kv[1]);
                }
                else
                {
                    ret.put(kv[0], "");
                }
            }
        }
        return ret;
    }

    public void handleInner(HttpExchange exchange) throws IOException
    {
        var headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Expose-Headers", "*");
        headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.add("Access-Control-Allow-Private-Network", "true");
        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Max-Age", "120");

        byte[] buffer;
        int code;

        switch (exchange.getRequestMethod())
        {
            case "GET" -> {
                var uri = exchange.getRequestURI();
                var path = uri.getPath();
                var params = getQueryParam(uri);

                try
                {
                    System.out.println(path + " | " + params);
                    var result = handle(path, params);
                    var msg = result == null ? "" : new ObjectMapper().valueToTree(result).toString();
                    buffer = msg.getBytes(StandardCharsets.UTF_8);
                    code = 200;
                }
                catch (Exception any)
                {
                    any.printStackTrace(System.err);
                    buffer = null;
                    code = 500;
                }
            }
            case "OPTIONS" -> {
                buffer = null;
                code = 204;
            }
            default -> {
                buffer = null;
                code = 400;
            }
        }

        exchange.sendResponseHeaders(code, buffer == null ? -1 : buffer.length);

        if(buffer != null)
        {
            try(var os = exchange.getResponseBody();
            )
            {
                os.write(buffer);
                os.flush();
            }
        }

        exchange.close();
    }

    public abstract Object handle(String path, Map<String, String> params) throws Exception;

    @Override
    public void close()
    {
        server.stop(0);
    }
}

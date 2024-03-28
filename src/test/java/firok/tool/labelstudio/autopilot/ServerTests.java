package firok.tool.labelstudio.autopilot;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class ServerTests
{
    @Test
    void testServer()
    {
        try
        {
            var server = new ServerWheel(8080) {
                @Override
                public Object handle(String path, Map<String, String> params) throws Exception
                {
                    return null;
                }
            };
            Thread.sleep(60_000);
            server.close();
        }
        catch (Exception any)
        {
            throw new RuntimeException(any);
        }
    }
}

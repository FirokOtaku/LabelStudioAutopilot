package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import firok.tool.labelstudio.LabelStudioConnector;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;

public class DetectronTests
{
    static final ObjectMapper om;
    static final Config config;
    static final LabelStudioConnector conn;
    static
    {
        try
        {
            om = Autopilot.omJson5();
            config = om.readValue(new File("./config.json5"), Config.class);
            conn = new LabelStudioConnector(new URI(config.url).toURL(), config.token);;
        }
        catch (Exception any)
        {
            throw new RuntimeException(any);
        }
    }
    @Test
    void testGetTaskApi() throws Exception
    {
        var task = conn.Tasks.getTask(config.listTaskId.get(0));
        System.out.println("task: " + task);
    }

    @Test
    void testGetTaskListApi()
    {
        var list = conn.Tasks.getTasksList(config.projectId, null);
        System.out.println(list);
    }

    @Test
    void testGetAnnoApi()
    {
        var list = conn.Annotations.getAllTaskAnnotations(config.listTaskId.get(0));
        System.out.println(Arrays.asList(list));
    }
}

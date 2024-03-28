package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import firok.tool.alloywrench.bean.CocoData;
import firok.topaz.general.Collections;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class CocoWheel
{
    public static Map<Integer, String> getMappingCategory(File fileCoco) throws IOException
    {
        var coco = new ObjectMapper().readValue(fileCoco, CocoData.class);
        return Collections.mappingKeyValue(
                coco.getCategories(),
                CocoData.Category::getId,
                CocoData.Category::getName
        );
    }
}

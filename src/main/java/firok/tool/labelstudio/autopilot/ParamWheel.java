package firok.tool.labelstudio.autopilot;

import java.util.ArrayList;
import java.util.List;

public class ParamWheel
{
    /**
     * 从用户输入读取应该处理的任务 ID
     * */
    public static List<Long> readTaskId(String line)
    {
        var ret = new ArrayList<Long>();
        if(line.isBlank()) return ret;

        var components = line.contains(",") ? line.split(",") : new String[] { line };
        for(var component : components)
        {
            if(component.contains("-"))
            {
                var range = component.split("-");
                var start = Long.parseLong(range[0].trim());
                var end = Long.parseLong(range[1].trim());
                for(var step = start; step <= end; step++)
                {
                    ret.add(step);
                }
            }
            else
            {
                ret.add(Long.parseLong(component.trim()));
            }
        }
        return ret;
    }
}

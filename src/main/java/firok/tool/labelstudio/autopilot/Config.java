package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config
{
    String url;

    String token;

    /**
     * 本次执行的模式
     * */
    Mode mode;

    /**
     * 用来填充 completeBy 字段
     * */
    Long uid;

    /**
     * 要处理的项目 id
     * */
    Long projectId;

    /**
     * 要处理的任务 id
     * */
    String listTaskId;
    public List<Long> getListTaskId()
    {
        return ParamWheel.readTaskId(listTaskId);
    }

    /**
     * 缓存目录
     * */
    String folderCache;

    String pathDetectron;

    String pathDetectronConfig;

    String pathFileModel;

    String pathFileDataset;

    /**
     * 启动 detectron2 的端口
     * */
    int portDetectron;

    /**
     * 结果简化距离
     * */
    int simplifyDistance;

    /**
     * 用于启动推理服务器的端口
     * */
    int portServer;
}

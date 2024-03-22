package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Detectron2Response
{
    Double startTime;
    Double endTime;
    Double costTime;
    List<Double> predScores;
    List<Integer> predClasses;
    List<List<int[]>> predShapes;
}

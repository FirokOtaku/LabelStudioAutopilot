package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import firok.tool.labelstudio.LabelStudioConnector;
import firok.tool.labelstudio.bean.AnnotationActionEnum;
import firok.tool.labelstudio.bean.AnnotationBean;
import firok.topaz.resource.Files;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static firok.topaz.general.Collections.sizeOf;

public class StudioWheel
{
    private final LabelStudioConnector conn;
    private final File folderCache;
    private static final BigDecimal D100 = new BigDecimal(100);

    public StudioWheel(LabelStudioConnector conn, File folderCache)
    {
        this.conn = conn;
        this.folderCache = folderCache;
    }
    public void interact(
            Long taskId, Long projectId, Long uid,
            Map<Integer, String> mappingCategory,
            InferenceWheel wheelInference,
            SimplifyWheel wheelSimplify
    )
    {
        try
        {
            var omTask = new ObjectMapper();
            // 获取任务信息
            var task = conn.Tasks.getTask(taskId);
            System.out.println(STR."任务 \{task.getId()}");

            // 获取图片信息
            var imageUrlUrl = task.getData().get("image").textValue();
            var imageUrlReal = URLDecoder.decode(imageUrlUrl, StandardCharsets.UTF_8);
            var imageFilename = (new File(imageUrlReal)).getName();
            var fileImage = new File(folderCache, imageUrlUrl);
            Files.mkParent(fileImage);

            // 下载图片
            if(!fileImage.exists()) // 检查缓存里有没有, 有的话不用下了
            {
                try(var ims = conn.Direct.requestStream(imageUrlReal);
                    var ofs = new FileOutputStream(fileImage))
                {
                    ims.transferTo(ofs);
                }
                catch (Exception any)
                {
                    System.err.println("下载图片发生错误: " + imageFilename);
                    any.printStackTrace(System.err);
                    return;
                }
            }

            // 读取图片信息
            var image = ImageIO.read(fileImage);
            final var originalWidth = image.getWidth();
            final var originalHeight = image.getHeight();
            final var imageWidth = new BigDecimal(originalWidth);
            final var imageHeight = new BigDecimal(originalHeight);

            // 把图片交给 detectron2 推理
            File fileResult;
            try
            {
                fileResult = wheelInference.inference(fileImage);
            }
            catch (Exception any)
            {
                System.err.println("推理发生错误: " + imageFilename);
                any.printStackTrace(System.err);
                return;
            }

            // 读取推理结束后的数据
            ArrayNode result;
            try
            {
                /*
                response 里面的内容大概是这个画风: 包含一个不闭合的 multi-polygon 列表
                {
                  "startTime": 1, "endTime": 2, "costTime": 1, "predScores": [0.99, 0.99],
                  "predClasses": [1, 2],
                  "predShapes": [
                    [ [ 331, 202, 330, 203, 325, 203 ] ],
                    [ [ 342, 189, 341, 190, 331, 190 ] ]
                  ]
                }
                * */
                var response = omTask.readValue(fileResult, Detectron2Response.class);
                final var countShape = sizeOf(response.getPredScores());

                /*
                Label Studio 需要的数据大概是这个画风
                巧了, 也不闭合, 处理起来倒是简单
                [
                    {
                        "original_width": 440, "original_height": 237, "image_rotation": 0,
                        "value": {
                            "points": [
                                [ 25.643507972665148, 87.01594533029613 ],
                                [ 48.5876993166287, 67.88154897494306 ],
                                [ 53.372851522054255, 91.79954441913439 ]
                            ],
                            "closed": true, "polygonlabels": [ "干净" ]
                        },
                        "id": "LrdPBd3RiH",
                        "from_name": "label",
                        "to_name": "image",
                        "type": "polygonlabels",
                        "origin": "manual"
                    }
                ]
                * */

                result = omTask.createArrayNode();

                for(var stepShape = 0; stepShape < countShape; stepShape++)
                {
                    var uniqueId = UUID.randomUUID().toString().substring(24);
                    var anno = omTask.createObjectNode();
                    anno.put("original_width", originalWidth);
                    anno.put("original_height", originalHeight);
                    anno.put("image_rotation", 0);
                    anno.put("id", uniqueId);
                    anno.put("from_name", "label");
                    anno.put("to_name", "image");
                    anno.put("type", "polygonlabels");
                    anno.put("origin", "manual");

                    var value = omTask.createObjectNode();
                    value.put("closed", true);
                    var categoryIndex = response.predClasses.get(stepShape);
                    var categoryName = mappingCategory.get(categoryIndex);
                    var valueLabels = omTask.createArrayNode().add(categoryName);
                    value.set("polygonlabels", valueLabels);
                    var shapeRaw = response.predShapes.get(stepShape).get(0); // 只获取外圈的数据
                    var shape = wheelSimplify.simplify(shapeRaw); // 简化多边形
                    var countPoint = sizeOf(shape) / 2;
                    var valuePoints = omTask.createArrayNode();
                    for(var stepPoint = 0; stepPoint < countPoint; stepPoint++)
                    {
                        var originalPointX = shape[stepPoint * 2];
                        var originalPointY = shape[stepPoint * 2 + 1];
                        var pointX = new BigDecimal(originalPointX).multiply(D100).divide(imageWidth, 13, RoundingMode.HALF_UP);
                        var pointY = new BigDecimal(originalPointY).multiply(D100).divide(imageHeight, 13, RoundingMode.HALF_UP);
                        var valuePoint = omTask.createArrayNode();
                        valuePoint.add(pointX).add(pointY);
                        valuePoints.add(valuePoint);
                    }
                    value.set("points", valuePoints);
                    anno.set("value", value);

                    result.add(anno);
                }
            }
            catch (Exception any)
            {
                System.err.println("读取并转换推理结果发生错误: " + imageFilename);
                any.printStackTrace(System.err);
                return;
            }

            // 将推理结果转换为 Label Studio 的标注格式
            var bean = new AnnotationBean();
            bean.setProject(projectId);
            bean.setTask(taskId);
            bean.setCompletedBy(uid);
            bean.setGroundTruth(false);
            bean.setWasCancelled(false);
            bean.setUniqueId(UUID.randomUUID().toString());
            bean.setResult(result);
            bean.setLastAction(AnnotationActionEnum.submitted);
//            System.out.println("任务 " + taskId + " 创建的标注为: " + bean);

            // 调用 Label Studio 接口, 删除已有标注, 创建新标注
            var listAnno = conn.Annotations.getAllTaskAnnotations(taskId);
            for(var anno : listAnno)
            {
                conn.Annotations.deleteAnnotation(anno.getId());
            }
            conn.Annotations.createAnnotation(taskId, bean);

            // todo 暂时先不删除缓存的图片和数据文件

            System.out.println("任务 " + taskId + " 完成处理");
        }
        catch (Exception any)
        {
            System.err.println("任务 " + taskId + " 遇到未知错误");
            any.printStackTrace(System.err);
        }
    }
}

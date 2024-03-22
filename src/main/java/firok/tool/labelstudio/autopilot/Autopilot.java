package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import firok.tool.alloywrench.bean.CocoData;
import firok.tool.labelstudio.LabelStudioConnector;
import firok.tool.labelstudio.bean.AnnotationActionEnum;
import firok.tool.labelstudio.bean.AnnotationBean;
import firok.topaz.general.Collections;
import firok.topaz.resource.Files;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static firok.topaz.general.Collections.sizeOf;

public class Autopilot
{
    static ObjectMapper omJson5()
    {
        return JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
                .build();
    }
    public static void main(String[] args) throws Exception
    {
        System.out.println("Label Studio Autopilot");

        var fileConfig = new File("./config.json5").getCanonicalFile();

        var om = omJson5();
        var config = om.readValue(fileConfig, Config.class);

        final var conn = new LabelStudioConnector(new URI(config.url).toURL(), config.token);

        switch (config.mode)
        {
            case ListProject -> {
                var page = conn.Projects.listProjects(null, null, 1, Integer.MAX_VALUE);
                var projects = page.getResults();
                System.out.println(STR."共 \{page.getCount()} 个项目");
                for(var project : projects)
                {
                    System.out.println(STR."* \{project.getId()} - \{project.getTitle()}");
                }
            }
            case ListTask -> {
                var projectId = config.projectId;
                var page = conn.Tasks.getTasksList(projectId, null);
                System.out.println(STR."共 \{page.getTotal()} 个任务, 当前有 \{page.getTotalAnnotations()} 个标注");
                for(var task : page.getTasks())
                {
                    System.out.println(STR."* \{task.getId()} - \{task.getData().get("image")}");
                }
            }
            case Annotate -> {
                var projectId = config.projectId;
                var listTaskId = config.listTaskId;
                var folderCache = new File(config.folderCache).getCanonicalFile();

                // 读取数据集信息, 创建类型映射
                var folderDataset = new File(config.pathFolderDataset).getCanonicalFile();
                var fileCoco = new File(folderDataset, "coco.json");
                var coco = om.readValue(fileCoco, CocoData.class);
                var mappingCategory = Collections.mappingKeyValue(
                        coco.getCategories(),
                        CocoData.Category::getId,
                        CocoData.Category::getName
                );

                // 启动一个 detectron2 实例
                try(var pool = Executors.newFixedThreadPool(6);
                    var wheel = new InferenceWheel(
                            config.pathDetectron,
                            config.pathDetectronConfig,
                            config.pathFileModel,
                            config.portDetectron
                    ))
                {
                    for(var taskId : listTaskId)
                    {
                        pool.submit(() ->{
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

                                // 把图片交给 detectron2 推理
                                File fileResult;
                                try
                                {
                                    fileResult = wheel.inference(fileImage);
                                }
                                catch (Exception any)
                                {
                                    System.err.println("推理发生错误: " + imageFilename);
                                    any.printStackTrace(System.err);
                                    return;
                                }

                                // 读取推理结束后的数据
                                JsonNode jsonResult;
                                try
                                {
                                    var response = omTask.readValue(fileResult, Detectron2Response.class);

                                    jsonResult = omTask.createObjectNode();
                                }
                                catch (Exception any)
                                {
                                    System.err.println("读取并转换推理结果发生错误: " + imageFilename);
                                    any.printStackTrace(System.err);
                                    return;
                                }

                                // 将推理结果转换为 Label Studio 的标注格式
                                var anno = new AnnotationBean();
                                anno.setProject(projectId);
                                anno.setTask(taskId);
                                anno.setCompletedBy(config.uid);
                                anno.setGroundTruth(false);
                                anno.setWasCancelled(false);
                                anno.setUniqueId(UUID.randomUUID().toString().substring(24));
                                anno.setResult(jsonResult);
                                anno.setLastAction(AnnotationActionEnum.submitted);

                                // 调用 Label Studio 接口, 上传数据
//                            conn.Annotations.createAnnotation(taskId, anno);

                                // todo 暂时先不删除缓存的图片和数据文件
                            }
                            catch (Exception any)
                            {
                                System.err.println("遇到未知错误: " + taskId);
                                any.printStackTrace(System.err);
                            }
                        });
                    }

                    pool.shutdown();
                    pool.awaitTermination(sizeOf(listTaskId) * 10L, TimeUnit.MINUTES);
                    System.out.println("完成所有图片推理");
                }
            }
        }
    }
}

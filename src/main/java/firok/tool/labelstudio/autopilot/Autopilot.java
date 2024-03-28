package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import firok.tool.labelstudio.LabelStudioConnector;
import firok.topaz.general.ProgramMeta;
import firok.topaz.general.Version;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;

import static firok.topaz.general.Collections.sizeOf;

public class Autopilot
{
    public static final ProgramMeta META = new ProgramMeta(
            "firok.tool.labelstudioautopilot",
            "Label Studio Autopilot",
            new Version(0, 4, 0),
            "",
            List.of("Firok"),
            List.of("https://github.com/FirokOtaku/LabelStudioAutopilot"),
            List.of("https://github.com/FirokOtaku/LabelStudioAutopilot"),
            "Mulan PSL v2"
    );

    private static final BigDecimal D100 = new BigDecimal(100);

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
        var projectId = config.getProjectId();
        var listTaskId = config.getListTaskId();
        var folderCache = new File(config.folderCache).getCanonicalFile();

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
                var page = conn.Tasks.getTasksList(projectId, null);
                System.out.println(STR."共 \{page.getTotal()} 个任务, 当前有 \{page.getTotalAnnotations()} 个标注");
                for(var task : page.getTasks())
                {
                    System.out.println(STR."* \{task.getId()} - \{task.getData().get("image")}");
                }
            }
            case Annotate -> {
                // 读取数据集信息, 创建类型映射
                var fileCoco = new File(config.pathFileDataset).getCanonicalFile();
                var mappingCategory = CocoWheel.getMappingCategory(fileCoco);

                var wheelStudio = new StudioWheel(conn, folderCache);

                // 启动一个 detectron2 实例
                try(var pool = Executors.newFixedThreadPool(6);
                    var wheelInference = new InferenceWheel(
                            config.pathDetectron,
                            config.pathDetectronConfig,
                            config.pathFileModel,
                            config.portDetectron
                    ))
                {
                    var wheelSimplify = new SimplifyWheel(config);

                    for(var taskId : listTaskId)
                    {
                        pool.submit(() -> wheelStudio.interact(
                                taskId, projectId, config.uid,
                                mappingCategory,
                                wheelInference,
                                wheelSimplify
                        ));
                    }

                    pool.shutdown();
                    pool.awaitTermination(sizeOf(listTaskId) * 2L, TimeUnit.MINUTES);
                    System.out.println("完成所有图片推理");
                }
            }
            case AnnotateServer -> {
                // 读取数据集信息, 创建类型映射
                var fileCoco = new File(config.pathFileDataset).getCanonicalFile();
                var mappingCategory = CocoWheel.getMappingCategory(fileCoco);

                var wheelStudio = new StudioWheel(conn, folderCache);
                var wheelInference = new InferenceWheel(
                        config.pathDetectron,
                        config.pathDetectronConfig,
                        config.pathFileModel,
                        config.portDetectron
                );
                var wheelSimplify = new SimplifyWheel(config);

                try(var pool = Executors.newFixedThreadPool(6);
                    var in = new Scanner(System.in);
                    var server = new ServerWheel(config.portServer >= 1 ? config.portServer : 39270) {
                    @Override
                    public Object handle(String path, Map<String, String> params)
                    {
                        var taskId = Long.parseLong(params.get("taskId"));
                        pool.submit(() -> wheelStudio.interact(
                                taskId, projectId, config.uid,
                                mappingCategory,
                                wheelInference,
                                wheelSimplify
                        ));
                        return "task-submitted";
                    }
                })
                {
                    System.out.println("推理服务器启动成功于端口 " + config.portServer);
                    while(in.hasNextLine())
                    {
                        var line = in.nextLine().trim();
                        switch (line)
                        {
                            case "stop", "exit" -> {
                                System.out.println("服务器停止中...");
                                pool.shutdown();
                                pool.awaitTermination(1, TimeUnit.MINUTES);
                                server.close();
                                throw new WrongThreadException();
                            }
                        }
                    }
                }
                catch (WrongThreadException _)
                {
                    System.out.println("服务器关闭");
                }
                catch (Exception any)
                {
                    any.printStackTrace(System.err);
                }
            }
        }
    }
}

package firok.tool.labelstudio.autopilot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import firok.topaz.function.MustCloseable;
import firok.topaz.platform.NativeProcess;
import firok.topaz.resource.Files;
import firok.topaz.thread.ReentrantLockCompound;
import firok.topaz.thread.Threads;
import lombok.Data;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 执行推理和结果数据优化工作
 * */
public class InferenceWheel implements MustCloseable
{
    final String pathDetectron, pathFileModel;
    final NativeProcess processDetectron;
    final int portDetectron;
    private boolean isClosed = false;
    private OkHttpClient client;
    /**
     * @param pathDetectron detectron2 根目录
     * @param pathDetectronConfig detectron2 配置文件
     * @param pathFileModel 模型文件路径
     * */
    public InferenceWheel(
            String pathDetectron,
            String pathDetectronConfig,
            String pathFileModel,
            int portDetectron
    ) throws Exception
    {
        this.pathDetectron = pathDetectronConfig;
        this.pathFileModel = pathFileModel;
        this.portDetectron = portDetectron;

        var fileBatch = new File("./detectron2.bat");
        Files.writeTo(fileBatch, STR."""
                rem 启动 Detectron2 服务
                chcp 65001
                cd \{pathDetectron}
                set KMP_DUPLICATE_LIB_OK=TRUE
                call conda activate detectron2
                python demo/flask_inference.py --config-file "\{pathDetectronConfig}" --flask-port \{portDetectron} --opts "MODEL.WEIGHTS" "\{pathFileModel}"
                """);
        processDetectron = new NativeProcess("./detectron2.bat", System.out::println, System.err::println);
        var timeout = Duration.of(60, ChronoUnit.SECONDS);
        this.client = new OkHttpClient.Builder()
                .writeTimeout(timeout)
                .connectTimeout(timeout)
                .callTimeout(timeout)
                .readTimeout(timeout)
                .build();
        Threads.sleep(15000); // 等待 Detectron2 Web 服务启动完成

        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    /**
     * 对指定图片进行推理
     * @return 推理得出的结果数据文件, JSON 格式
     * */
    public synchronized File inference(File fileImage) throws Exception
    {
        var parent = fileImage.getParentFile();
        var fileResult = new File(parent, fileImage.getName() + ".json");

        // 调用 detectron2 进行推理
        var url = "http://localhost:" + portDetectron + "/inference";
        var form = new FormBody.Builder()
                .add("path", fileImage.getCanonicalPath()) // todo
                .build();
        var request = new Request.Builder()
                .post(form)
                .url(url)
                .build();
        try(var res = client.newCall(request).execute();
            var ibs = res.body().byteStream();
            var obs = new FileOutputStream(fileResult)
        )
        {
            ibs.transferTo(obs);
        }

        return fileResult;
    }

    @Override
    public synchronized void close()
    {
        // 销毁 detectron 实例
        if(this.isClosed) return;

        processDetectron.killTreeForcibly();

        this.isClosed = true;
    }
}

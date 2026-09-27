package com.shumamall.video.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg / ffprobe 能力自检器。
 * <p>
 * 启动时探测两个外部依赖是否真的可用，把结果打进日志。
 * <p>
 * 起因是一次真实故障：PATH 上的 ffmpeg 被换成了 {@code --disable-everything}
 * 精简构建（只保留了 mp4 muxer），于是所有上传都在异步线程里切片失败，
 * 用户侧表现为进度条卡在「服务端 ffprobe 校验 + HLS 切片中…」十几秒后报
 * "HLS 切片失败"，日志里只有一段 ffmpeg 版本横幅——横幅既不体现二进制路径，
 * 也看不出缺的是哪个能力，从现象倒推构建能力的成本很高。
 * <p>
 * 故改为启动即探测。探测不通过只打 ERROR 不打挂启动：上传/播放等不依赖
 * FFmpeg 的接口（列表、签名播放、进度）应当继续可用，硬失败会把这些一起拖垮。
 */
@Slf4j
@Component
public class FfmpegCapabilityCheckRunner implements ApplicationRunner {

    @Value("${video.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /** 探测超时秒数：只跑 -version / -h 这类瞬时返回的命令，5 秒足够 */
    private static final long PROBE_TIMEOUT_SECONDS = 5;

    @Override
    public void run(ApplicationArguments args) {
        checkFfprobe();
        checkFfmpegHls();
    }

    /**
     * 自检 ffprobe 是否可执行（格式校验依赖它，缺失则所有上传都会被判为「无法读取视频信息」）。
     */
    private void checkFfprobe() {
        Path exe = resolveExecutable(ffprobePath);
        if (exe == null) {
            log.error("ffprobe 自检失败：video.ffprobe-path={} 未在 PATH 上找到可执行文件，"
                    + "所有视频格式校验都会失败", ffprobePath);
            return;
        }
        String output = runAndCapture(List.of(exe.toString(), "-hide_banner", "-version"));
        if (output == null) {
            log.error("ffprobe 自检失败：{} 无法执行，所有视频格式校验都会失败", exe);
            return;
        }
        log.info("ffprobe 自检通过: path={}, {}", exe, firstLine(output));
    }

    /**
     * 自检 ffmpeg 是否支持 hls muxer（HLS 切片依赖它，缺失则所有上传最终落库为 failed）。
     */
    private void checkFfmpegHls() {
        Path exe = resolveExecutable(ffmpegPath);
        if (exe == null) {
            log.error("ffmpeg 自检失败：video.ffmpeg-path={} 未在 PATH 上找到可执行文件，"
                    + "所有上传都会在切片阶段失败", ffmpegPath);
            return;
        }
        String output = runAndCapture(List.of(exe.toString(), "-hide_banner", "-h", "muxer=hls"));
        // 只看输出内容不看退出码：不支持 hls 的构建会打印 "Unknown format 'hls'." 后以非 0 退出，
        // 而不同构建对未知 muxer 的退出码并不一致，按内容判断更可靠
        if (output != null && output.contains("Muxer hls")) {
            log.info("ffmpeg 自检通过: path={}, {}，HLS 切片可用", exe, firstLine(output));
            return;
        }
        log.error("ffmpeg 自检失败：{} 不支持 hls muxer，上传会在切片阶段全部失败并落库为 failed。"
                        + "请把 video.ffmpeg-path 指向含 HLS 的完整构建"
                        + "（该构建需带 --enable-muxer=hls、libx264 与 aac 编码器），"
                        + "校验方法：执行 `ffmpeg -h muxer=hls` 应输出 `Muxer hls [Apple HTTP Live Streaming]`。"
                        + "当前构建的实际输出: {}",
                exe, firstLine(output));
    }

    /**
     * 把配置的 ffmpeg / ffprobe 解析成绝对路径。
     * <p>
     * 配置通常只写命令名（如 {@code ffmpeg}），真正生效的二进制由 PATH 决定，
     * 出问题的恰恰是 PATH 上那一个。故这里按 PATH 手工查找，
     * 让日志直接打出「到底用了哪个文件」，而不是只回显一个命令名。
     *
     * @param configured 配置值，可为命令名或绝对/相对路径
     * @return 命中文件的绝对路径；未找到返回 null
     */
    private Path resolveExecutable(String configured) {
        if (configured.contains("/") || configured.contains("\\")) {
            Path direct = Paths.get(configured);
            return Files.isRegularFile(direct) ? direct.toAbsolutePath() : null;
        }
        List<String> names = isWindows()
                ? List.of(configured, configured + ".exe", configured + ".cmd", configured + ".bat")
                : List.of(configured);
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String name : names) {
                Path candidate = Paths.get(dir, name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * 执行探测命令并返回全部输出。
     * <p>
     * 与 {@code VideoProcessServiceImpl#exec} 同样把输出重定向到临时文件而非管道：
     * ffmpeg 的 {@code -h} 帮助文本可能超过管道缓冲区（Windows 约 4KB），
     * 本方法在进程退出前不读取管道，用管道会双方互等造成死锁。
     *
     * @param cmd 命令与参数
     * @return 命令输出；执行失败或超时返回 null
     */
    private String runAndCapture(List<String> cmd) {
        Path outFile = null;
        try {
            outFile = Files.createTempFile("ffcheck-", ".log");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outFile.toFile());
            Process process = pb.start();
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.error("自检命令超时（{}s）: cmd={}", PROBE_TIMEOUT_SECONDS, cmd);
                return null;
            }
            return Files.readString(outFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("自检命令执行失败: cmd={}, error={}", cmd, e.getMessage());
            return null;
        } finally {
            if (outFile != null) {
                try {
                    Files.deleteIfExists(outFile);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响启动
                }
            }
        }
    }

    /**
     * 判断是否为 Windows 平台（决定是否追加 .exe/.cmd/.bat 后缀）。
     *
     * @return Windows 返回 true
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * 取首个非空行（版本横幅），避免把整段 help 文本打进日志。
     *
     * @param text 命令输出
     * @return 首个非空行；无内容返回空串
     */
    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        return text.lines().filter(line -> !line.isBlank()).findFirst().orElse("").trim();
    }
}

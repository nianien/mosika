package com.skyfalling.mousika.ui.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * mousika-web 启动类。
 * <p>
 * 提供规则定义与规则流（RuleFlow）的持久化与 REST 能力，
 * 存储采用嵌入式 SQLite；核心执行引擎沿用 {@code mousika-core} 的
 * {@link com.skyfalling.mousika.suite.RuleSuite}。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@SpringBootApplication
public class MousikaWebApplication {

    private static final Logger log = LoggerFactory.getLogger(MousikaWebApplication.class);

    /** 默认 SQLite 文件路径；与 application.yml 中的 {@code mousika.db.path} 保持一致。 */
    static final String DEFAULT_DB_PATH = "./data/mousika.db";

    public static void main(String[] args) {
        ensureDbDirectory();
        ConfigurableApplicationContext context = SpringApplication.run(MousikaWebApplication.class, args);
        logAccessUrls(context.getEnvironment());
    }

    /**
     * 启动完成后，打印可访问的前端入口与 API 基址，方便直接点开。
     */
    private static void logAccessUrls(Environment env) {
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        if (contextPath.equals("/")) {
            contextPath = "";
        }
        String address = env.getProperty("server.address", "127.0.0.1");
        String displayHost = "0.0.0.0".equals(address) || "::".equals(address) ? "localhost" : address;
        String base = "http://" + displayHost + ":" + port + contextPath;
        log.info("\n----------------------------------------------------------\n" +
                        "  Mousika 规则编排已启动，可访问：\n" +
                        "  规则流控制台   : {}/\n" +
                        "  原子规则库     : {}/rules\n" +
                        "  规则画布       : {}/flow/{{id}}\n" +
                        "  API 基址       : {}/api\n" +
                        "  监听地址       : {}\n" +
                        "----------------------------------------------------------",
                base, base, base, base, address);
    }

    /**
     * xerial 的 SQLite JDBC 驱动不会自动创建父目录，这里在 Spring 启动前
     * 先按 env → system property → 默认值的顺序解析目标路径，然后 mkdir -p 父目录。
     */
    private static void ensureDbDirectory() {
        String path = System.getenv("MOUSIKA_DB_PATH");
        if (path == null || path.isBlank()) {
            path = System.getProperty("mousika.db.path", DEFAULT_DB_PATH);
        }
        try {
            Path parent = Paths.get(path).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            System.err.println("[mousika] failed to create db parent directory for " + path + ": " + e.getMessage());
        }
    }
}

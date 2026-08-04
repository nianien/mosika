package com.skyfalling.mousika.ui.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <ul>
 *   <li>CORS：本工具默认同源访问，仅放开本机回环开发源（localhost/127.0.0.1），
 *       不携带凭证。若要对外提供服务，请在此显式配置受信 Origin 并补充鉴权。</li>
 *   <li>静态资源：规则编排前端位于 Spring Boot 标准目录
 *       {@code classpath:/static/ui/}，直接以 {@code /ui/**} 对外提供。</li>
 *   <li>入口：根路径 {@code /} 转发到规则流列表控制台。</li>
 * </ul>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 本地工具：仅允许本机回环开发源跨端口调试，不带凭证。
        // 对外部署时应改为明确 Origin 白名单并叠加鉴权，切勿放开任意 Origin。
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 正式干净路由（forward 到静态页，浏览器地址栏保留干净路径；页面静态资源用 /ui/ 绝对引用）：
        //   /            规则流列表
        //   /rules       原子规则库
        //   /flow/{id}   规则画布（id 在路径中，由前端 app.js 解析）
        //   /flow        缺 id 时回列表
        registry.addViewController("/").setViewName("forward:/ui/console.html");
        registry.addViewController("/rules").setViewName("forward:/ui/rules.html");
        registry.addViewController("/flow/{id:\\d+}").setViewName("forward:/ui/index.html");
        registry.addRedirectViewController("/flow", "/");
    }
}

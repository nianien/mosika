package com.skyfalling.mousika.ui.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <ul>
 *   <li>CORS：对 {@code /api/**} 放开，便于本地前端调试。</li>
 *   <li>静态资源：规则编排前端位于 {@code classpath:/demo/ui-tree/}，对外统一挂在
 *       {@code /ui/**}（正式访问路径，URL 不含 demo）；{@code /demo/**} 仅保留给
 *       {@code bench} 基准工具向后兼容。</li>
 *   <li>入口：根路径 {@code /} 重定向到业务场景控制台 {@code /ui/console.html}。</li>
 * </ul>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 正式访问路径：/ui/console.html、/ui/rules.html、/ui/index.html?flowId=N
        registry.addResourceHandler("/ui/**")
                .addResourceLocations("classpath:/demo/ui-tree/");
        // 向后兼容：bench 基准工具与历史链接仍可用 /demo/ 访问
        registry.addResourceHandler("/demo/**")
                .addResourceLocations("classpath:/demo/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 正式干净路由（forward 到静态页，浏览器地址栏保留干净路径；页面静态资源用 /ui/ 绝对引用）：
        //   /            业务场景列表
        //   /rules       原子规则库
        //   /flow/{id}   规则画布（id 在路径中，由前端 app.js 解析）
        //   /flow        缺 id 时回列表
        registry.addViewController("/").setViewName("forward:/ui/console.html");
        registry.addViewController("/rules").setViewName("forward:/ui/rules.html");
        registry.addViewController("/flow/{id:\\d+}").setViewName("forward:/ui/index.html");
        registry.addRedirectViewController("/flow", "/");
    }
}


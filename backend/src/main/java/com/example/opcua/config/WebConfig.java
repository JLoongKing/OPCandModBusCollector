package com.example.opcua.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web配置类
 * 配置UTF-8编码支持
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 注册UTF-8编码过滤器
     */
    @Bean
    public FilterRegistrationBean<UTF8EncodingFilter> encodingFilter() {
        FilterRegistrationBean<UTF8EncodingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new UTF8EncodingFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    /**
     * 配置字符串消息转换器为UTF-8
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        converters.add(0, stringConverter);
    }

    /**
     * 配置字符串消息转换器Bean
     */
    @Bean
    public StringHttpMessageConverter stringHttpMessageConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }
}
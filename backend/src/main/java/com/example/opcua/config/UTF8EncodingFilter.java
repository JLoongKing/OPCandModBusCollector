package com.example.opcua.config;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * UTF-8编码过滤器
 * 确保所有请求和响应都使用UTF-8编码
 */
@Component
@WebFilter(filterName = "encodingFilter", urlPatterns = "/*")
public class UTF8EncodingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化操作
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 设置请求编码
        httpRequest.setCharacterEncoding("UTF-8");

        // 设置响应编码
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setContentType("application/json; charset=UTF-8");
        httpResponse.setHeader("Content-Type", "application/json; charset=UTF-8");

        // 继续执行过滤器链
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 销毁操作
    }
}
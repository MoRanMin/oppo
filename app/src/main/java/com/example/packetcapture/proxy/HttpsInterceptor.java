package com.example.packetcapture.proxy;

import android.util.Log;

import com.example.packetcapture.config.RewriteConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class HttpsInterceptor {
    private static final String TAG = "HttpsInterceptor";
    
    private RewriteConfig rewriteConfig;
    
    public HttpsInterceptor(RewriteConfig rewriteConfig) {
        this.rewriteConfig = rewriteConfig;
    }
    
    /**
     * 拦截并处理HTTP响应
     * @param url 请求URL
     * @param responseBody 原始响应体
     * @return 处理后的响应体
     */
    public byte[] interceptResponse(String url, byte[] responseBody) {
        if (rewriteConfig == null || rewriteConfig.getRules().isEmpty()) {
            return responseBody;
        }
        
        // 检查是否有匹配的重写规则
        for (RewriteConfig.RewriteRule rule : rewriteConfig.getRules()) {
            if (!rule.isEnabled()) {
                continue;
            }
            
            // 使用正则表达式匹配URL
            if (Pattern.matches(rule.getUrl(), url)) {
                Log.d(TAG, "找到匹配的重写规则: " + rule.getName() + " 对于URL: " + url);
                
                // 处理响应体替换
                for (RewriteConfig.RewriteItem item : rule.getItems()) {
                    if (item.isEnabled() && "replaceResponseBody".equals(item.getType())) {
                        String newBody = item.getValues().get("body");
                        if (newBody != null) {
                            Log.d(TAG, "替换响应体");
                            return newBody.getBytes(StandardCharsets.UTF_8);
                        }
                    }
                }
            }
        }
        
        return responseBody;
    }
    
    /**
     * 拦截并处理HTTP请求
     * @param url 请求URL
     * @param requestBody 原始请求体
     * @return 处理后的请求体
     */
    public byte[] interceptRequest(String url, byte[] requestBody) {
        // 目前只实现了响应拦截，可以在这里添加请求拦截逻辑
        return requestBody;
    }
    
    /**
     * 将输入流转换为字节数组
     * @param inputStream 输入流
     * @return 字节数组
     * @throws IOException 如果读取失败
     */
    public static byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, length);
        }
        return byteArrayOutputStream.toByteArray();
    }
    
    /**
     * 将字节数组转换为输入流
     * @param bytes 字节数组
     * @return 输入流
     */
    public static InputStream toInputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
} 
package com.example.packetcapture.proxy;

import android.util.Log;

import com.example.packetcapture.config.RewriteConfig;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class ProxyConnectionHandler implements Runnable {
    private static final String TAG = "ProxyConnectionHandler";
    private static final Pattern HTTP_REQUEST_PATTERN = Pattern.compile("^(GET|POST|PUT|DELETE|HEAD|OPTIONS|TRACE|CONNECT)\\s+(\\S+)\\s+HTTP/\\d\\.\\d$");
    
    private Socket clientSocket;
    private RewriteConfig rewriteConfig;
    private HttpsInterceptor httpsInterceptor;
    
    public ProxyConnectionHandler(Socket clientSocket, RewriteConfig rewriteConfig) {
        this.clientSocket = clientSocket;
        this.rewriteConfig = rewriteConfig;
        this.httpsInterceptor = new HttpsInterceptor(rewriteConfig);
    }
    
    @Override
    public void run() {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();
            
            // 解析HTTP请求
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput));
            String requestLine = reader.readLine();
            
            if (requestLine == null) {
                closeQuietly(clientSocket);
                return;
            }
            
            Matcher matcher = HTTP_REQUEST_PATTERN.matcher(requestLine);
            if (!matcher.matches()) {
                Log.w(TAG, "无效的HTTP请求: " + requestLine);
                closeQuietly(clientSocket);
                return;
            }
            
            String method = matcher.group(1);
            String requestUrl = matcher.group(2);
            
            // 如果是CONNECT方法（HTTPS隧道），处理HTTPS连接
            if ("CONNECT".equalsIgnoreCase(method)) {
                handleHttpsConnect(requestLine, reader, clientInput, clientOutput);
                return;
            }
            
            // 处理普通HTTP请求
            handleHttpRequest(method, requestUrl, reader, clientOutput);
            
        } catch (IOException e) {
            Log.e(TAG, "处理代理连接时出错", e);
        } finally {
            closeQuietly(clientSocket);
        }
    }
    
    private void handleHttpRequest(String method, String requestUrl, BufferedReader reader, OutputStream clientOutput) throws IOException {
        // 解析请求头
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
        
        // 创建连接到目标服务器的请求
        URL url = new URL(requestUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        
        // 设置请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        
        // 如果是POST等方法，需要处理请求体
        if ("POST".equals(method) || "PUT".equals(method)) {
            connection.setDoOutput(true);
            int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
            if (contentLength > 0) {
                byte[] buffer = new byte[contentLength];
                int bytesRead = 0;
                int offset = 0;
                while (offset < contentLength && (bytesRead = reader.read(buffer, offset, contentLength - offset)) != -1) {
                    offset += bytesRead;
                }
                
                // 拦截请求体
                buffer = httpsInterceptor.interceptRequest(requestUrl, buffer);
                
                OutputStream out = connection.getOutputStream();
                out.write(buffer);
                out.flush();
            }
        }
        
        // 获取响应
        int responseCode = connection.getResponseCode();
        
        // 读取原始响应体
        byte[] responseBody;
        try {
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            responseBody = byteArrayOutputStream.toByteArray();
            in.close();
        } catch (IOException e) {
            // 处理错误响应
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = errorStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                responseBody = byteArrayOutputStream.toByteArray();
                errorStream.close();
            } else {
                responseBody = new byte[0];
            }
        }
        
        // 拦截并修改响应体
        responseBody = httpsInterceptor.interceptResponse(requestUrl, responseBody);
        
        // 写入响应头
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("HTTP/1.1 ").append(responseCode).append(" ")
                .append(connection.getResponseMessage()).append("\r\n");
        
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            String key = entry.getKey();
            if (key != null) {
                for (String value : entry.getValue()) {
                    // 如果我们修改了响应体，需要更新Content-Length头
                    if (key.equalsIgnoreCase("Content-Length")) {
                        responseBuilder.append(key).append(": ").append(responseBody.length).append("\r\n");
                    } else {
                        responseBuilder.append(key).append(": ").append(value).append("\r\n");
                    }
                }
            }
        }
        responseBuilder.append("\r\n");
        clientOutput.write(responseBuilder.toString().getBytes());
        
        // 写入响应体
        clientOutput.write(responseBody);
        clientOutput.flush();
        connection.disconnect();
    }
    
    private void handleHttpsConnect(String requestLine, BufferedReader reader, InputStream clientInput, OutputStream clientOutput) throws IOException {
        // 解析主机和端口
        String[] parts = requestLine.split("\\s+")[1].split(":");
        String host = parts[0];
        int port = (parts.length > 1) ? Integer.parseInt(parts[1]) : 443;
        
        // 读取所有请求头
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // 忽略请求头
        }
        
        // 连接到目标服务器
        Socket serverSocket = new Socket(host, port);
        
        try {
            // 告诉客户端连接已建立
            clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
            clientOutput.flush();
            
            // 创建双向管道
            Thread clientToServer = new Thread(() -> {
                try {
                    pipe(clientInput, serverSocket.getOutputStream());
                } catch (IOException e) {
                    closeQuietly(clientSocket);
                    closeQuietly(serverSocket);
                }
            });
            
            Thread serverToClient = new Thread(() -> {
                try {
                    // 这里可以添加响应拦截和修改逻辑
                    InputStream serverInput = serverSocket.getInputStream();
                    
                    // 注意：HTTPS流量是加密的，需要更复杂的解密机制才能修改内容
                    // 这里简化处理，直接传递数据
                    // 如果要实现真正的HTTPS拦截，需要实现中间人代理（MITM）
                    pipe(serverInput, clientOutput);
                } catch (IOException e) {
                    closeQuietly(clientSocket);
                    closeQuietly(serverSocket);
                }
            });
            
            clientToServer.start();
            serverToClient.start();
            
            // 等待两个线程完成
            try {
                clientToServer.join();
                serverToClient.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            closeQuietly(serverSocket);
        }
    }
    
    private void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            out.flush();
        }
    }
    
    private void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭套接字时出错", e);
            }
        }
    }
} 
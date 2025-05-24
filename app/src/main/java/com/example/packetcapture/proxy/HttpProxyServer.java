package com.example.packetcapture.proxy;

import android.util.Log;

import com.example.packetcapture.config.RewriteConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxyServer {
    private static final String TAG = "HttpProxyServer";
    private static final int DEFAULT_PORT = 8888;
    
    private int port;
    private boolean isRunning;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private RewriteConfig rewriteConfig;
    
    public HttpProxyServer(int port, RewriteConfig rewriteConfig) {
        this.port = port;
        this.rewriteConfig = rewriteConfig;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    public HttpProxyServer(RewriteConfig rewriteConfig) {
        this(DEFAULT_PORT, rewriteConfig);
    }
    
    public void start() {
        if (isRunning) return;
        
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            
            isRunning = true;
            Log.i(TAG, "HTTP代理服务器启动在端口: " + port);
            
            // 启动接受连接的线程
            new Thread(this::acceptConnections).start();
        } catch (IOException e) {
            Log.e(TAG, "启动HTTP代理服务器失败", e);
        }
    }
    
    private void acceptConnections() {
        while (isRunning && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ProxyConnectionHandler(clientSocket, rewriteConfig));
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "接受连接时出错", e);
                }
            }
        }
    }
    
    public void stop() {
        isRunning = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭服务器套接字时出错", e);
            }
        }
        
        executorService.shutdown();
        Log.i(TAG, "HTTP代理服务器已停止");
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public int getPort() {
        return port;
    }
} 
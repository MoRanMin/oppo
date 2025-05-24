package com.example.packetcapture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.packetcapture.config.RewriteConfig;
import com.example.packetcapture.proxy.HttpProxyServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class VpnService extends android.net.VpnService {
    private static final String TAG = "PacketCaptureVPN";
    private static final String CHANNEL_ID = "VPN_CHANNEL";
    private static final int NOTIFICATION_ID = 1;
    private static final int PROXY_PORT = 8888;
    
    private ParcelFileDescriptor vpnInterface;
    private ExecutorService executorService;
    private boolean isRunning = false;
    private ConcurrentLinkedQueue<PacketInfo> capturedPackets;
    private PacketCallback packetCallback;
    private AtomicInteger packetCount = new AtomicInteger(0);
    
    // 新增的HTTP代理服务器
    private HttpProxyServer proxyServer;
    private RewriteConfig rewriteConfig;

    public interface PacketCallback {
        void onPacketCaptured(PacketInfo packet);
        void onCountUpdated(int count);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        capturedPackets = new ConcurrentLinkedQueue<>();
        executorService = Executors.newFixedThreadPool(2);
        
        // 初始化配置
        rewriteConfig = new RewriteConfig();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            return START_STICKY;
        }

        // 加载重写配置
        if (intent != null && intent.hasExtra("config_path")) {
            String configPath = intent.getStringExtra("config_path");
            File configFile = new File(configPath);
            if (configFile.exists()) {
                rewriteConfig.loadConfigFromFile(configFile);
                Log.i(TAG, "已加载重写配置: " + configPath);
            } else {
                Log.e(TAG, "配置文件不存在: " + configPath);
            }
        }
        
        // 创建通知通道
        createNotificationChannel();
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, buildNotification());
        
        // 启动HTTP代理服务器
        proxyServer = new HttpProxyServer(PROXY_PORT, rewriteConfig);
        proxyServer.start();
        
        // 启动VPN服务
        startVpn();
        
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VPN Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("VPN Service for packet capture");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("抓包工具")
                .setContentText("正在捕获网络数据包")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void startVpn() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
            vpnInterface = null;
        }

        try {
            // 配置VPN
            Builder builder = new Builder()
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .setSession("Packet Capture VPN")
                    .setMtu(1500);

            // 将所有流量重定向到代理服务器
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("8.8.8.8");
            
            // 对所有应用拦截流量，但排除自己
            builder.addDisallowedApplication(getPackageName());

            // 建立VPN接口
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN connection");
                return;
            }

            isRunning = true;
            packetCount.set(0);

            // 启动数据包处理线程
            executorService.submit(new VpnRunnable());
        } catch (Exception e) {
            Log.e(TAG, "Error setting up VPN", e);
            stopVpn();
        }
    }

    public void stopVpn() {
        isRunning = false;
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
        }
        
        // 停止代理服务器
        if (proxyServer != null && proxyServer.isRunning()) {
            proxyServer.stop();
        }
        
        stopForeground(true);
        stopSelf();
    }

    public void setPacketCallback(PacketCallback callback) {
        this.packetCallback = callback;
    }

    public int getPacketCount() {
        return packetCount.get();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }

    private class VpnRunnable implements Runnable {
        @Override
        public void run() {
            FileInputStream vpnInput = null;
            FileOutputStream vpnOutput = null;

            try {
                vpnInput = new FileInputStream(vpnInterface.getFileDescriptor());
                vpnOutput = new FileOutputStream(vpnInterface.getFileDescriptor());

                ByteBuffer packet = ByteBuffer.allocate(32767);
                
                while (isRunning) {
                    // 清空缓冲区
                    packet.clear();
                    
                    // 读取数据包
                    int length = vpnInput.read(packet.array());
                    if (length > 0) {
                        // 设置缓冲区位置和限制
                        packet.limit(length);
                        
                        // 处理数据包
                        processPacket(packet, length);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in VPN thread", e);
            } finally {
                try {
                    if (vpnInput != null) vpnInput.close();
                    if (vpnOutput != null) vpnOutput.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing streams", e);
                }
            }
        }

        private void processPacket(ByteBuffer packet, int length) {
            try {
                // 读取IP头部信息
                byte version = (byte) (packet.get(0) >> 4);
                if (version == 4) {
                    processIPv4Packet(packet, length);
                } else if (version == 6) {
                    processIPv6Packet(packet, length);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing packet", e);
            }
        }

        private void processIPv4Packet(ByteBuffer packet, int length) {
            try {
                // 解析IPv4头部
                byte protocol = packet.get(9);
                byte[] sourceIP = new byte[4];
                byte[] destIP = new byte[4];
                
                // 源IP地址在偏移量12的位置
                packet.position(12);
                packet.get(sourceIP, 0, 4);
                
                // 目标IP地址在偏移量16的位置
                packet.get(destIP, 0, 4);
                
                // 获取IP头部长度
                int headerLength = (packet.get(0) & 0x0F) * 4;
                
                // 解析传输层协议
                String protocolName;
                int sourcePort = 0;
                int destPort = 0;
                String details = "";
                
                if (protocol == 6) { // TCP
                    protocolName = "TCP";
                    // 解析TCP头部
                    packet.position(headerLength);
                    sourcePort = (packet.get() & 0xFF) << 8 | (packet.get() & 0xFF);
                    destPort = (packet.get() & 0xFF) << 8 | (packet.get() & 0xFF);
                    
                    // 获取TCP标志
                    packet.position(headerLength + 13);
                    byte flags = packet.get();
                    boolean isSyn = (flags & 0x02) != 0;
                    boolean isAck = (flags & 0x10) != 0;
                    boolean isFin = (flags & 0x01) != 0;
                    boolean isRst = (flags & 0x04) != 0;
                    
                    details = "标志: " + (isSyn ? "SYN " : "") + (isAck ? "ACK " : "") + 
                             (isFin ? "FIN " : "") + (isRst ? "RST " : "");
                    
                    // 检查是否是HTTP或HTTPS流量
                    if (destPort == 80 || destPort == 443) {
                        details += " | " + (destPort == 80 ? "HTTP" : "HTTPS");
                    }
                } else if (protocol == 17) { // UDP
                    protocolName = "UDP";
                    // 解析UDP头部
                    packet.position(headerLength);
                    sourcePort = (packet.get() & 0xFF) << 8 | (packet.get() & 0xFF);
                    destPort = (packet.get() & 0xFF) << 8 | (packet.get() & 0xFF);
                    
                    int udpLength = (packet.get() & 0xFF) << 8 | (packet.get() & 0xFF);
                    details = "UDP长度: " + udpLength + " 字节";
                } else if (protocol == 1) { // ICMP
                    protocolName = "ICMP";
                    packet.position(headerLength);
                    byte type = packet.get();
                    byte code = packet.get();
                    details = "类型: " + type + ", 代码: " + code;
                } else {
                    protocolName = "IP协议: " + protocol;
                }
                
                // 创建数据包信息对象
                PacketInfo packetInfo = new PacketInfo(
                        protocolName,
                        InetAddress.getByAddress(sourceIP).getHostAddress(),
                        InetAddress.getByAddress(destIP).getHostAddress(),
                        sourcePort,
                        destPort,
                        System.currentTimeMillis(),
                        details,
                        length
                );
                
                // 更新计数器并通知回调
                int count = packetCount.incrementAndGet();
                if (packetCallback != null) {
                    packetCallback.onPacketCaptured(packetInfo);
                    packetCallback.onCountUpdated(count);
                }
                
                // 保存到队列
                capturedPackets.add(packetInfo);
                
                // 限制队列大小，防止内存溢出
                while (capturedPackets.size() > 1000) {
                    capturedPackets.poll();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing IPv4 packet", e);
            }
        }

        private void processIPv6Packet(ByteBuffer packet, int length) {
            // IPv6处理逻辑类似于IPv4，但这里简化处理
            try {
                PacketInfo packetInfo = new PacketInfo(
                        "IPv6",
                        "IPv6地址",
                        "IPv6地址",
                        0,
                        0,
                        System.currentTimeMillis(),
                        "IPv6数据包",
                        length
                );
                
                int count = packetCount.incrementAndGet();
                if (packetCallback != null) {
                    packetCallback.onPacketCaptured(packetInfo);
                    packetCallback.onCountUpdated(count);
                }
                
                capturedPackets.add(packetInfo);
            } catch (Exception e) {
                Log.e(TAG, "Error processing IPv6 packet", e);
            }
        }
    }
} 
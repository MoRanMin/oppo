package com.example.packetcapture;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int VPN_REQUEST_CODE = 1;

    private TextView statusTextView;
    private TextView packetCountTextView;
    private Button startStopButton;
    private Button clearButton;
    private Button exportButton;
    private Button selectConfigButton;
    private TextView configPathTextView;
    private RecyclerView packetsRecyclerView;
    private PacketAdapter packetAdapter;

    private boolean isCapturing = false;
    private com.example.packetcapture.VpnService vpnService;
    private boolean isBound = false;
    private String selectedConfigPath = null;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // 我们不使用Binder，因为VpnService不支持直接绑定
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vpnService = null;
            isBound = false;
            updateUI(false);
        }
    };
    
    // 文件选择器结果处理
    private final ActivityResultLauncher<String> configFilePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    handleSelectedConfig(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        statusTextView = findViewById(R.id.statusTextView);
        packetCountTextView = findViewById(R.id.packetCountTextView);
        startStopButton = findViewById(R.id.startStopButton);
        clearButton = findViewById(R.id.clearButton);
        exportButton = findViewById(R.id.exportButton);
        selectConfigButton = findViewById(R.id.selectConfigButton);
        configPathTextView = findViewById(R.id.configPathTextView);
        packetsRecyclerView = findViewById(R.id.packetsRecyclerView);

        // 设置RecyclerView
        packetsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        packetAdapter = new PacketAdapter();
        packetsRecyclerView.setAdapter(packetAdapter);

        // 更新UI状态
        updateUI(isCapturing);

        // 设置按钮点击事件
        startStopButton.setOnClickListener(v -> {
            if (isCapturing) {
                stopCapture();
            } else {
                startCapture();
            }
        });

        clearButton.setOnClickListener(v -> {
            packetAdapter.clearPackets();
            packetCountTextView.setText(getString(R.string.packet_count, 0));
            Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show();
        });

        exportButton.setOnClickListener(v -> {
            exportPacketsToFile();
        });
        
        selectConfigButton.setOnClickListener(v -> {
            selectConfigFile();
        });

        // 设置数据包点击事件
        packetAdapter.setOnPacketClickListener(position -> {
            // 显示数据包详情，这里简单用Toast显示
            PacketInfo packet = packetAdapter.getPacketList().get(position);
            Toast.makeText(this, 
                    "协议: " + packet.getProtocol() + "\n" +
                    "源: " + packet.getSourceAddressWithPort() + "\n" +
                    "目标: " + packet.getDestinationAddressWithPort() + "\n" +
                    "详情: " + packet.getDetails(),
                    Toast.LENGTH_LONG).show();
        });
    }
    
    private void selectConfigFile() {
        configFilePicker.launch("*/*");
    }
    
    private void handleSelectedConfig(Uri uri) {
        try {
            // 获取文件路径
            String path = getPathFromUri(uri);
            if (path != null) {
                selectedConfigPath = path;
                configPathTextView.setText("已选择配置: " + new File(path).getName());
                Toast.makeText(this, "已选择配置文件: " + new File(path).getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "无法获取文件路径", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "处理选择的配置文件时出错", e);
            Toast.makeText(this, "处理配置文件时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getPathFromUri(Uri uri) {
        try {
            // 对于Android 10及以上，直接使用URI
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // 将文件复制到应用私有目录
                File destFile = new File(getFilesDir(), "config.json");
                
                try (java.io.InputStream is = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream fos = new FileOutputStream(destFile)) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    
                    return destFile.getAbsolutePath();
                }
            } else {
                // 对于较老版本，尝试获取实际路径
                String[] projection = {android.provider.MediaStore.MediaColumns.DATA};
                android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
                
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA);
                            return cursor.getString(columnIndex);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                
                // 如果无法获取路径，复制文件
                return getPathFromUri(uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取URI路径时出错", e);
            return null;
        }
    }

    private void startCapture() {
        Intent vpnPrepareIntent = VpnService.prepare(this);
        if (vpnPrepareIntent != null) {
            startActivityForResult(vpnPrepareIntent, VPN_REQUEST_CODE);
        } else {
            onVpnPermissionGranted();
        }
    }

    private void onVpnPermissionGranted() {
        Intent intent = new Intent(this, com.example.packetcapture.VpnService.class);
        
        // 如果选择了配置文件，传递给服务
        if (selectedConfigPath != null) {
            intent.putExtra("config_path", selectedConfigPath);
        }
        
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // 注册回调
        com.example.packetcapture.VpnService.PacketCallback callback = new com.example.packetcapture.VpnService.PacketCallback() {
            @Override
            public void onPacketCaptured(PacketInfo packet) {
                runOnUiThread(() -> packetAdapter.addPacket(packet));
            }

            @Override
            public void onCountUpdated(int count) {
                runOnUiThread(() -> packetCountTextView.setText(getString(R.string.packet_count, count)));
            }
        };
        
        // 全局静态方法设置回调
        vpnService = null; // 通过服务绑定获取实例
        
        isCapturing = true;
        updateUI(true);
    }

    private void stopCapture() {
        Intent intent = new Intent(this, com.example.packetcapture.VpnService.class);
        stopService(intent);
        
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        
        isCapturing = false;
        updateUI(false);
    }

    private void updateUI(boolean isRunning) {
        if (isRunning) {
            statusTextView.setText(getString(R.string.capture_status, getString(R.string.status_running)));
            startStopButton.setText(R.string.stop_capture);
            selectConfigButton.setEnabled(false);
        } else {
            statusTextView.setText(getString(R.string.capture_status, getString(R.string.status_stopped)));
            startStopButton.setText(R.string.start_capture);
            selectConfigButton.setEnabled(true);
        }
    }

    private void exportPacketsToFile() {
        List<PacketInfo> packets = packetAdapter.getPacketList();
        if (packets.isEmpty()) {
            Toast.makeText(this, "没有数据包可导出", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 检查存储权限
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要存储权限才能导出文件", Toast.LENGTH_SHORT).show();
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                return;
            }

            // 创建导出文件
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "packet_capture_" + sdf.format(new Date()) + ".csv";
            File outputFile = new File(downloadsDir, fileName);

            FileWriter writer = new FileWriter(outputFile);
            writer.append("协议,源地址,目标地址,时间戳,详情,大小(字节)\n");

            for (PacketInfo packet : packets) {
                writer.append(packet.getProtocol()).append(",")
                      .append(packet.getSourceAddressWithPort()).append(",")
                      .append(packet.getDestinationAddressWithPort()).append(",")
                      .append(packet.getFormattedTimestamp()).append(",")
                      .append(packet.getDetails().replace(",", ";")).append(",")
                      .append(String.valueOf(packet.getSize())).append("\n");
            }

            writer.flush();
            writer.close();

            Toast.makeText(this, "数据包已导出到: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "Error exporting packets", e);
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            onVpnPermissionGranted();
        } else {
            Toast.makeText(this, R.string.vpn_permission_required, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }
} 
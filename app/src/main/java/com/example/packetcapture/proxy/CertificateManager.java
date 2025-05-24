package com.example.packetcapture.proxy;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

public class CertificateManager {
    private static final String TAG = "CertificateManager";
    private static final String KEYSTORE_PASSWORD = "packetcapture";
    private static final String CA_ALIAS = "packet_capture_ca";
    private static final String KEYSTORE_FILE = "packet_capture.keystore";
    
    private Context context;
    private KeyStore keyStore;
    private PrivateKey caPrivateKey;
    private X509Certificate caCertificate;
    private Map<String, SSLSocketFactory> socketFactoryCache;
    
    public CertificateManager(Context context) {
        this.context = context;
        this.socketFactoryCache = new HashMap<>();
        initKeyStore();
    }
    
    private void initKeyStore() {
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
            
            if (keystoreFile.exists()) {
                // 加载现有的KeyStore
                try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                    keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
                    
                    // 获取CA证书和私钥
                    if (keyStore.containsAlias(CA_ALIAS)) {
                        caPrivateKey = (PrivateKey) keyStore.getKey(CA_ALIAS, KEYSTORE_PASSWORD.toCharArray());
                        caCertificate = (X509Certificate) keyStore.getCertificate(CA_ALIAS);
                    } else {
                        generateCACertificate();
                    }
                }
            } else {
                // 创建新的KeyStore
                keyStore.load(null, KEYSTORE_PASSWORD.toCharArray());
                generateCACertificate();
                
                // 保存KeyStore
                try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
                    keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化KeyStore时出错", e);
        }
    }
    
    private void generateCACertificate() throws Exception {
        // 生成RSA密钥对
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        
        PublicKey publicKey = keyPair.getPublic();
        caPrivateKey = keyPair.getPrivate();
        
        // 创建CA证书
        X500Principal subject = new X500Principal("CN=Packet Capture CA, O=Packet Capture, C=CN");
        
        // 这里简化了证书生成过程，实际应用中应使用更完整的证书生成库
        // 例如 Bouncy Castle
        
        // 保存到KeyStore
        Certificate[] chain = new Certificate[1];
        chain[0] = caCertificate;
        keyStore.setKeyEntry(CA_ALIAS, caPrivateKey, KEYSTORE_PASSWORD.toCharArray(), chain);
        
        // 保存KeyStore
        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
    }
    
    public SSLSocketFactory getSocketFactory(String hostname) {
        // 检查缓存
        if (socketFactoryCache.containsKey(hostname)) {
            return socketFactoryCache.get(hostname);
        }
        
        try {
            // 创建服务器证书
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            
            // 使用CA私钥签名服务器证书
            // 这里简化了证书生成过程
            
            // 创建KeyStore并导入证书
            KeyStore serverKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            serverKeyStore.load(null, KEYSTORE_PASSWORD.toCharArray());
            
            // 创建SSLContext
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(serverKeyStore, KEYSTORE_PASSWORD.toCharArray());
            
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            socketFactoryCache.put(hostname, socketFactory);
            
            return socketFactory;
        } catch (Exception e) {
            Log.e(TAG, "创建SSL Socket Factory时出错", e);
            return null;
        }
    }
    
    public File exportCACertificate() {
        try {
            File certFile = new File(context.getExternalFilesDir(null), "packet_capture_ca.crt");
            
            // 导出证书到文件
            try (FileOutputStream fos = new FileOutputStream(certFile)) {
                fos.write(caCertificate.getEncoded());
            }
            
            return certFile;
        } catch (Exception e) {
            Log.e(TAG, "导出CA证书时出错", e);
            return null;
        }
    }
} 
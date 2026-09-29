package com.doxton.questfan;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small, dependency-free client for the already-running ESP32 firmware. */
public final class FanProtocol {
    private FanProtocol() { }

    public static String normalizedHost(String input) {
        if (input == null) throw new IllegalArgumentException("请输入风扇板 IP");
        String host = input.trim();
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("请输入四段 IPv4 地址");
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) throw new IllegalArgumentException("IP 格式不正确");
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') throw new IllegalArgumentException("IP 格式不正确");
            }
            if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("IP 范围应为 0–255");
        }
        return host;
    }

    public static String gearPath(int speed) {
        if (speed != 80 && speed != 90 && speed != 100) throw new IllegalArgumentException("只允许三挡");
        return "/gear?v=" + speed;
    }

    public static String request(String host, String path) throws IOException {
        if (!(path.equals("/state") || path.equals("/off") || path.equals("/gear?v=80")
                || path.equals("/gear?v=90") || path.equals("/gear?v=100"))) {
            throw new IllegalArgumentException("无效的风扇操作");
        }
        HttpURLConnection conn = (HttpURLConnection) new URL("http://" + host + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setUseCaches(false);
        try {
            int status = conn.getResponseCode();
            if (status != 200) throw new IOException("风扇板返回 HTTP " + status);
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[1024];
                int n;
                while ((n = in.read(bytes)) != -1) {
                    if (out.size() + n > 4096) throw new IOException("响应过长");
                    out.write(bytes, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { conn.disconnect(); }
    }
}

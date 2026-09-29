package com.doxton.questfan;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class FanProtocolTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        check(FanProtocol.normalizedHost(" 192.168.10.178 ").equals("192.168.10.178"), "trim IP");
        for (String bad : new String[]{"", "http://1.2.3.4", "1.2.3.999", "1.2.3.4/foo", "abc.local", "1.2.3.4:80"}) {
            try { FanProtocol.normalizedHost(bad); throw new AssertionError("accepted " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/state", exchange -> {
            byte[] body = "{\"on\":false,\"speed\":100,\"ip\":\"127.0.0.1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/gear", exchange -> {
            check("v=90".equals(exchange.getRequestURI().getQuery()), "gear query");
            byte[] body = "{\"on\":true,\"speed\":90,\"ip\":\"127.0.0.1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/off", exchange -> {
            byte[] body = "{\"on\":false,\"speed\":90,\"ip\":\"127.0.0.1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            String host = "127.0.0.1:" + server.getAddress().getPort();
            check(FanProtocol.request(host, "/state").contains("\"on\":false"), "read actual state");
            check(FanProtocol.request(host, "/gear?v=90").contains("\"speed\":90"), "select gear");
            check(FanProtocol.request(host, "/off").contains("\"on\":false"), "off");
            server.removeContext("/state");
            try { FanProtocol.request(host, "/state"); throw new AssertionError("404 accepted"); }
            catch (java.io.IOException expected) { }
            try { FanProtocol.gearPath(85); throw new AssertionError("invalid gear accepted"); }
            catch (IllegalArgumentException expected) { }
            check(FanProtocol.gearPath(80).equals("/gear?v=80"), "gear path");
        } finally { server.stop(0); }
        System.out.println("FanProtocolTest PASS");
    }
}

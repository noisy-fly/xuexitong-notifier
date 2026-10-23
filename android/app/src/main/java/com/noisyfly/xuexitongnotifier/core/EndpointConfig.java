package com.noisyfly.xuexitongnotifier.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class EndpointConfig {
    private static final Pattern TOPIC_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final String serverUrl;
    private final String topic;
    private final String token;

    public EndpointConfig(String serverUrl, String topic, String token) {
        this.serverUrl = normalizeServer(serverUrl);
        this.topic = normalizeTopic(topic);
        this.token = normalizeToken(token);
    }

    public String serverUrl() {
        return serverUrl;
    }

    public String topic() {
        return topic;
    }

    public String token() {
        return token;
    }

    public String subscriptionUrl() {
        return serverUrl + "/" + topic + "/json";
    }

    public String authorizationHeader() {
        return token.isEmpty() ? null : "Bearer " + token;
    }

    private static String normalizeServer(String value) {
        String candidate = value == null ? "" : value.trim();
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme() == null
                    ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http"))) {
                throw new IllegalArgumentException("服务器地址必须使用 HTTP 或 HTTPS");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("服务器地址缺少主机名");
            }
            String path = uri.getPath();
            if (path != null && !(path.isEmpty() || path.equals("/"))) {
                throw new IllegalArgumentException("服务器地址不能包含路径");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("服务器地址不能包含账号、查询参数或片段");
            }
            return new URI(
                    scheme,
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    null,
                    null,
                    null
            ).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("服务器地址格式不正确", exception);
        }
    }

    private static String normalizeTopic(String value) {
        String candidate = value == null ? "" : value.trim();
        if (!TOPIC_PATTERN.matcher(candidate).matches()) {
            throw new IllegalArgumentException("主题只能包含字母、数字、下划线和连字符");
        }
        return candidate;
    }

    private static String normalizeToken(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.indexOf('\r') >= 0 || candidate.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("访问令牌格式不正确");
        }
        return candidate;
    }
}


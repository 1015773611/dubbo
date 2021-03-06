package my.server;

import my.common.utils.ArrayUtils;
import my.common.utils.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geyu
 * @date 2021/1/28 18:12
 */
public class URL {
    private String address;
    private String protocol;
    private String username;
    private String pwd;
    private String path;
    private String host;
    private int port;
    private Map<String, String> parameters;
    private Map<String, Number> numberMap = new ConcurrentHashMap<>();
    private Map<String, Map<String, Number>> methodNumbers;
    private Map<String, Map<String, String>> methodParameters;
    private String ip;
    private volatile transient String identity;


    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Map<String, String> getParameters() {
        if (parameters == null) {
            parameters = new ConcurrentHashMap<>();
        }
        return parameters;
    }


    public int getPositiveParameter(String key, int defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <=0");
        }
        int value = getParameter(key, defaultValue);
        return value < 0 ? defaultValue : value;
    }


    public String getParameter(String key) {
        return parameters.get(key);
    }

    public int getParameter(String key, int defaultValue) {
        Number number = numberMap.get(key);
        if (number == null && parameters != null) {
            String val = parameters.get(key);
            if (val != null) {
                number = Integer.parseInt(val);
                numberMap.put(key, number);
            } else {
                return defaultValue;
            }
        }
        return number == null ? -1 : number.intValue();
    }


    public boolean getParameter(String key, boolean defaultValue) {
        if (parameters != null) { // todo myRPC 这里不应该判空
            String value = parameters.get(key);
            return (value == null || value.isEmpty()) ? defaultValue : Boolean.parseBoolean(value);
        }
        return false;
    }


    public String getParameter(String key, String defaultValue) {
        if (parameters != null) { // todo myRPC 这里不应该判空
            String value = parameters.get(key);
            return (value == null || value.isEmpty()) ? defaultValue : value;
        }
        return defaultValue;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    private URL(Builder b) {
        this.protocol = b.protocol;
        this.username = b.username;
        this.pwd = b.pwd;
        this.path = b.path;
        this.host = b.host;
        this.port = b.port;
        this.parameters = b.parameters != null ? b.parameters : new HashMap<>();
        this.address = host + ":" + port;
    }

    public URL() {

    }

    public String getIp() {
        if (ip == null) {
            ip = NetUtils.getIpByHost(host);
        }
        return ip;
    }

    public String getAddress() {
        return address;
    }

    public void addParameterIfAbsent(String key, String value) {
        if (StringUtils.isEmpty(key)
                || StringUtils.isEmpty(value)) {
            return;
        }
        if (hasParameter(key)) {
            return;
        }
        Map<String, String> map = new HashMap<>(getParameters());
        map.put(key, value);
        parameters = map;
    }

    public boolean hasParameter(String key) {
        String value = getParameter(key);
        return value != null && value.length() > 0;
    }

    public boolean getMethodParameter(String methodName, String key, boolean defaultValue) {
        String val = getMethodParameter(methodName, key);
        return StringUtils.isEmpty(val) ? defaultValue : Boolean.parseBoolean(val);
    }

    public long getMethodPositiveParameter(String methodName, String key, long defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        long val = getMethodParameter(methodName, key, defaultValue);
        return val <= 0 ? defaultValue : val;
    }

    public long getMethodParameter(String method, String key, long defaultValue) {
        Number n = getCachedNumber(method, key);
        if (n != null) {
            return n.longValue();
        }
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }

        long l = Long.parseLong(value);
        updateCachedNumber(method, key, l);
        return l;
    }

    public int getMethodParameter(String method, String key, int defaultValue) {
        Number n = getCachedNumber(method, key);
        if (n != null) {
            return n.intValue();
        }
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }

        int i = Integer.parseInt(value);
        updateCachedNumber(method, key, i);
        return i;
    }


    public String getMethodParameter(String method, String key) {
        Map<String, String> keyMap = getMethodParameters().get(method);
        String value = null;
        if (keyMap != null) {
            value = keyMap.get(key);
        }
        if (StringUtils.isEmpty(value)) {
            value = parameters.get(key);
        }
        return value;
    }


    private void updateCachedNumber(String method, String key, long l) {

        Map<String, Number> stringNumberMap = getMethodNumbers().computeIfAbsent(method, m -> new HashMap<>());
        stringNumberMap.put(key, l);
    }


    private Number getCachedNumber(String method, String key) {
        Map<String, Number> stringNumberMap = getMethodNumbers().get(method);
        if (stringNumberMap != null) {
            return stringNumberMap.get(key);
        }
        return null;
    }

    private Map<String, Map<String, Number>> getMethodNumbers() {
        if (methodNumbers == null) {
            methodNumbers = new ConcurrentHashMap<>();
        }
        return methodNumbers;
    }


    public Map<String, Map<String, String>> getMethodParameters() {
        if (methodParameters == null) {
            methodParameters = new ConcurrentHashMap<>();
        }
        return methodParameters;
    }



    public static class Builder {
        String protocol;
        String username;
        String pwd;
        String path;
        String host;
        int port = 0;
        Map<String, String> parameters = null;

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder pwd(String pwd) {
            this.pwd = pwd;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder parameters(Map<String, String> parameters) {
            this.parameters = parameters;
            return this;
        }

        public URL build() {
            return new URL(this);
        }
    }

    public static URL valueOf(String url) {
        if (url == null || ((url = url.trim()).length() == 0)) {
            throw new IllegalArgumentException("url == null");
        }
        String protocol = null;
        String username = null;
        String pwd = null;
        String path = null;
        String host = null;
        int port = 0;
        Map<String, String> parameters = null;
        // 1.参数对
        int i = url.indexOf("?");
        if (i > 0) {
            String[] parts = url.substring(i + 1).split("&");
            parameters = new HashMap<>();
            for (String part : parts) {
                part = part.trim();
                if (part.length() > 0) {
                    int j = part.indexOf("=");
                    String k = part.substring(0, j);
                    String v = part.substring(j + 1);
                    parameters.put(k, v);
                } else {
                    parameters.put(part, part);
                }
            }
            url = url.substring(0, i);
        }
        // 2.protocol
        i = url.indexOf("://");
        if (i >= 0) {
            if (i == 0) {
                throw new IllegalArgumentException("url missing protocol");
            } else {
                protocol = url.substring(0, i);
            }
            url = url.substring(i + 3);
        } else {
            // todo myRPC
        }
        // 3.path
        i = url.indexOf("/");
        if (i >= 0) {
            path = url.substring(i + 1);
            url = url.substring(0, i);
        }
        // 4.username:pwd
        i = url.lastIndexOf("@");
        if (i >= 0) {
            username = url.substring(0, i);
            int j = username.indexOf(":");
            if (j >= 0) {
                pwd = username.substring(j + 1);
                username = username.substring(0, j);
            }
            url = url.substring(i + 1);
        }
        // 5.ip:port
        i = url.indexOf(":");
        if (i >= 0) {
            port = Integer.parseInt(url.substring(i + 1));
            url = url.substring(0, i);
        }
        if (url.length() > 0) {
            host = url;
        }
        return new URL.Builder().username(username).host(host).port(port).pwd(pwd).protocol(protocol).parameters(parameters).path(path).build();
    }


    public String toIdentityString() {
        if (identity != null) {
            return identity;
        }
        return identity = buildString(true, false); // only return identity message, see the method "equals" and "hashCode"
    }

    private String buildString(boolean appendUser, boolean appendParameter, String... parameters) {
        return buildString(appendUser, appendParameter, false, false, parameters);
    }
    private String buildString(boolean appendUser, boolean appendParameter, boolean useIP, boolean useService, String... parameters) {
        StringBuilder buf = new StringBuilder();
        if (StringUtils.isNotEmpty(protocol)) {
            buf.append(protocol);
            buf.append("://");
        }
        if (appendUser && StringUtils.isNotEmpty(username)) {
            buf.append(username);
            if (StringUtils.isNotEmpty(pwd)) {
                buf.append(":");
                buf.append(pwd);
            }
            buf.append("@");
        }
        String host;
        if (useIP) {
            host = getIp();
        } else {
            host = getHost();
        }
        if (StringUtils.isNotEmpty(host)) {
            buf.append(host);
            if (port > 0) {
                buf.append(":");
                buf.append(port);
            }
        }
        String path = null;
        if (useService) {
            // path = getServiceKey();
        } else {
            path = getPath();
        }
        if (StringUtils.isNotEmpty(path)) {
            buf.append("/");
            buf.append(path);
        }

        if (appendParameter) {
            buildParameters(buf, true, parameters);
        }
        return buf.toString();
    }


    private void buildParameters(StringBuilder buf, boolean concat, String[] parameters) {
        if (CollectionUtils.isNotEmptyMap(getParameters())) {
            List<String> includes = (ArrayUtils.isEmpty(parameters) ? null : Arrays.asList(parameters));
            boolean first = true;
            for (Map.Entry<String, String> entry : new TreeMap<>(getParameters()).entrySet()) {
                if (StringUtils.isNotEmpty(entry.getKey())
                        && (includes == null || includes.contains(entry.getKey()))) {
                    if (first) {
                        if (concat) {
                            buf.append("?");
                        }
                        first = false;
                    } else {
                        buf.append("&");
                    }
                    buf.append(entry.getKey());
                    buf.append("=");
                    buf.append(entry.getValue() == null ? "" : entry.getValue().trim());
                }
            }
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public URL addParameter(String key, boolean value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, char value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, byte value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, short value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, int value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, long value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, float value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, double value) {
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, Enum<?> value) {
        if (value == null) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, Number value) {
        if (value == null) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, CharSequence value) {
        if (value == null || value.length() == 0) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    public URL addParameter(String key, String value) {
        if (StringUtils.isEmpty(key)
                || StringUtils.isEmpty(value)) {
            return this;
        }
        // if value doesn't change, return immediately
        if (value.equals(getParameters().get(key))) { // value != null
            return this;
        }
        getParameters().put(key, value);
        // todo myRPC  原版本是深拷贝 我这里直接返回本身  why？
        return this;
    }
}

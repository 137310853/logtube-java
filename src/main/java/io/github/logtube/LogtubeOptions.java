package io.github.logtube;

import io.github.logtube.utils.HttpIgnore;
import io.github.logtube.utils.Maps;
import io.github.logtube.utils.Strings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.*;

public class LogtubeOptions {

    private static final String CUSTOM_TOPICS_PREFIX = "logtube.topics.";

    public static @NotNull String getHostname() {
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        if (hostname == null) {
            hostname = "localhost";
        }
        return hostname;
    }

    private static @NotNull Set<String> quickStringSet(@NotNull String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    public static @NotNull LogtubeOptions fromClasspath() {
        Properties properties = propertiesFromFile("logtube.yml");

        if (properties == null) {
            properties = propertiesFromFile("logtube.yaml");
        }

        if (properties == null) {
            properties = propertiesFromFile("logtube.properties");
        }

        if (properties == null) {
            properties = new Properties();
            System.err.println("logtube failed to load config file, using default configs");
        }

        return new LogtubeOptions(properties);
    }

    public static @NotNull LogtubeOptions getDefault() {
        return new LogtubeOptions(new Properties());
    }

    @Nullable
    private static Properties propertiesFromFile(@NotNull String filename) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)) {

            Properties properties = new Properties();
            if (stream != null) {
                if (filename.toLowerCase().endsWith(".properties")) {
                    properties.load(stream);
                } else if (filename.toLowerCase().endsWith(".yml") || filename.toLowerCase().endsWith(".yaml")) {
                    Yaml yml = new Yaml();
                    Map<String, Object> map = yml.load(stream);
                    Maps.flattenProperties(properties, map);
                } else {
                    System.err.println("unsupported file " + filename + ".");
                    return null;
                }
                String configFile = Strings.evaluateEnvironmentVariables(properties.getProperty("logtube.config-file"));
                if (configFile != null) {
                    if (configFile.equalsIgnoreCase("APOLLO")) {
                        return propertiesFromApollo();
                    }
                    return propertiesFromFile(configFile);
                } else {
                    return properties;
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("failed to load " + filename + ".");
        }
        return null;
    }

    @Nullable
    private static Properties propertiesFromApollo() {
        return propertiesFromProvider("io.github.logtube.apollo.LogtubeApolloOptionsProvider");
    }

    @Nullable
    private static Properties propertiesFromProvider(String className) {
        try {
            Class<?> providerClass = Class.forName(className);
            Object provider = providerClass.newInstance();
            if (provider instanceof LogtubeOptionsProvider) {
                return ((LogtubeOptionsProvider) provider).loadOptions();
            }
            return null;
        } catch (Throwable e) {
            System.err.println("Failed to load options provider: " + className + ": " + e.getMessage());
            return null;
        }
    }

    @NotNull
    private final Properties properties;

    public LogtubeOptions(@NotNull Properties properties) {
        this.properties = properties;
    }

    @Nullable
    private String getProperty(@NotNull String field) {
        return Strings.evaluateEnvironmentVariables(this.properties.getProperty(field));
    }

    @Nullable
    @Contract("_, !null -> !null")
    private String sanitizedStringValue(@NotNull String field, @Nullable String defaultValue) {
        return Strings.sanitize(getProperty(field), defaultValue);
    }

    @Nullable
    @Contract("_, !null -> !null")
    private String stringValue(@NotNull String field, @Nullable String defaultValue) {
        String ret = getProperty(field);
        return ret == null ? defaultValue : ret;
    }

    private boolean booleanValue(String field, boolean defaultValue) {
        String value = getProperty(field);
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("no")) {
            return false;
        }
        return defaultValue;
    }

    private int intValue(String field, int defaultValue) {
        String value = getProperty(field);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        return Integer.valueOf(value.trim());
    }

    @Contract("_, !null -> !null")
    private @Nullable Set<String> setValue(String field, @Nullable Set<String> defaultValue) {
        String value = getProperty(field);
        if (value == null) {
            return defaultValue;
        }
        String[] components = value.split(",");
        if (components.length == 0) {
            return defaultValue;
        }
        HashSet<String> result = new HashSet<>();
        for (String component : components) {
            component = component.trim();
            if (component.length() == 0) {
                continue;
            }
            result.add(component);
        }
        if (result.isEmpty()) {
            return defaultValue;
        }
        return result;
    }

    @Contract("_, !null -> !null")
    private @Nullable Map<String, String> mapValue(String field, @Nullable Map<String, String> defaultValue) {
        String value = getProperty(field);
        if (value == null) {
            return defaultValue;
        }
        String[] components = value.split(",");
        if (components.length == 0) {
            return defaultValue;
        }
        HashMap<String, String> result = new HashMap<>();
        for (String component : components) {
            component = component.trim();
            if (component.length() == 0) {
                continue;
            }
            String[] kvs = component.split("=");
            if (kvs.length != 2) {
                continue;
            }
            String k = Strings.normalize(kvs[0]);
            String v = Strings.normalize(kvs[1]);
            if (k == null || v == null) {
                continue;
            }
            result.put(k, v);
        }
        if (result.isEmpty()) {
            return defaultValue;
        }
        return result;
    }

    @Contract("_, !null -> !null")
    private @Nullable String[] arrayValue(@NotNull String field, String[] defaultValue) {
        String value = getProperty(field);
        if (value == null) {
            return defaultValue;
        }
        String[] components = value.split(",");
        if (components.length == 0) {
            return defaultValue;
        }
        for (int i = 0; i < components.length; i++) {
            components[i] = components[i].trim();
        }
        return components;
    }

    @NotNull
    public String getProject() {
        return sanitizedStringValue("logtube.project", "unknown-project");
    }

    @NotNull
    public String getEnv() {
        return sanitizedStringValue("logtube.env", "unknown-env");
    }

    @NotNull
    public Set<String> getTopics() {
        return setValue("logtube.topics.root", quickStringSet("ALL", "-trace", "-debug"));
    }

    @NotNull
    public Map<String, Set<String>> getCustomTopics() {
        HashMap<String, Set<String>> result = new HashMap<>();
        this.properties.keySet().forEach(k -> {
            String key = k.toString();
            if (key.startsWith(CUSTOM_TOPICS_PREFIX)) {
                result.put(key.substring(CUSTOM_TOPICS_PREFIX.length()).toLowerCase(), setValue(key, new HashSet<>()));
            }
        });
        return result;
    }

    @NotNull
    public Map<String, String> getTopicMappings() {
        return mapValue("logtube.topic-mappings", new HashMap<>());
    }

    public boolean getConsoleEnabled() {
        return booleanValue("logtube.console.enabled", false);
    }

    @Nullable
    public Set<String> getConsoleTopics() {
        return setValue("logtube.console.topics", null);
    }

    @NotNull
    public String getRotationMode() {
        return stringValue("logtube.rotation.mode", "none");
    }

    public int getRotationKeep() {
        return intValue("logtube.rotation.keep", 0);
    }

    public boolean getFileEnabled() {
        return booleanValue("logtube.file.enabled", false);
    }

    @Nullable
    public Set<String> getFileTopics() {
        return setValue("logtube.file.topics", quickStringSet("ALL"));
    }

    @NotNull
    public String getFileDir() {
        return stringValue("logtube.file.dir", "logs");
    }

    @NotNull
    public Map<String, String> getFileSubdirMappings() {
        return mapValue("logtube.file.subdir-mappings", new HashMap<>());
    }

    @NotNull
    public String getFileSignal() {
        return stringValue("logtube.file.signal", "/tmp/logtube.reopen.txt");
    }

    public boolean getFilePlainEnabled() {
        return booleanValue("logtube.file-plain.enabled", false);
    }

    @Nullable
    public Set<String> getFilePlainTopics() {
        return setValue("logtube.file-plain.topics", quickStringSet("trace", "debug", "info", "warn", "error"));
    }

    @NotNull
    public String getFilePlainDir() {
        return stringValue("logtube.file-plain.dir", "logs");
    }

    @NotNull
    public Map<String, String> getFilePlainSubdirMappings() {
        return mapValue("logtube.file-plain.subdir-mappings", new HashMap<>());
    }

    @NotNull
    public String getFilePlainSignal() {
        return stringValue("logtube.file-plain.signal", "/tmp/logtube.reopen.txt");
    }

    public boolean getFileJSONEnabled() {
        return booleanValue("logtube.file-json.enabled", false);
    }

    @Nullable
    public Set<String> getFileJSONTopics() {
        return setValue("logtube.file-json.topics", quickStringSet("ALL", "-trace", "-debug", "-info", "-warn", "-error"));
    }

    @NotNull
    public String getFileJSONDir() {
        return stringValue("logtube.file-json.dir", "logs");
    }

    @NotNull
    public Map<String, String> getFileJSONSubdirMappings() {
        return mapValue("logtube.file-json.subdir-mappings", new HashMap<>());
    }

    @NotNull
    public String getFileJSONSignal() {
        return stringValue("logtube.file-json.signal", "/tmp/logtube.reopen.txt");
    }

    public boolean getRemoteEnabled() {
        return booleanValue("logtube.remote.enabled", false);
    }

    @Nullable
    public Set<String> getRemoteTopics() {
        return setValue("logtube.remote.topics", quickStringSet("ALL", "-trace", "-debug"));
    }

    @NotNull
    public String[] getRemoteHosts() {
        return arrayValue("logtube.remote.hosts", new String[]{"127.0.0.1:9921"});
    }

    public boolean getRedisEnabled() {
        return booleanValue("logtube.redis.enabled", false);
    }

    @NotNull
    public Set<String> getRedisTopics() {
        return setValue("logtube.redis.topics", quickStringSet("ALL", "-trace", "-debug"));
    }


    @NotNull
    public String[] getRedisHosts() {
        return arrayValue("logtube.redis.hosts", new String[]{"127.0.0.1"});
    }

    @NotNull
    public String getRedisKey() {
        return stringValue("logtube.redis.key", "logtube");
    }

    public HttpIgnore[] getHttpIgnores() {
        String[] raw = arrayValue("logtube.filter.http-ignores", new String[0]);
        ArrayList<HttpIgnore> ret = new ArrayList<>();
        for (String s : raw) {
            String[] split = s.split(" ");
            if (split.length != 2) {
                continue;
            }
            ret.add(new HttpIgnore(split[0].trim(), split[1].trim()));
        }
        ret.add(new HttpIgnore("HEAD", "/"));
        ret.add(new HttpIgnore("GET", "/check"));
        ret.add(new HttpIgnore("GET", "/favicon.ico"));
        ret.add(new HttpIgnore("GET", "/actuator/health"));
        return ret.toArray(new HttpIgnore[0]);
    }

    public int getRedisMinDuration() {
        return intValue("logtube.filter.redis-min-duration", 0);
    }

    public int getRedisMinResultSize() {
        return intValue("logtube.filter.redis-min-result-size", 0);
    }

}

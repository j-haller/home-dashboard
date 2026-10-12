package dev.haller.dashboard.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class SettingsRepository {

    private final JdbcClient jdbcClient;

    public SettingsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Map<String, String> getAll() {
        return jdbcClient.sql("SELECT key, value FROM user_settings")
            .query((rs, rowNum) -> Map.entry(rs.getString("key"), rs.getString("value")))
            .list()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Optional<String> get(String key) {
        return jdbcClient.sql("SELECT value FROM user_settings WHERE key = :key")
            .param("key", key)
            .query(String.class)
            .optional();
    }

    public void set(String key, String value) {
        jdbcClient.sql("INSERT INTO user_settings (key, value) VALUES (:key, :value) ON CONFLICT(key) DO UPDATE SET value = excluded.value")
            .param("key", key)
            .param("value", value)
            .update();
    }
}

package com.example.wmtippspiel.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistenz der "WM-Notify"-Abonnenten (für gezielte Tipp-Erinnerungen ohne Member-Intent). */
@Repository
public class NotifySubscriberRepository {

    private final JdbcClient jdbc;

    public NotifySubscriberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isSubscribed(String userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notify_subscribers WHERE user_id = :u")
                .param("u", userId)
                .query(Integer.class)
                .single() > 0;
    }

    public void subscribe(String userId, String username, java.time.Instant now) {
        jdbc.sql("""
                        INSERT INTO notify_subscribers (user_id, username, subscribed_at)
                        VALUES (:u, :name, :ts)
                        ON CONFLICT (user_id) DO UPDATE SET username = EXCLUDED.username
                        """)
                .param("u", userId)
                .param("name", username)
                .param("ts", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    public void unsubscribe(String userId) {
        jdbc.sql("DELETE FROM notify_subscribers WHERE user_id = :u").param("u", userId).update();
    }

    public List<String> findAllUserIds() {
        return jdbc.sql("SELECT user_id FROM notify_subscribers").query(String.class).list();
    }
}

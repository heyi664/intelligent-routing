package com.xinchan.voiceqa.memory;

import com.xinchan.voiceqa.conversation.ConversationState;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "app.memory", name = "enabled", havingValue = "true")
public class PgMemoryStore {
    private final MemoryProperties properties;

    public PgMemoryStore(MemoryProperties properties) {
        this.properties = properties;
        initializeSchema();
    }

    public Optional<ConversationState> findState(String conversationId) {
        String sql = "select conversation_id,user_id,current_agent,updated_at from conversation_state where conversation_id=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ConversationState(
                    rs.getString("conversation_id"),
                    rs.getString("user_id"),
                    RouteTarget.valueOf(rs.getString("current_agent")),
                    rs.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read conversation state", ex);
        }
    }

    public void saveState(ConversationState state) {
        String sql = """
            insert into conversation_state(conversation_id,user_id,current_agent,created_at,updated_at)
            values(?,?,?,?,?)
            on conflict(conversation_id) do update set
              user_id=excluded.user_id,
              current_agent=excluded.current_agent,
              updated_at=excluded.updated_at
            """;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.from(state.updatedAt());
            statement.setString(1, state.conversationId());
            statement.setString(2, state.userId());
            statement.setString(3, state.currentAgent().name());
            statement.setTimestamp(4, now);
            statement.setTimestamp(5, now);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save conversation state", ex);
        }
    }

    public ChatTurn saveTurn(ChatTurn turn) {
        String sql = """
            insert into chat_turn(conversation_id,user_id,user_message,assistant_message,target_agent,source,created_at)
            values(?,?,?,?,?,?,?)
            returning id
            """;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, turn.conversationId());
            statement.setString(2, turn.userId());
            statement.setString(3, turn.userMessage());
            statement.setString(4, turn.assistantMessage());
            statement.setString(5, turn.targetAgent().name());
            statement.setString(6, turn.source());
            statement.setTimestamp(7, Timestamp.from(turn.createdAt()));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new ChatTurn(rs.getLong(1), turn.conversationId(), turn.userId(), turn.userMessage(), turn.assistantMessage(), turn.targetAgent(), turn.source(), turn.createdAt());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save chat turn", ex);
        }
    }

    public List<ChatTurn> findRecentTurns(String conversationId, int limit) {
        String sql = """
            select id,conversation_id,user_id,user_message,assistant_message,target_agent,source,created_at
            from chat_turn
            where conversation_id=?
            order by created_at desc, id desc
            limit ?
            """;
        List<ChatTurn> turns = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            statement.setInt(2, Math.max(0, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    turns.add(new ChatTurn(
                        rs.getLong("id"),
                        rs.getString("conversation_id"),
                        rs.getString("user_id"),
                        rs.getString("user_message"),
                        rs.getString("assistant_message"),
                        RouteTarget.valueOf(rs.getString("target_agent")),
                        rs.getString("source"),
                        rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read chat turns", ex);
        }
        Collections.reverse(turns);
        return turns;
    }

    private void initializeSchema() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                create table if not exists conversation_state(
                  conversation_id varchar(128) primary key,
                  user_id varchar(128) not null,
                  current_agent varchar(64) not null,
                  created_at timestamptz not null,
                  updated_at timestamptz not null
                )
                """);
            statement.executeUpdate("""
                create table if not exists chat_turn(
                  id bigserial primary key,
                  conversation_id varchar(128) not null,
                  user_id varchar(128) not null,
                  user_message text not null,
                  assistant_message text not null,
                  target_agent varchar(64) not null,
                  source varchar(128) not null,
                  created_at timestamptz not null
                )
                """);
            statement.executeUpdate("create index if not exists idx_chat_turn_conversation_created on chat_turn(conversation_id, created_at, id)");
            statement.executeUpdate("""
                create table if not exists conversation_summary(
                  id bigserial primary key,
                  conversation_id varchar(128) not null,
                  user_id varchar(128) not null,
                  summary text not null,
                  covered_turn_id_until bigint not null,
                  created_at timestamptz not null
                )
                """);
            statement.executeUpdate("create index if not exists idx_conversation_summary_conversation on conversation_summary(conversation_id, created_at, id)");
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize memory schema", ex);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(properties.getJdbcUrl(), properties.getJdbcUsername(), properties.getJdbcPassword());
    }
}
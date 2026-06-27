package plugin.database;

import arc.util.Log;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static plugin.PVars.*;

public class Database {
    public static final HikariDataSource dataSource = createDataSource();

    private static HikariDataSource createDataSource() {
        try {
            Class.forName("org.postgresql.Driver");
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + db);
            config.setUsername(dbUser);
            if (!dbPassword.equals("empty") && !dbPassword.isEmpty())
                config.setPassword(dbPassword);

            config.setMaximumPoolSize(10);
            config.setMinimumIdle(3);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(5000);

            return new HikariDataSource(config);
        } catch (ClassNotFoundException err) {
            Log.err(err);
            return null;
        }
    }

    public static <T> Optional<T> executeQuery(String sql, StatementSetter<PreparedStatement> statementSetter, Serealizer<ResultSet, T> serealizer) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            statementSetter.accept(pstmt);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(serealizer.apply(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            // System.out.println(e);
            Log.err("SQL query failed @ @", sql, e);
            return Optional.empty();
        }
    }

    public static boolean executeUpdate(String sql, StatementSetter<PreparedStatement> statementSetter) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            statementSetter.accept(pstmt);
            int updated = pstmt.executeUpdate();
            return updated > 0;

        } catch (SQLException e) {
            // System.out.println(e);
            Log.err("SQL query failed @ @", sql, e);
            return false;
        }
    }

    public static <T> List<T> executeQueryList(String sql, StatementSetter<PreparedStatement> statementSetter, Serealizer<ResultSet, T> serealizer) {
        List<T> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            statementSetter.accept(pstmt);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(serealizer.apply(rs));
                }
            }
        } catch (SQLException e) {
            // System.out.println(e);
            Log.err("SQL query failed @ @", sql, e);
        }
        return results;
    }

    @FunctionalInterface
    public interface StatementSetter<T> {
        void accept(T t) throws SQLException;
    }

    @FunctionalInterface
    public interface Serealizer<T, R> {
        R apply(T t) throws SQLException;
    }
}

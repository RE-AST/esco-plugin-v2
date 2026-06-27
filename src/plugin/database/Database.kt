package plugin.database

import arc.util.Log
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import plugin.PVars.*
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

object Database {
    val dataSource: HikariDataSource? = createDataSource()

    private fun createDataSource(): HikariDataSource? {
        return try {
            Class.forName("org.postgresql.Driver")

            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:postgresql://$dbHost:$dbPort/$db"
                username = dbUser

                if (dbPassword != "empty" && dbPassword.isNotEmpty()) {
                    password = dbPassword
                }

                maximumPoolSize = 10
                minimumIdle = 3
                idleTimeout = 30000
                connectionTimeout = 5000
            }

            HikariDataSource(config)
        } catch (err: ClassNotFoundException) {
            Log.err(err)
            null
        }
    }

    @JvmStatic
    fun <T> executeQuery(
        sql: String,
        setter: StatementSetter<PreparedStatement>,
        mapper: (ResultSet) -> T
    ): T? {
        return try {
            dataSource!!.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    setter.accept(stmt)

                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            mapper(rs)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Log.err("SQL query failed @ @", sql, e)
            null
        }
    }

    @JvmStatic
    fun executeUpdate(
        sql: String,
        statementSetter: StatementSetter<PreparedStatement>
    ): Boolean {
        return try {
            dataSource!!.connection.use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    statementSetter.accept(pstmt)
                    val updated = pstmt.executeUpdate()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            Log.err("SQL query failed @ @", sql, e)
            false
        }
    }

    @JvmStatic
    fun <T> executeQueryList(
        sql: String,
        statementSetter: StatementSetter<PreparedStatement>,
        serializer: Serializer<ResultSet, T>
    ): List<T> {
        val results = mutableListOf<T>()

        try {
            dataSource!!.connection.use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    statementSetter.accept(pstmt)

                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            results.add(serializer.apply(rs))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Log.err("SQL query failed @ @", sql, e)
        }

        return results
    }

    fun interface StatementSetter<T> {
        @Throws(SQLException::class)
        fun accept(t: T)
    }

    fun interface Serializer<T, R> {
        @Throws(SQLException::class)
        fun apply(t: T): R
    }
}
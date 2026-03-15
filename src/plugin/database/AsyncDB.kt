package plugin.database

import arc.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import plugin.database.Database.Serealizer
import plugin.database.Database.StatementSetter
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

val dbScope = CoroutineScope(Dispatchers.Default)

suspend fun <T> query(
    sql: String,
    setter: (PreparedStatement) -> Unit,
    serializer: (ResultSet) -> T
): Optional<T> = withContext(Dispatchers.IO) {
    Database.executeQueryAsync(sql, { setter(it) }, { serializer(it) })
}

suspend fun update(
    sql: String,
    setter: (PreparedStatement) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    Database.executeUpdate(sql) { setter(it) }
}

suspend fun <T> queryList(
    sql: String,
    setter: (PreparedStatement) -> Unit,
    serializer: (ResultSet) -> T
): MutableList<T> = withContext(Dispatchers.IO) {
    Database.executeQueryList(sql, { setter(it) }, { serializer(it) })
}
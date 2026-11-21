package com.fl.pos

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

const val OPEN = 0   // 未结账
const val PAID = 1   // 已结账

@Entity(tableName = "dish")
data class Dish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceCents: Int,
    val category: String = "热菜"
)

@Entity(tableName = "ticket")
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableNo: String = "",
    val status: Int = OPEN,
    val openedAt: Long = System.currentTimeMillis(),
    val paidAt: Long = 0L,
    val totalCents: Int = 0,
    val payMethod: String = ""
)

@Entity(tableName = "ticket_item")
data class TicketItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: Long,
    val dishName: String,
    val priceCents: Int,
    val qty: Int
)

data class DishRank(val dishName: String, val qty: Int, val amount: Int)
data class PaySum(val payMethod: String, val amount: Int, val cnt: Int)

@Dao
interface PosDao {

    /* ---------- 菜品 ---------- */
    @Query("SELECT * FROM dish ORDER BY category, name")
    fun dishes(): Flow<List<Dish>>

    @Insert suspend fun addDish(d: Dish)
    @Delete suspend fun delDish(d: Dish)

    /* ---------- 客单 ---------- */
    // 返回 List 而不是 Flow<Ticket?>：Room 对可空 Flow 的支持不稳，取列表再 firstOrNull() 最保险
    @Query("SELECT * FROM ticket WHERE status = 0 ORDER BY openedAt DESC LIMIT 1")
    fun openTicket(): Flow<List<Ticket>>

    @Insert suspend fun newTicket(t: Ticket): Long

    @Update suspend fun updateTicket(t: Ticket)

    @Query("SELECT * FROM ticket WHERE status = 1 AND paidAt BETWEEN :from AND :to ORDER BY paidAt DESC")
    fun history(from: Long, to: Long): Flow<List<Ticket>>

    /* ---------- 明细 ---------- */
    @Query("SELECT * FROM ticket_item WHERE ticketId = :id ORDER BY id")
    fun items(id: Long): Flow<List<TicketItem>>

    @Query("SELECT * FROM ticket_item WHERE ticketId = :id ORDER BY id")
    suspend fun itemsOnce(id: Long): List<TicketItem>

    @Query("SELECT * FROM ticket_item WHERE ticketId = :id AND dishName = :name LIMIT 1")
    suspend fun findItem(id: Long, name: String): TicketItem?

    @Insert suspend fun insertItem(i: TicketItem)
    @Update suspend fun updateItem(i: TicketItem)
    @Delete suspend fun deleteItem(i: TicketItem)

    /* ---------- 统计 ---------- */
    @Query("SELECT COALESCE(SUM(totalCents),0) FROM ticket WHERE status = 1 AND paidAt BETWEEN :from AND :to")
    fun income(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM ticket WHERE status = 1 AND paidAt BETWEEN :from AND :to")
    fun ticketCount(from: Long, to: Long): Flow<Int>

    @Query(
        """SELECT dishName, SUM(qty) AS qty, SUM(qty * priceCents) AS amount
           FROM ticket_item WHERE ticketId IN
           (SELECT id FROM ticket WHERE status = 1 AND paidAt BETWEEN :from AND :to)
           GROUP BY dishName ORDER BY amount DESC"""
    )
    fun rank(from: Long, to: Long): Flow<List<DishRank>>

    @Query(
        """SELECT payMethod, SUM(totalCents) AS amount, COUNT(*) AS cnt
           FROM ticket WHERE status = 1 AND paidAt BETWEEN :from AND :to
           GROUP BY payMethod ORDER BY amount DESC"""
    )
    fun byPay(from: Long, to: Long): Flow<List<PaySum>>
}

@Database(entities = [Dish::class, Ticket::class, TicketItem::class], version = 1, exportSchema = false)
abstract class PosDb : RoomDatabase() {
    abstract fun dao(): PosDao
    companion object {
        @Volatile private var inst: PosDb? = null
        fun get(ctx: Context): PosDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, PosDb::class.java, "pos.db")
                .build().also { inst = it }
        }
    }
}

class PosRepo(private val dao: PosDao) {

    fun dishes() = dao.dishes()
    fun openTicket() = dao.openTicket()
    fun items(id: Long) = dao.items(id)
    suspend fun itemsOf(id: Long) = dao.itemsOnce(id)
    fun history(from: Long, to: Long) = dao.history(from, to)
    fun income(from: Long, to: Long) = dao.income(from, to)
    fun ticketCount(from: Long, to: Long) = dao.ticketCount(from, to)
    fun rank(from: Long, to: Long) = dao.rank(from, to)
    fun byPay(from: Long, to: Long) = dao.byPay(from, to)

    suspend fun createDish(name: String, yuan: Double, cat: String) =
        dao.addDish(Dish(name = name.trim(), priceCents = (yuan * 100).toInt(), category = cat))

    suspend fun deleteDish(d: Dish) = dao.delDish(d)

    suspend fun openNew(tableNo: String): Long = dao.newTicket(Ticket(tableNo = tableNo.trim()))

    /** 点一道菜：同名菜合并数量 */
    suspend fun tap(dish: Dish, ticketId: Long) {
        val ex = dao.findItem(ticketId, dish.name)
        if (ex == null) {
            dao.insertItem(
                TicketItem(ticketId = ticketId, dishName = dish.name, priceCents = dish.priceCents, qty = 1)
            )
        } else {
            dao.updateItem(ex.copy(qty = ex.qty + 1))
        }
    }

    suspend fun changeQty(item: TicketItem, delta: Int) {
        val q = item.qty + delta
        if (q <= 0) dao.deleteItem(item) else dao.updateItem(item.copy(qty = q))
    }

    suspend fun remove(item: TicketItem) = dao.deleteItem(item)

    /** 结账：标记已结 + 存合计/支付方式/时间，日后统计全靠这条 */
    suspend fun settle(ticket: Ticket, items: List<TicketItem>, payMethod: String): Int {
        val total = items.sumOf { it.priceCents * it.qty }
        dao.updateTicket(
            ticket.copy(
                status = PAID,
                totalCents = total,
                payMethod = payMethod,
                paidAt = System.currentTimeMillis()
            )
        )
        return total
    }

    /** 作废：不计入收入（合计置 0） */
    suspend fun voidTicket(ticket: Ticket) =
        dao.updateTicket(ticket.copy(status = PAID, totalCents = 0, payMethod = "作废",
            paidAt = System.currentTimeMillis()))
}

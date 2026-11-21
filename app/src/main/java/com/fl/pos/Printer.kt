package com.fl.pos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.text.SimpleDateFormat
import java.util.*

/** ESC/POS 指令封装，58mm 机型一行 32 字符 */
object Esc {
    val INIT = byteArrayOf(0x1B, 0x40)
    fun text(s: String) = s.toByteArray(charset("GBK"))
    fun align(n: Int) = byteArrayOf(0x1B, 0x61, n.toByte())
    fun bold(on: Boolean) = byteArrayOf(0x1B, 0x45, if (on) 1 else 0)
    fun size(w: Int, h: Int) = byteArrayOf(0x1D, 0x21, ((w shl 4) or h).toByte())
    fun feed(n: Int) = byteArrayOf(0x1B, 0x64, n.toByte())
    fun cut() = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    fun line(n: Int = 32) = text("-".repeat(n) + "\n")

    /** 显示宽度：中文算 2 个字符位 */
    fun dw(s: String) = s.sumOf { if (it.code > 127) 2 else 1 }

    fun padRight(s: String, w: Int) = s + " ".repeat((w - dw(s)).coerceAtLeast(0))
    fun padLeft(s: String, w: Int) = " ".repeat((w - dw(s)).coerceAtLeast(0)) + s

    fun truncate(s: String, w: Int): String {
        var r = s
        while (dw(r) > w && r.isNotEmpty()) r = r.dropLast(1)
        return r
    }

    fun padTo(s: String, w: Int) = padRight(truncate(s, w), w)

    /** 左名 + 数量 + 右金额 */
    fun row(name: String, qty: String, amount: String, width: Int = 32): String {
        val q = 5
        val a = 9
        return padTo(name, width - q - a) + padRight(qty, q) + padLeft(amount, a) + "\n"
    }

    fun lr(left: String, right: String, width: Int = 32) =
        padTo(left, width - dw(right)) + right + "\n"
}

@SuppressLint("MissingPermission")
class BtPrinter {
    private var socket: BluetoothSocket? = null
    private fun m(c: Int) = String.format("%.2f", c / 100.0)

    fun paired(): List<BluetoothDevice> =
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()

    fun connect(dev: BluetoothDevice): Boolean = try {
        socket?.close()
        socket = dev.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        ).also { it.connect() }
        true
    } catch (e: Exception) {
        socket = null
        false
    }

    fun isConnected() = socket?.isConnected == true

    fun write(vararg chunks: ByteArray): Boolean = try {
        val out = socket?.outputStream ?: return false
        chunks.forEach { out.write(it) }
        out.flush()
        true
    } catch (e: Exception) {
        false
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    /** 客单小票：逐道菜 + 合计 + 支付方式 */
    fun printTicket(t: Ticket, items: List<TicketItem>, shopName: String = "本店"): Boolean {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val sb = StringBuilder()
        sb.append(Esc.align(1)).append(Esc.size(0, 1)).append(shopName)
        sb.append(Esc.size(0, 0)).append("\n").append(Esc.align(0))
        sb.append(Esc.line())
        sb.append("单号 ").append(t.id)
        if (t.tableNo.isNotBlank()) sb.append("    桌 ").append(t.tableNo)
        sb.append("\n").append(df.format(Date(System.currentTimeMillis()))).append("\n")
        sb.append(Esc.line())
        sb.append(Esc.row("菜名", "数量", "金额"))
        sb.append(Esc.line())
        items.forEach {
            sb.append(Esc.row(it.dishName, "x${it.qty}", m(it.priceCents * it.qty)))
        }
        sb.append(Esc.line())
        val total = items.sumOf { it.priceCents * it.qty }
        sb.append(Esc.bold(true))
        sb.append(Esc.lr("合计", m(total), 32))
        sb.append(Esc.bold(false))
        sb.append(Esc.lr("支付", t.payMethod.ifBlank { "现金" }))
        sb.append(Esc.line())
        sb.append(Esc.align(1)).append("谢谢惠顾").append("\n").append(Esc.align(0))
        return write(Esc.INIT, Esc.text(sb.toString()), Esc.feed(3), Esc.cut())
    }

    /** 日汇总：收入、单数、收款构成、菜品销量 */
    fun printDaily(
        dateTxt: String,
        income: Int,
        count: Int,
        pays: List<PaySum>,
        rank: List<DishRank>
    ): Boolean {
        val sb = StringBuilder()
        sb.append(Esc.align(1)).append(Esc.size(0, 1)).append("营业日报")
        sb.append(Esc.size(0, 0)).append("\n").append(dateTxt).append("\n").append(Esc.align(0))
        sb.append(Esc.line())
        sb.append(Esc.bold(true)).append(Esc.lr("收入合计", m(income))).append(Esc.bold(false))
        sb.append(Esc.lr("客单数", "$count"))
        sb.append(Esc.lr("客单价", if (count > 0) m(income / count) else "0.00"))
        sb.append(Esc.line()).append("收款方式\n")
        pays.forEach { sb.append(Esc.lr("  ${it.payMethod}", "${m(it.amount)} / ${it.cnt}单")) }
        sb.append(Esc.line()).append("菜品销量\n")
        sb.append(Esc.row("菜名", "份数", "金额"))
        rank.take(20).forEach { sb.append(Esc.row(it.dishName, "${it.qty}", m(it.amount))) }
        sb.append(Esc.line())
        sb.append(Esc.align(1))
            .append(SimpleDateFormat("HH:mm 打印", Locale.CHINA).format(Date()))
            .append("\n").append(Esc.align(0))
        return write(Esc.INIT, Esc.text(sb.toString()), Esc.feed(4), Esc.cut())
    }
}

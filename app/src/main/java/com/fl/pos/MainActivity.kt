package com.fl.pos

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    /** Android 12+ 枚举/连接已配对蓝牙设备必须运行时授权，缺了会抛 SecurityException */
    private val btPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            btPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        val repo = PosRepo(PosDb.get(this).dao())
        val printer = BtPrinter()

        setContent {
            MaterialTheme {
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(bottomBar = {
                    NavigationBar {
                        NavigationBarItem(tab == 0, { tab = 0 }, {}, label = { Text("点单") })
                        NavigationBarItem(tab == 1, { tab = 1 }, {}, label = { Text("日报") })
                        NavigationBarItem(tab == 2, { tab = 2 }, {}, label = { Text("菜品") })
                    }
                }) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> OrderScreen(repo, printer)
                            1 -> DailyScreen(repo, printer)
                            else -> DishScreen(repo)
                        }
                    }
                }
            }
        }
    }
}

fun dayStart(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/* ==================== 点单页 ==================== */
@Composable
fun OrderScreen(repo: PosRepo, printer: BtPrinter) {
    val scope = rememberCoroutineScope()
    val openTickets by repo.openTicket()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val ticket: Ticket? = openTickets.firstOrNull()
    val dishes by repo.dishes().collectAsStateWithLifecycle(initialValue = emptyList())

    val itemFlow = remember(ticket?.id) {
        val id = ticket?.id
        if (id == null) flowOf(emptyList<TicketItem>()) else repo.items(id)
    }
    val items by itemFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var msg by remember { mutableStateOf("") }
    var askTable by remember { mutableStateOf(false) }
    var askPay by remember { mutableStateOf(false) }
    var tableInput by remember { mutableStateOf("") }
    val total = items.fold(0) { acc, i -> acc + i.priceCents * i.qty }

    Column(Modifier.fillMaxSize().padding(10.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ticket == null) "未开台" else
                    "单号 ${ticket!!.id}" +
                        (if (ticket!!.tableNo.isNotBlank()) " · 桌 ${ticket!!.tableNo}" else ""),
                Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
            if (ticket == null) {
                Button(onClick = { askTable = true }) { Text("开台") }
            } else {
                TextButton(onClick = {
                    scope.launch { repo.voidTicket(ticket!!); msg = "已作废本单" }
                }) { Text("作废") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        Text("点菜（点一下加 1 份）", style = MaterialTheme.typography.bodySmall)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(if (items.isEmpty()) 190.dp else 140.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            gridItems(dishes, key = { it.id }) { d ->
                Card(Modifier.clickable(enabled = ticket != null) {
                    scope.launch {
                        val tid = ticket?.id ?: return@launch
                        repo.tap(d, tid)
                    }
                }) {
                    Column(Modifier.padding(8.dp)) {
                        Text(d.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Text(
                            "%.2f".format(d.priceCents / 100.0),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("本单明细", fontWeight = FontWeight.Medium)

        LazyColumn(Modifier.weight(1f)) {
            items(items, key = { it.id }) { line ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.dishName, fontWeight = FontWeight.Medium)
                        Text(
                            "%.2f x ${line.qty} = %.2f".format(
                                line.priceCents / 100.0,
                                line.priceCents * line.qty / 100.0
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { scope.launch { repo.changeQty(line, -1) } }) { Text("-") }
                    TextButton(onClick = { scope.launch { repo.changeQty(line, 1) } }) { Text("+") }
                    TextButton(onClick = { scope.launch { repo.remove(line) } }) { Text("删") }
                }
                HorizontalDivider()
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("合计", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                "%.2f 元".format(total / 100.0),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = ticket != null && items.isNotEmpty(),
                onClick = { askPay = true },
                modifier = Modifier.height(52.dp)
            ) { Text("结账打票") }
        }

        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
        PrinterBar(printer) { msg = it }
    }

    /* ---- 开台 ---- */
    if (askTable) {
        AlertDialog(
            onDismissRequest = { askTable = false },
            title = { Text("开台") },
            text = {
                OutlinedTextField(
                    tableInput, { tableInput = it },
                    label = { Text("桌号 / 客人标识（可空）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.openNew(tableInput)
                        tableInput = ""
                        askTable = false
                        msg = "已开台"
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { askTable = false }) { Text("取消") } }
        )
    }

    /* ---- 结账：选支付方式 → 落账 → 打票 ---- */
    if (askPay && ticket != null) {
        AlertDialog(
            onDismissRequest = { askPay = false },
            title = { Text("结账 %.2f 元".format(total / 100.0)) },
            text = {
                Column {
                    listOf("现金", "微信", "支付宝", "银行卡").forEach { p ->
                        Text(p, Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                val t = ticket ?: return@launch
                                val snapshot = items
                                repo.settle(t, snapshot, p)
                                val paid = t.copy(
                                    status = PAID, totalCents = total, payMethod = p
                                )
                                askPay = false
                                msg = when {
                                    !printer.isConnected() -> "已结账，打印机未连接"
                                    printer.printTicket(paid, snapshot) -> "已结账并打票"
                                    else -> "已结账，打印失败"
                                }
                            }
                        }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { askPay = false }) { Text("取消") } }
        )
    }
}

/* ==================== 打印机选择条 ==================== */
@Composable
fun PrinterBar(printer: BtPrinter, onMsg: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (printer.isConnected()) "打印机：已连接" else "打印机：未连接",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = { show = true }) { Text("选设备") }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("已配对的蓝牙设备") },
            text = {
                Column {
                    if (printer.paired().isEmpty()) Text("先在系统蓝牙设置里配对小票机。")
                    printer.paired().forEach { d ->
                        Text(
                            d.name ?: d.address,
                            Modifier.fillMaxWidth().clickable {
                                onMsg(if (printer.connect(d)) "已连接 ${d.name}" else "连接失败")
                                tick++
                                show = false
                            }.padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { show = false }) { Text("关闭") } }
        )
    }
}

/* ==================== 日报页 ==================== */
@Composable
fun DailyScreen(repo: PosRepo, printer: BtPrinter) {
    val scope = rememberCoroutineScope()
    val from = remember { dayStart() }
    val income by repo.income(from, Long.MAX_VALUE).collectAsStateWithLifecycle(initialValue = 0)
    val count by repo.ticketCount(from, Long.MAX_VALUE).collectAsStateWithLifecycle(initialValue = 0)
    val rank by repo.rank(from, Long.MAX_VALUE).collectAsStateWithLifecycle(initialValue = emptyList())
    val pays by repo.byPay(from, Long.MAX_VALUE).collectAsStateWithLifecycle(initialValue = emptyList())
    val history by repo.history(from, Long.MAX_VALUE).collectAsStateWithLifecycle(initialValue = emptyList())
    var msg by remember { mutableStateOf("") }
    val df = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "今日收入  %.2f 元".format(income / 100.0),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "客单 $count 笔" +
                        if (count > 0) "   客单价 %.2f".format(income / 100.0 / count) else ""
                )
                if (pays.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    pays.forEach {
                        Text(
                            "${it.payMethod}  %.2f 元 / ${it.cnt} 单".format(it.amount / 100.0),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Text("菜品销量", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Medium)
        LazyColumn(Modifier.weight(1f)) {
            items(rank, key = { it.dishName }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(it.dishName, Modifier.weight(1f))
                    Text("${it.qty} 份")
                    Spacer(Modifier.width(12.dp))
                    Text("%.2f".format(it.amount / 100.0))
                }
                HorizontalDivider()
            }
        }

        Text("今日已结单（点一行重打小票）", Modifier.padding(top = 6.dp),
            fontWeight = FontWeight.Medium)
        LazyColumn(Modifier.height(110.dp)) {
            items(history, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            val its = repo.itemsOf(t.id)
                            msg = if (printer.printTicket(t, its)) "已重打 #${t.id}" else "打印失败"
                        }
                    }.padding(vertical = 3.dp)
                ) {
                    Text("#${t.id}", Modifier.width(52.dp))
                    Text(if (t.tableNo.isNotBlank()) "桌${t.tableNo}" else "—", Modifier.width(58.dp))
                    Text(df.format(Date(t.paidAt)), Modifier.weight(1f))
                    Text("%.2f".format(t.totalCents / 100.0))
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val ok = printer.printDaily(
                    dateTxt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()),
                    income = income,
                    count = count,
                    pays = pays,
                    rank = rank
                )
                msg = if (ok) "日报已打印" else "打印失败，检查蓝牙"
            }
        ) { Text("打印今日汇总") }

        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
        PrinterBar(printer) { msg = it }
    }
}

/* ==================== 菜品页 ==================== */
@Composable
fun DishScreen(repo: PosRepo) {
    val scope = rememberCoroutineScope()
    val dishes by repo.dishes().collectAsStateWithLifecycle(initialValue = emptyList())
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("热菜") }
    var msg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("菜名") },
                modifier = Modifier.weight(1.5f), singleLine = true)
            OutlinedTextField(price, { price = it }, label = { Text("单价") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(cat, { cat = it }, label = { Text("分类") },
                modifier = Modifier.weight(1f), singleLine = true)
        }
        Button(
            onClick = {
                val p = price.toDoubleOrNull()
                if (name.isBlank() || p == null) {
                    msg = "菜名或价格不对"
                    return@Button
                }
                scope.launch {
                    repo.createDish(name, p, cat.ifBlank { "热菜" })
                    name = ""
                    price = ""
                    msg = "已添加"
                }
            },
            modifier = Modifier.padding(top = 6.dp)
        ) { Text("添加菜品") }

        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)

        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(dishes, key = { it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Medium)
                        Text(
                            "%.2f 元 · ${d.category}".format(d.priceCents / 100.0),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { scope.launch { repo.deleteDish(d) } }) { Text("删除") }
                }
                HorizontalDivider()
            }
        }
    }
}

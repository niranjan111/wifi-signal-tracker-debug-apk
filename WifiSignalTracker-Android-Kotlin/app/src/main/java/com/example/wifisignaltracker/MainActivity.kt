package com.example.wifisignaltracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val Bg = androidx.compose.ui.graphics.Color(0xFF080D18)
private val Panel = androidx.compose.ui.graphics.Color(0xFF111A2B)
private val Panel2 = androidx.compose.ui.graphics.Color(0xFF172238)
private val Mint = androidx.compose.ui.graphics.Color(0xFF56F0B4)
private val Blue = androidx.compose.ui.graphics.Color(0xFF6DA8FF)
private val TextMain = androidx.compose.ui.graphics.Color(0xFFF3F6FC)
private val TextMuted = androidx.compose.ui.graphics.Color(0xFF91A0B8)
private val Amber = androidx.compose.ui.graphics.Color(0xFFFFC56B)
private val Red = androidx.compose.ui.graphics.Color(0xFFFF7185)

data class Sample(val time: Long, val rssi: Int, val ssid: String, val frequency: Int, val linkSpeed: Int)
data class Report(val id: Long, val started: Long, val ended: Long, val count: Int, val average: Int, val minimum: Int, val maximum: Int, val ssid: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WifiTrackerApp() }
    }
}

@Composable
private fun WifiTrackerApp() {
    val context = LocalContext.current
    val wifi = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    var permissionGranted by remember { mutableStateOf(hasWifiPermission(context)) }
    var tracking by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Sample?>(null) }
    val samples = remember { mutableStateListOf<Sample>() }
    val reports = remember { mutableStateListOf<Report>().apply { addAll(loadReports(context)) } }
    var selectedTab by remember { mutableStateOf(0) }
    var sessionStart by remember { mutableLongStateOf(0L) }
    var lastSaved by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result.values.all { it } || hasWifiPermission(context)
        if (!permissionGranted) Toast.makeText(context, "Allow Wi-Fi/location permission to read signal details.", Toast.LENGTH_LONG).show()
    }

    fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        permissionLauncher.launch(permissions.toTypedArray())
    }

    fun readSample(): Sample {
        @Suppress("DEPRECATION")
        val info = wifi.connectionInfo
        val rssi = if (info != null && info.networkId != -1) info.rssi else -100
        val ssid = if (info?.ssid.isNullOrBlank() || info?.ssid == "<unknown ssid>") "Network unavailable" else info!!.ssid.trim('"')
        return Sample(System.currentTimeMillis(), rssi.coerceIn(-100, -30), ssid, info?.frequency ?: 0, info?.linkSpeed ?: 0)
    }

    LaunchedEffect(tracking, permissionGranted) {
        while (tracking && permissionGranted) {
            val sample = readSample()
            current = sample
            samples.add(sample)
            if (samples.size > 120) samples.removeAt(0)
            delay(2000)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint, secondary = Blue, background = Bg, surface = Panel,
            onPrimary = Bg, onBackground = TextMain, onSurface = TextMain
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Panel2, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Text("⌁", color = Mint, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("SIGNAL / SCOPE", color = TextMuted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Text("Wi-Fi Tracker", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.background(if (tracking) Mint.copy(alpha = .14f) else Panel2, CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)) {
                        Text(if (tracking) "● LIVE" else "○ IDLE", color = if (tracking) Mint else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(5.dp)) {
                    listOf("Overview", "Reports").forEachIndexed { index, label ->
                        Box(Modifier.weight(1f).background(if (selectedTab == index) Panel2 else Panel, RoundedCornerShape(12.dp))) {
                            TextButton(onClick = { selectedTab = index }, modifier = Modifier.fillMaxWidth()) {
                                Text(label, color = if (selectedTab == index) Mint else TextMuted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (selectedTab == 0) {
                    val sample = current
                    val signal = sample?.rssi ?: -100
                    val quality = qualityLabel(signal)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1.15f).background(Panel, RoundedCornerShape(22.dp)).padding(17.dp)) {
                            Text("SIGNAL STRENGTH", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(if (sample == null) "--" else "$signal", color = TextMain, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(5.dp))
                                Text(if (sample == null) "dBm" else "dBm", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(quality, color = qualityColor(signal), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            SignalBar(signal)
                        }
                        Column(Modifier.weight(.85f).background(Panel, RoundedCornerShape(22.dp)).padding(17.dp)) {
                            Text("QUALITY", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.size(76.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawArc(Panel2, -90f, 360f, false, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
                                    drawArc(qualityColor(signal), -90f, ((signal + 100).coerceIn(0, 70) / 70f) * 360f, false, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
                                }
                                Text(if (sample == null) "—" else "${((signal + 100).coerceIn(0, 70) * 100 / 70)}%", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(9.dp))
                            Text(if (sample == null) "Waiting for data" else "Signal score", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("SIGNAL HISTORY", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Updates every 2 seconds while tracking", color = TextMuted, fontSize = 11.sp)
                            }
                            Text("${samples.size} pts", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        SignalChart(samples.map { it.rssi })
                        Spacer(Modifier.height(7.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("WEAK  −100 dBm", color = TextMuted, fontSize = 10.sp)
                            Text("STRONG  −30 dBm", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(17.dp)) {
                        Text("CONNECTED NETWORK", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(9.dp))
                        Text(sample?.ssid ?: "Start tracking to read Wi-Fi details", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("FREQUENCY", sample?.let { if (it.frequency > 0) "${it.frequency} MHz" else "—" } ?: "—")
                            Metric("LINK SPEED", sample?.let { if (it.linkSpeed > 0) "${it.linkSpeed} Mbps" else "—" } ?: "—")
                            Metric("SAMPLES", samples.size.toString())
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (!permissionGranted) {
                        Button(onClick = { requestPermissions() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Text("Grant Wi-Fi permissions")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!tracking) {
                                    samples.clear()
                                    sessionStart = System.currentTimeMillis()
                                    tracking = true
                                } else {
                                    tracking = false
                                    if (samples.isNotEmpty()) {
                                        val values = samples.map { it.rssi }
                                        val report = Report(
                                            id = System.currentTimeMillis(), started = sessionStart, ended = System.currentTimeMillis(),
                                            count = values.size, average = values.average().toInt(), minimum = values.minOrNull() ?: -100,
                                            maximum = values.maxOrNull() ?: -30, ssid = samples.last().ssid
                                        )
                                        reports.add(0, report)
                                        saveReports(context, reports)
                                        lastSaved = report.id
                                        Toast.makeText(context, "Session saved to Reports", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (tracking) Red else Mint, contentColor = Bg)
                        ) {
                            Text(if (tracking) "■  Stop & Save Session" else "▶  Start Live Tracking", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Saved reports", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Stored locally on this device", color = TextMuted, fontSize = 12.sp)
                        }
                        Text("${reports.size} sessions", color = Mint, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (reports.isEmpty()) {
                        Box(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No reports yet", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Start tracking, then stop to save a session.", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                            items(reports, key = { it.id }) { report ->
                                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(report.ssid, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(formatDate(report.started), color = TextMuted, fontSize = 11.sp)
                                        }
                                        Text("${report.count} samples", color = Mint, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.height(13.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Metric("AVERAGE", "${report.average} dBm")
                                        Metric("MINIMUM", "${report.minimum} dBm")
                                        Metric("MAXIMUM", "${report.maximum} dBm")
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text("Duration: ${durationText(report.ended - report.started)}", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalBar(rssi: Int) {
    val pct = ((rssi + 100).coerceIn(0, 70) / 70f)
    Box(Modifier.fillMaxWidth().height(7.dp).background(Panel2, CircleShape)) {
        Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(qualityColor(rssi), CircleShape))
    }
}

@Composable
private fun SignalChart(values: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
        val left = 3.dp.toPx()
        val right = size.width - 3.dp.toPx()
        val top = 4.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            drawLine(Panel2, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = left + (right - left) * index / (values.size - 1).toFloat()
            val normalized = ((value + 100).coerceIn(0, 70) / 70f)
            val y = bottom - normalized * (bottom - top)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = .7.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun qualityLabel(rssi: Int): String = when {
    rssi >= -55 -> "Excellent"
    rssi >= -67 -> "Very good"
    rssi >= -75 -> "Fair"
    rssi > -85 -> "Weak"
    else -> "Very weak"
}
private fun qualityColor(rssi: Int) = when {
    rssi >= -67 -> Mint
    rssi >= -75 -> Amber
    else -> Red
}
private fun formatDate(time: Long) = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(time))
private fun durationText(ms: Long): String {
    val sec = max(0L, ms / 1000)
    return if (sec < 60) "${sec}s" else "${sec / 60}m ${sec % 60}s"
}
private fun hasWifiPermission(context: Context): Boolean {
    val location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val nearby = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    return location && nearby
}
private fun loadReports(context: Context): List<Report> {
    return try {
        val raw = context.getSharedPreferences("signal_tracker", Context.MODE_PRIVATE).getString("reports", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Report(o.getLong("id"), o.getLong("started"), o.getLong("ended"), o.getInt("count"),
                o.getInt("average"), o.getInt("minimum"), o.getInt("maximum"), o.getString("ssid"))
        }
    } catch (_: Exception) { emptyList() }
}
private fun saveReports(context: Context, reports: List<Report>) {
    val array = JSONArray()
    reports.take(100).forEach { r ->
        array.put(JSONObject().put("id", r.id).put("started", r.started).put("ended", r.ended)
            .put("count", r.count).put("average", r.average).put("minimum", r.minimum)
            .put("maximum", r.maximum).put("ssid", r.ssid))
    }
    context.getSharedPreferences("signal_tracker", Context.MODE_PRIVATE).edit().putString("reports", array.toString()).apply()
}

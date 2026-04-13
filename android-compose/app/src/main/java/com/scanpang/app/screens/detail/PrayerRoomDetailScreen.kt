package com.scanpang.app.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.data.HalalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerRoomDetailScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    halalVm: HalalViewModel = viewModel(),
) {
    val rooms by halalVm.prayerRooms.collectAsState()
    LaunchedEffect(Unit) { if (rooms.isEmpty()) halalVm.loadPrayerRooms() }
    val room = rooms.firstOrNull { it.name == title }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { inner ->
        if (room == null) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 이용 상태
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (room.availability_status == "open") Color(0xFFE8F5E9) else Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(if (room.availability_status == "open") Icons.Default.CheckCircle else Icons.Default.Info, null,
                        tint = if (room.availability_status == "open") Color(0xFF2E7D32) else Color(0xFFE65100))
                    Text(if (room.availability_status == "open") "현재 이용 가능" else "이용 가능 여부 확인 필요",
                        fontWeight = FontWeight.SemiBold,
                        color = if (room.availability_status == "open") Color(0xFF2E7D32) else Color(0xFFE65100))
                }
                // 시설
                Text("시설 정보", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("wudu" to "우두시설", "gender_separation" to "남녀분리", "prayer_mat" to "기도매트", "quran_available" to "꾸란").forEach { (key, label) ->
                        val on = room.facilities[key] == true
                        Box(Modifier.background(if (on) Color(0xFFE3F2FD) else Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(label, fontSize = 12.sp, color = if (on) Color(0xFF1565C0) else Color.Gray, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Divider(color = Color(0xFFEEEEEE))
                Text("상세 정보", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (room.floor.isNotEmpty()) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Layers, null, Modifier.size(20.dp), tint = Color.Gray); Column { Text("층", fontSize = 12.sp, color = Color.Gray); Text(room.floor) } } }
                if (room.open_hours.isNotEmpty()) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Schedule, null, Modifier.size(20.dp), tint = Color.Gray); Column { Text("운영시간", fontSize = 12.sp, color = Color.Gray); Text(room.open_hours) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Place, null, Modifier.size(20.dp), tint = Color.Gray); Column { Text("주소", fontSize = 12.sp, color = Color.Gray); Text(room.address) } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.NearMe, null, Modifier.size(20.dp), tint = Color.Gray); Column { Text("거리", fontSize = 12.sp, color = Color.Gray); Text("${room.distance_m.toInt()}m") } }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

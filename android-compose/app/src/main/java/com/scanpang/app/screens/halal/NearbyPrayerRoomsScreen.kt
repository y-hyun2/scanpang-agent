package com.scanpang.app.screens.halal

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.scanpang.app.data.HalalViewModel
import com.scanpang.app.data.PrayerRoomDetail
import com.scanpang.app.navigation.HomeRoutes

@Composable
fun NearbyPrayerRoomsScreen(
    navController: NavHostController,
    onBack: () -> Unit,
    halalVm: HalalViewModel = viewModel(),
) {
    val rooms by halalVm.prayerRooms.collectAsState()
    val loading by halalVm.loading.collectAsState()

    LaunchedEffect(Unit) { halalVm.loadPrayerRooms() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 헤더
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "뒤로")
            }
            Text("주변 기도실", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(48.dp))
        }

        // 키블라 배너
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(Color(0xFFEEF2FF), RoundedCornerShape(12.dp))
                .clickable { navController.navigate(HomeRoutes.QIBLA) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Explore, null, Modifier.size(24.dp), tint = Color(0xFF3366FF))
            Text("키블라 방향 확인", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rooms.forEach { room ->
                    PrayerRoomRow(room) {
                        navController.navigate("${HomeRoutes.PRAYER_ROOM_DETAIL}/${Uri.encode(room.name)}")
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun PrayerRoomRow(room: PrayerRoomDetail, onClick: () -> Unit) {
    val facilityText = listOfNotNull(
        if (room.facilities["wudu"] == true) "우두시설" else null,
        if (room.facilities["gender_separation"] == true) "남녀분리" else null,
        if (room.facilities["prayer_mat"] == true) "기도매트" else null,
    ).joinToString(" · ").ifEmpty { "시설 정보 없음" }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(Color(0xFFF0F0F0), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Mosque, null, Modifier.size(24.dp), tint = Color(0xFF3366FF))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${room.distance_m.toInt()}m · ${room.floor.ifEmpty { "정보 없음" }} · ${
                        if (room.availability_status == "open") "이용 가능" else "확인 필요"
                    }",
                    fontSize = 13.sp, color = Color.Gray,
                )
                Text(facilityText, fontSize = 12.sp, color = Color(0xFF3366FF))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}

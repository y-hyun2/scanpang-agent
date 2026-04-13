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
import com.scanpang.app.data.HalalRestaurant
import com.scanpang.app.data.HalalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantDetailScreen(
    title: String,   // restaurant_id 또는 name_ko
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    halalVm: HalalViewModel = viewModel(),
) {
    val restaurants by halalVm.restaurants.collectAsState()

    // restaurants가 비어있으면 로드
    LaunchedEffect(Unit) {
        if (restaurants.isEmpty()) halalVm.loadRestaurants()
    }

    // title이 restaurant_id이면 ID로 매칭, 아니면 이름으로 매칭
    val restaurant = restaurants.firstOrNull { it.restaurant_id == title }
        ?: restaurants.firstOrNull { it.name_ko == title }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text(restaurant?.name_ko ?: title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { inner ->
        if (restaurant == null) {
            Box(
                Modifier.padding(inner).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 할랄 타입 뱃지
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HalalBadge(restaurant.halal_type)
                    Text(
                        restaurant.cuisine_type.joinToString(" · "),
                        fontSize = 14.sp, color = Color.Gray,
                    )
                    Text("${restaurant.distance_m.toInt()}m", fontSize = 14.sp, color = Color.Gray)
                }

                // 무슬림 조리사 / 주류 미판매
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (restaurant.muslim_cooks_available == true) {
                        InfoChip(Icons.Default.Restaurant, "무슬림 조리사")
                    }
                    if (restaurant.no_alcohol_sales == true) {
                        InfoChip(Icons.Default.NoDrinks, "주류 미판매")
                    }
                }

                Divider(color = Color(0xFFEEEEEE))

                // 대표 메뉴
                if (restaurant.menu_examples.isNotEmpty()) {
                    Text("대표 메뉴", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    restaurant.menu_examples.forEach { menu ->
                        Text("• $menu", fontSize = 14.sp, color = Color.DarkGray)
                    }
                    Divider(color = Color(0xFFEEEEEE))
                }

                // 상세 정보
                Text("상세 정보", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                InfoRow(Icons.Default.Schedule, "영업시간", restaurant.opening_hours.ifEmpty { "정보 없음" })
                if (restaurant.break_time.isNotEmpty()) {
                    InfoRow(Icons.Default.Coffee, "브레이크타임", restaurant.break_time)
                }
                if (restaurant.last_order.isNotEmpty()) {
                    InfoRow(Icons.Default.Timer, "라스트오더", restaurant.last_order)
                }
                InfoRow(Icons.Default.Place, "주소", restaurant.address)
                if (restaurant.phone.isNotEmpty()) {
                    InfoRow(Icons.Default.Phone, "전화", restaurant.phone)
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun HalalBadge(halalType: String) {
    val (bg, text, label) = when {
        halalType.contains("HALAL MEAT") -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "HALAL MEAT")
        halalType.contains("SEAFOOD") -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "SEAFOOD")
        halalType.contains("VEGGIE") -> Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), "VEGGIE")
        else -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "SALAM")
    }
    Box(
        Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 12.sp, color = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = Color(0xFF34A853))
        Text(label, fontSize = 11.sp, color = Color(0xFF2E7D32))
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = Color.Gray)
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 14.sp)
        }
    }
}

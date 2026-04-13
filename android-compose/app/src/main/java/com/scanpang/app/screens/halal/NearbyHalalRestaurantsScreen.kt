package com.scanpang.app.screens.halal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NoDrinks
import androidx.compose.material.icons.filled.Restaurant
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
import com.scanpang.app.data.HalalRestaurant
import com.scanpang.app.data.HalalViewModel
import com.scanpang.app.navigation.HomeRoutes
import android.net.Uri

private val FILTER_CHIPS = listOf("전체" to "", "HALAL MEAT" to "HALAL_MEAT", "SEAFOOD" to "SEAFOOD", "VEGGIE" to "VEGGIE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyHalalRestaurantsScreen(
    navController: NavHostController,
    onBack: () -> Unit,
    halalVm: HalalViewModel = viewModel(),
) {
    val restaurants by halalVm.restaurants.collectAsState()
    val loading by halalVm.loading.collectAsState()
    var selectedFilter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { halalVm.loadRestaurants() }

    val filtered = if (selectedFilter.isEmpty()) restaurants
    else restaurants.filter { it.halal_type.uppercase().contains(selectedFilter.replace("_", " ")) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // 헤더
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "뒤로")
            }
            Column(Modifier.weight(1f)) {
                Text("주변 할랄 식당", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("명동역 근처", fontSize = 13.sp, color = Color.Gray)
            }
        }

        // 필터 칩
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTER_CHIPS.forEach { (label, type) ->
                val selected = selectedFilter == type
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedFilter = type
                        halalVm.loadRestaurants(type)
                    },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 리스트
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                filtered.forEach { r ->
                    RestaurantCard(r) {
                        navController.navigate("${HomeRoutes.RESTAURANT_DETAIL}/${Uri.encode(r.restaurant_id)}")
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun RestaurantCard(r: HalalRestaurant, onClick: () -> Unit) {
    val chipColor = when {
        r.halal_type.contains("HALAL MEAT") -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        r.halal_type.contains("SEAFOOD") -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        r.halal_type.contains("VEGGIE") -> Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
        else -> Color(0xFFFFF3E0) to Color(0xFFE65100)
    }
    val chipLabel = when {
        r.halal_type.contains("HALAL MEAT") -> "HALAL MEAT"
        r.halal_type.contains("SEAFOOD") -> "SEAFOOD"
        r.halal_type.contains("VEGGIE") -> "VEGGIE"
        else -> "SALAM"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.name_ko, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(chipColor.first, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(chipLabel, fontSize = 11.sp, color = chipColor.second, fontWeight = FontWeight.SemiBold)
                }
                Text(r.cuisine_type.joinToString(" · "), fontSize = 12.sp, color = Color.Gray)
                Text("${r.distance_m.toInt()}m", fontSize = 12.sp, color = Color.Gray)
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Color(0xFF34A853), CircleShape)
                )
                Text("영업 중", fontSize = 11.sp, color = Color(0xFF34A853))
            }
            if (r.muslim_cooks_available == true || r.no_alcohol_sales == true) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.muslim_cooks_available == true) {
                        Row(
                            Modifier
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Restaurant, null, Modifier.size(14.dp), tint = Color(0xFF34A853))
                            Text("무슬림 조리사", fontSize = 11.sp, color = Color(0xFF2E7D32))
                        }
                    }
                    if (r.no_alcohol_sales == true) {
                        Row(
                            Modifier
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.NoDrinks, null, Modifier.size(14.dp), tint = Color(0xFF34A853))
                            Text("주류 미판매", fontSize = 11.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}

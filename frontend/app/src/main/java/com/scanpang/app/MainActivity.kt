package com.scanpang.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.scanpang.app.data.auth.SupabaseProvider
import com.scanpang.app.navigation.ScanPangApp
import com.scanpang.app.ui.theme.ScanPangTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    /**
     * Supabase SDK 에 redirect intent 를 위임한다.
     * 성공 시 SDK 가 [SupabaseProvider.auth.sessionStatus] 를 [Authenticated] 로 갱신하므로
     * 화면 측은 그 flow 만 구독하면 된다. 콜백은 단순 로깅 — 실제 분기는 sessionStatus 가 담당.
     */
    private fun feedDeeplink(intent: Intent) {
        SupabaseProvider.client.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { Log.d("SupabaseAuth", "deep link → session 복원 성공") },
            onError = { error -> Log.e("SupabaseAuth", "deep link 처리 실패", error) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold start 로 OAuth redirect deep link 가 도착한 경우.
        feedDeeplink(intent)
        setContent {
            ScanPangTheme {
                ScanPangApp(modifier = Modifier.fillMaxSize())
            }
        }
    }

    /**
     * Warm start (launchMode=singleTask) 로 OAuth redirect 가 들어왔을 때.
     * [setIntent] 로 새 intent 를 현재 액티비티에 반영한 뒤 SDK 에 핸들링을 넘긴다 —
     * 안 하면 다음 [getIntent] 호출이 옛 intent 를 반환해서 디버깅이 어렵다.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        feedDeeplink(intent)
    }
}

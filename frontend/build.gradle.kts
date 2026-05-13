plugins {
    id("com.android.application") version "8.9.2" apply false
    // supabase-kt 3.6.0 / ktor 3.4.x 의 transitive 가 Kotlin 2.3.x 기반 — 같은 라인으로 맞춰야
    // metadata 버전 불일치(reader 2.1 vs binary 2.3)로 인한 type checker 폭주를 피할 수 있다.
    // compose plugin 은 kotlin 컴파일러 플러그인이라 버전을 정확히 같게 유지.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

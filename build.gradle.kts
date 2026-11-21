// 保守版本组合：Kotlin 1.9.24 + Compose Compiler 1.5.14 + KSP 1.9.24-1.0.20
// 这套是 2024 年中期被大量生产项目验证过的，Room 2.6.1 与之兼容性最好。
// 不要随意升到 Kotlin 2.0 + Room 2.6.x，KSP 会有兼容问题。
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

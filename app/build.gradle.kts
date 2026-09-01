plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.ceireader.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.ceireader.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1,versions/**}"
        // jmrtd 0.8.8 pulls a newer BouncyCastle transitively than the explicit
        // bcprov/bcutil pins below, so bcprov-jdk18on and bcutil-jdk18on end up
        // resolved at different versions, each bundling its own (differing)
        // META-INF/LICENSE.md. Keep one copy instead of failing the merge.
        resources.pickFirsts += "META-INF/LICENSE.md"
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jmrtd:jmrtd:0.8.8")
    implementation("net.sf.scuba:scuba-sc-android:0.0.26")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcutil-jdk18on:1.78.1")
    testImplementation("junit:junit:4.13.2")
}

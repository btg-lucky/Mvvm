import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

/**
 * 从 local.properties 读聚合数据「新闻头条」的 key（不进 git）；未配置时为空串，请求会得到 key 错误提示。
 * 注意：新闻与天气是聚合数据两个独立接口产品，各自的 key 不同，故用独立属性 JUHE_NEWS_API_KEY。
 */
val juheNewsApiKey: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    props.getProperty("JUHE_NEWS_API_KEY", "")
}

android {
    namespace = "com.btg.news"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "JUHE_NEWS_API_KEY", "\"$juheNewsApiKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":lib_common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

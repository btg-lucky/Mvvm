plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.btg.opensource"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 网络（原有）
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.retrofit.converter.gson)
    api(libs.gson)
    api(libs.logger)

    // 存储
    api(libs.androidx.datastore.preferences)
    api(libs.androidx.security.crypto)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)

    // 导航
    api(libs.androidx.navigation.fragment.ktx)
    api(libs.androidx.navigation.ui.ktx)

    // 生命周期（前后台监听）
    api(libs.androidx.lifecycle.process)

    // 图片
    api(libs.coil)

    // UI
    api(libs.material)
    api(libs.smartrefresh.kernel)
    api(libs.smartrefresh.header.classics)
    api(libs.smartrefresh.footer.classics)
}

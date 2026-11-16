dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "The KissKH source only, powered by the PlavioHD addon."
    iconUrl = "https://raw.githubusercontent.com/kotlinsyd/plaviohd/main/icon.png"
    authors = listOf("Kotlinsyd")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1

    tvTypes = listOf("Movie", "TvSeries")

    // The settings sheet (addon URL field) ships layouts/strings in the cs3.
    requiresResources = true
    language = "en"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

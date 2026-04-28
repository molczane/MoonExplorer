This is a Kotlin Multiplatform project targeting Android and iOS, built on AGP 9.0 with the
`com.android.kotlin.multiplatform.library` plugin.

## Modules

* [/shared](./shared/src) — KMP library shared across all platforms.
    - [commonMain](./shared/src/commonMain/kotlin) — code common to all targets, including the
      Compose Multiplatform UI.
    - [androidMain](./shared/src/androidMain/kotlin) — Android-specific `actual` declarations.
    - [iosMain](./shared/src/iosMain/kotlin) — iOS-specific `actual` declarations and the
      `MainViewController` consumed by the iOS app.
* [/androidApp](./androidApp/src) — Android application entry point. Hosts `MainActivity`, the
  `AndroidManifest.xml`, and app-level resources. Depends on `:shared`.
* [/iosApp](./iosApp/iosApp) — iOS Xcode project. Imports the `Shared` framework produced by
  `:shared:embedAndSignAppleFrameworkForXcode` and adds any SwiftUI code on top.

## Build and Run

### Android

```shell
./gradlew :androidApp:assembleDebug
```

### iOS

Open [/iosApp](./iosApp) in Xcode and run, or invoke
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` and build from Xcode.

### Tests

```shell
./gradlew :shared:testAndroidHostTest          # Android JVM unit tests
./gradlew :shared:iosSimulatorArm64Test        # iOS simulator tests
./gradlew :shared:allTests                     # all platform tests, aggregated
```

## Toolchain

| Component   | Version       |
|-------------|---------------|
| Gradle      | 9.1.0         |
| AGP         | 9.0.x         |
| Kotlin      | 2.3.20        |
| Compose MP  | 1.10.3        |
| compileSdk  | 36            |
| minSdk      | 24            |
| JDK (build) | 17 or higher  |

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).

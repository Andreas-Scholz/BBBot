#!/usr/bin/env bash
set -euo pipefail

rm -rf generated

mkdir -p \
  generated/app/src/main/java/de/example/blockblastbot \
  generated/app/src/main/res/values \
  generated/app/src/main/res/xml

cat > generated/settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BlockBlastBot"
include(":app")
EOF

cat > generated/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
EOF

cat > generated/gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
EOF

cat > generated/app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.example.blockblastbot"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.example.blockblastbot"
        minSdk = 30
        targetSdk = 35
        versionCode = 41
        versionName = "4.1"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
EOF

cat > generated/app/src/main/AndroidManifest.xml <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="BlockBlastBot 4.1"
        android:theme="@style/AppTheme">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name=".BotAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService"/>
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config"/>
        </service>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths"/>
        </provider>
    </application>
</manifest>
EOF

cat > generated/app/src/main/res/values/styles.xml <<'EOF'
<resources>
    <style name="AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="android:fontFamily">sans</item>
    </style>
</resources>
EOF

cat > generated/app/src/main/res/values/strings.xml <<'EOF'
<resources>
    <string name="app_name">BlockBlastBot 4.1</string>
    <string name="accessibility_description">Screenshots und Touch-Gesten zur Diagnose.</string>
</resources>
EOF

cat > generated/app/src/main/res/xml/accessibility_service_config.xml <<'EOF'
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_description"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:canRetrieveWindowContent="false"
    android:notificationTimeout="100" />
EOF

cat > generated/app/src/main/res/xml/file_paths.xml <<'EOF'
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="files" path="." />
</paths>
EOF

cat > generated/app/src/main/java/de/example/blockblastbot/MainActivity.kt <<'EOF'
package de.example.blockblastbot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        status = TextView(this).apply {
            text = "BlockBlastBot 4.1 – Logging"
            textSize = 18f
            setPadding(0, 0, 0, 24)
        }

        fun button(text: String, action: () -> Unit) =
            Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }

        layout.addView(status)
        layout.addView(button("Accessibility aktivieren") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(button("TEST – erkennen") {
            BotAccessibilityService.instance?.requestSingleTest()
            status.text = "TEST angefordert – Log prüfen."
        })
        layout.addView(button("RUN – automatisch spielen") {
            BotAccessibilityService.instance?.startBot()
            status.text = "RUN gestartet."
        })
        layout.addView(button("STOP") {
            BotAccessibilityService.instance?.stopBot()
            status.text = "RUN gestoppt."
        })
        layout.addView(button("LOG EXPORT") {
            exportLog()
        })

        setContentView(layout)
    }

    private fun exportLog() {
        val file = File(filesDir, "logs/blockblast.log")
        if (!file.exists()) {
            status.text = "Noch kein Log vorhanden."
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(send, "Log exportieren"))
    }
}
EOF

cat > generated/app/src/main/java/de/example/blockblastbot/Logger.kt <<'EOF'
package de.example.blockblastbot

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Logger(context: Context) {
    private val file = File(context.filesDir, "logs/blockblast.log")

    init {
        file.parentFile?.mkdirs()
    }

    @Synchronized
    fun log(message: String) {
        val stamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.US
        ).format(Date())

        file.appendText("[$stamp] $message\n")

        if (file.length() > 1_000_000) {
            file.writeText(file.readText().takeLast(500_000))
        }
    }
}
EOF

cat > generated/app/src/main/java/de/example/blockblastbot/Vision.kt <<'EOF'
package de.example.blockblastbot

import android.graphics.Bitmap

object Vision {
    fun inspect(bitmap: Bitmap): String {
        val w = bitmap.width
        val h = bitmap.height

        var sum = 0L
        var count = 0L

        var y = (h * 0.20f).toInt()
        while (y < (h * 0.80f).toInt()) {
            var x = (w * 0.05f).toInt()

            while (x < (w * 0.95f).toInt()) {
                val p = bitmap.getPixel(x, y)
                sum += ((p shr 16) and 255)
                sum += ((p shr 8) and 255)
                sum += (p and 255)
                count += 3
                x += 12
            }

            y += 12
        }

        val average = if (count == 0L) 0.0 else sum.toDouble() / count
        return "SCREEN=${w}x${h} REGION_AVG=$average"
    }
}
EOF

cat > generated/app/src/main/java/de/example/blockblastbot/Board.kt <<'EOF'
package de.example.blockblastbot

data class Piece(val cells: List<Pair<Int, Int>>)

data class Move(
    val pieceIndex: Int,
    val row: Int,
    val col: Int,
    val clears: Int,
    val empty: Int
)

object Solver {
    fun solve(
        board: Array<BooleanArray>,
        pieces: List<Piece>
    ): List<Move> {
        val work = Array(8) { board[it].clone() }
        val result = mutableListOf<Move>()

        for ((index, piece) in pieces.withIndex()) {
            var best: Move? = null

            for (row in 0..7) {
                for (col in 0..7) {
                    if (!canPlace(work, piece, row, col)) continue

                    val test = Array(8) { work[it].clone() }
                    place(test, piece, row, col)

                    val clears = clear(test)
                    val empty = test.sumOf { cells ->
                        cells.count { !it }
                    }

                    val candidate = Move(
                        index,
                        row,
                        col,
                        clears,
                        empty
                    )

                    if (best == null || score(candidate) > score(best!!)) {
                        best = candidate
                    }
                }
            }

            if (best != null) {
                result += best!!
            }
        }

        return result
    }

    private fun score(move: Move): Int =
        move.clears * 10000 + move.empty * 10

    private fun canPlace(
        board: Array<BooleanArray>,
        piece: Piece,
        row: Int,
        col: Int
    ): Boolean =
        piece.cells.all { (dr, dc) ->
            val r = row + dr
            val c = col + dc
            r in 0..7 && c in 0..7 && !board[r][c]
        }

    private fun place(
        board: Array<BooleanArray>,
        piece: Piece,
        row: Int,
        col: Int
    ) {
        for ((dr, dc) in piece.cells) {
            board[row + dr][col + dc] = true
        }
    }

    private fun clear(board: Array<BooleanArray>): Int {
        val rows = (0..7).filter { row ->
            board[row].all { it }
        }

        val cols = (0..7).filter { col ->
            (0..7).all { row -> board[row][col] }
        }

        for (row in rows) {
            for (col in 0..7) {
                board[row][col] = false
            }
        }

        for (col in cols) {
            for (row in 0..7) {
                board[row][col] = false
            }
        }

        return rows.size + cols.size
    }
}
EOF

cat > generated/app/src/main/java/de/example/blockblastbot/BotAccessibilityService.kt <<'EOF'
package de.example.blockblastbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent

class BotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BotAccessibilityService? = null
            private set
    }

    private lateinit var logger: Logger
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var tick = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        logger = Logger(this)
        logger.log("SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        logger.log("ACCESSIBILITY_EVENT type=${event?.eventType}")
    }

    override fun onInterrupt() {
        logger.log("SERVICE_INTERRUPT")
        stopBot()
    }

    fun requestSingleTest() {
        logger.log("TEST_REQUEST")
        takeAndInspect()
    }

    fun startBot() {
        if (running) return
        running = true
        logger.log("BOT_START")
        handler.post(loop)
    }

    fun stopBot() {
        running = false
        handler.removeCallbacksAndMessages(null)
        logger.log("BOT_STOP")
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return

            tick++
            logger.log("BOT_TICK=$tick")
            takeAndInspect()

            handler.postDelayed(this, 1100)
        }
    }

    private fun takeAndInspect() {
        logger.log("SCREENSHOT_REQUEST")

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor
        ) { result ->
            if (result == null) {
                logger.log("SCREENSHOT_FAILURE result=null")
                return@takeScreenshot
            }

            try {
                val bitmap = result.hardwareBuffer?.let {
                    android.graphics.Bitmap.wrapHardwareBuffer(
                        it,
                        result.colorSpace
                    )
                }

                if (bitmap == null) {
                    logger.log("SCREENSHOT_FAILURE bitmap=null")
                } else {
                    logger.log("VISION ${Vision.inspect(bitmap)}")
                    bitmap.recycle()
                }

                result.hardwareBuffer?.close()
            } catch (t: Throwable) {
                logger.log(
                    "SCREENSHOT_EXCEPTION ${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }
    }

    fun drag(
        id: String,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 500L
    ) {
        logger.log(
            "DRAG_START id=$id start=($startX,$startY) " +
            "end=($endX,$endY) duration=$duration"
        )

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    duration
                )
            )
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    logger.log("DRAG_COMPLETED id=$id")
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    logger.log("DRAG_CANCELLED id=$id")
                }
            },
            null
        )

        logger.log(
            "DRAG_DISPATCH_RESULT id=$id dispatched=$dispatched"
        )
    }
}
EOF

echo "Complete Android project generated in ./generated"

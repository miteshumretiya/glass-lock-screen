package com.mitesh.glasslock.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mitesh.glasslock.LockActivity
import com.mitesh.glasslock.LockService
import com.mitesh.glasslock.NotificationService
import com.mitesh.glasslock.Prefs
import kotlinx.coroutines.launch

private val Accent = Color(0xFF0A84FF)
private val Done = Color(0xFF30D158)

private val TitleStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color.White)
private val SubStyle = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp, color = Color.White.copy(alpha = 0.7f))
private val SectionStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), letterSpacing = 0.6.sp)
private val RowTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
private val RowDesc = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.62f))

private fun open(context: Context, intent: Intent, fallback: Intent? = null) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        if (fallback != null) {
            try {
                context.startActivity(fallback)
            } catch (e2: Exception) {
                Toast.makeText(context, "Couldn't open settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

@SuppressLint("BatteryLife")
@Composable
fun SetupScreen(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val key = resumeTick + refresh

    val overlayOk = remember(key) { Settings.canDrawOverlays(context) }
    val listenerOk = remember(key) { NotificationService.isEnabled(context) }
    val notificationsOk = remember(key) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(key) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var showContent by remember { mutableStateOf(prefs.showContent) }
    var launchOnOff by remember { mutableStateOf(prefs.launchOnScreenOff) }
    var wallpaperVersion by remember { mutableIntStateOf(prefs.wallpaperVersion) }
    val thumb by produceState<ImageBitmap?>(null, wallpaperVersion) { value = WallpaperStore.thumbnail(context) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (WallpaperStore.save(context, uri)) {
                    prefs.wallpaperVersion = prefs.wallpaperVersion + 1
                    wallpaperVersion = prefs.wallpaperVersion
                } else {
                    Toast.makeText(context, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(overlayOk) {
        if (!overlayOk && enabled) {
            enabled = false
            prefs.enabled = false
            LockService.stop(context)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondaryContainer = Color.White.copy(alpha = 0.16f),
            onSecondaryContainer = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0A12))) {
            thumb?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f,
                    modifier = Modifier.fillMaxSize().blur(48.dp),
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                Text("Glass Lock", style = TitleStyle)
                Spacer(Modifier.height(6.dp))
                Text(
                    "An iOS-style liquid glass lock screen that sits on top of your Pixel's own secure lock.",
                    style = SubStyle,
                )

                Spacer(Modifier.height(26.dp))
                Text("SET UP", style = SectionStyle)
                Spacer(Modifier.height(8.dp))
                StepCard(1, "Show over other apps", "Lets the lock screen appear when your screen turns off.", overlayOk) {
                    open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)))
                }
                StepCard(2, "Notification access", "Shows your notifications and what's playing.", listenerOk) {
                    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotificationService.component(context).flattenToString())
                    open(context, detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                if (!listenerOk) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Greyed out or \"Restricted setting\"? Open App info, tap ⋮ and choose Allow restricted settings, then try again.",
                            style = RowDesc,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))) }) {
                            Text("App info")
                        }
                    }
                }
                StepCard(3, "Notifications", "Keeps one quiet status notification so Android doesn't stop the lock screen.", notificationsOk) {
                    if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                StepCard(4, "Unrestricted battery", "Stops Android from pausing the lock screen in the background.", batteryOk) {
                    open(
                        context,
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text("LOCK SCREEN", style = SectionStyle)
                Spacer(Modifier.height(8.dp))
                Card {
                    SwitchRow(
                        title = "Use Glass Lock",
                        desc = if (overlayOk) "Appears every time your screen turns on." else "Finish step 1 first.",
                        checked = enabled && overlayOk,
                        enabled = overlayOk,
                    ) { on ->
                        enabled = on
                        prefs.enabled = on
                        if (on) LockService.start(context) else LockService.stop(context)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(width = 54.dp, height = 112.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                        ) {
                            thumb?.let {
                                Image(it, contentDescription = "Current wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Wallpaper", style = RowTitle)
                            Spacer(Modifier.height(2.dp))
                            Text("Pick any photo, for example your current lock screen wallpaper.", style = RowDesc)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                ) { Text("Choose photo") }
                                OutlinedButton(
                                    onClick = {
                                        WallpaperStore.reset(context)
                                        prefs.wallpaperVersion = prefs.wallpaperVersion + 1
                                        wallpaperVersion = prefs.wallpaperVersion
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                ) { Text("Default") }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SwitchRow(
                        title = "Show notification text",
                        desc = "Turn off to show only the app name while locked.",
                        checked = showContent,
                    ) { on ->
                        showContent = on
                        prefs.showContent = on
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SwitchRow(
                        title = "Get ready when the screen turns off",
                        desc = "Smoothest wake-up. Turn off if your Always-on display stops showing.",
                        checked = launchOnOff,
                    ) { on ->
                        launchOnOff = on
                        prefs.launchOnScreenOff = on
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { LockActivity.launch(context, preview = true) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text("Preview lock screen", style = RowTitle)
                }

                Spacer(Modifier.height(18.dp))
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("How unlocking works", style = RowTitle)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Swipe up on the glass lock screen, then use your fingerprint or PIN as usual. " +
                                "Your Pixel's own lock stays on underneath, so the phone stays protected. " +
                                "Hold the flashlight or camera button to use it without unlocking.",
                            style = RowDesc,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        content = content,
    )
}

@Composable
private fun StepCard(number: Int, title: String, desc: String, done: Boolean, onAllow: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (done) Done else Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = RowTitle.copy(fontSize = 14.sp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = RowTitle)
            Text(desc, style = RowDesc)
        }
        if (!done) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAllow, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text("Allow")
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = RowTitle)
            Text(desc, style = RowDesc)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Done, checkedThumbColor = Color.White),
        )
    }
}

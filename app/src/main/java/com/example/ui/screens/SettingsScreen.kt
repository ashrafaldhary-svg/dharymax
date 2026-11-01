package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VelvetBlack
import com.example.ui.viewmodel.AppSettingsState

@Composable
fun SettingsScreen(
    settings: AppSettingsState,
    onUpdateSettings: (Int, Boolean, Boolean, Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VelvetBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "إعدادات التطبيق",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "تخصيص سلوك التحميل والتخزين والواجهة",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Concurrent Downloads Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CrimsonNeon.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CrimsonNeon, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "التحميلات المتزامنة في نفس الوقت",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "الحد الأقصى للملفات المحملة معاً",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "${settings.maxConcurrent} تحميلات",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonNeon
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = settings.maxConcurrent.toFloat(),
                    onValueChange = {
                        onUpdateSettings(it.toInt(), settings.wifiOnly, settings.autoResume, settings.notificationsEnabled)
                    },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = CrimsonNeon,
                        activeTrackColor = CrimsonNeon,
                        inactiveTrackColor = SurfaceBorder
                    ),
                    modifier = Modifier.testTag("concurrent_downloads_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Switches Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Wi-Fi Only Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "التحميل عبر Wi-Fi فقط", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "حفظ باقة بيانات الهاتف المحمول", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = settings.wifiOnly,
                        onCheckedChange = {
                            onUpdateSettings(settings.maxConcurrent, it, settings.autoResume, settings.notificationsEnabled)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanAccent),
                        modifier = Modifier.testTag("wifi_only_switch")
                    )
                }

                // Auto Resume Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = CrimsonNeon, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "استئناف تلقائي عند عودة الشبكة", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "مواصلة التنزيل تلقائياً بدون إعادة من الصفر", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = settings.autoResume,
                        onCheckedChange = {
                            onUpdateSettings(settings.maxConcurrent, settings.wifiOnly, it, settings.notificationsEnabled)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonNeon),
                        modifier = Modifier.testTag("auto_resume_switch")
                    )
                }

                // Notifications Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "إشعارات اكتمال التحميل", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "تنبيه عند انتهاء تحميل أي ملف وسائط", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = {
                            onUpdateSettings(settings.maxConcurrent, settings.wifiOnly, settings.autoResume, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonNeon),
                        modifier = Modifier.testTag("notifications_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Storage Locations Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = CyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "مسارات حفظ الوسائط", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• الفيديوهات: Movies/Dhari Max (MediaStore)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "• الصوتيات: Music/Dhari Max (MediaStore)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تظهر الملفات مباشرة في المعرض ومشغلات الموسيقى وتطبيقات الملفات.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonNeon
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Actions Card (Clear History)
        Button(
            onClick = onClearHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("clear_history_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "مسح سجل التحميلات المكتملة", color = TextPrimary, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // About Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "ذاري ماكس – Dhari Max", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CrimsonNeon)
                Text(text = "الإصدار 1.0.0 • بنية MVVM و Room و MediaStore", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "تم التطوير وفق أعلى معايير أداء أندرويد وتجربة المستخدم RTL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

package com.example.platformtool.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Components · Light", showBackground = true, widthDp = 360, heightDp = 760)
@Preview(
    name = "Components · Dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Components · 1.3× font", showBackground = true, widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Preview(name = "Components · Wide", showBackground = true, widthDp = 840, heightDp = 600)
@Composable
private fun PlatformComponentCatalogPreview() {
    PlatformTheme(useDynamicColor = false) {
        Column(
            modifier = Modifier.padding(PlatformTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(PlatformTokens.spacing.md),
        ) {
            ToolCard(
                title = "传感器",
                description = "实时查看硬件传感器状态与原始数据",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                badge = "可用",
            )
            StatusBanner(
                title = "ADB Bridge 未连接",
                message = "受保护操作将使用 App Shell；连接后可重试。",
                tone = StatusTone.WARNING,
                actionLabel = "查看步骤",
                onAction = {},
            )
            MetricCard(label = "电池电压", value = "4.182", unit = "V", supportingText = "刚刚更新")
            EngineeringOutput(
                label = "输出",
                text = "$ getprop ro.build.version.release\n14\n[退出码 0 · 0.021 秒]",
            )
        }
    }
}

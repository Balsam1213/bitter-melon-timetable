package com.balsam.timetable.widget

import androidx.glance.color.ColorProviders
import androidx.glance.color.ColorProvider
import androidx.glance.color.colorProviders
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.ui.theme.glanceColorSchemesOf

/**
 * 小组件主题：应用内设置的四主题（兰紫/靛蓝/松绿/玫粉）映射到 Glance 色板，
 * 浅色/深色各一套（ColorProvider(day, night) 由系统深色模式自动选择）。
 * 其余角色（tertiary/error 等）沿用 Glance 默认，组件只用到这里覆盖的角色。
 */
internal fun widgetColors(theme: ColorTheme): ColorProviders {
    val (light, dark) = glanceColorSchemesOf(theme)
    return colorProviders(
        primary = ColorProvider(light.primary, dark.primary),
        onPrimary = ColorProvider(light.onPrimary, dark.onPrimary),
        primaryContainer = ColorProvider(light.primaryContainer, dark.primaryContainer),
        onPrimaryContainer = ColorProvider(light.onPrimaryContainer, dark.onPrimaryContainer),
        secondary = ColorProvider(light.secondary, dark.secondary),
        onSecondary = ColorProvider(light.onSecondary, dark.onSecondary),
        secondaryContainer = ColorProvider(light.secondaryContainer, dark.secondaryContainer),
        onSecondaryContainer = ColorProvider(light.onSecondaryContainer, dark.onSecondaryContainer),
        tertiary = ColorProvider(light.tertiary, dark.tertiary),
        onTertiary = ColorProvider(light.onTertiary, dark.onTertiary),
        tertiaryContainer = ColorProvider(light.tertiaryContainer, dark.tertiaryContainer),
        onTertiaryContainer = ColorProvider(light.onTertiaryContainer, dark.onTertiaryContainer),
        error = ColorProvider(light.error, dark.error),
        errorContainer = ColorProvider(light.errorContainer, dark.errorContainer),
        onError = ColorProvider(light.onError, dark.onError),
        onErrorContainer = ColorProvider(light.onErrorContainer, dark.onErrorContainer),
        background = ColorProvider(light.background, dark.background),
        onBackground = ColorProvider(light.onBackground, dark.onBackground),
        surface = ColorProvider(light.surface, dark.surface),
        onSurface = ColorProvider(light.onSurface, dark.onSurface),
        surfaceVariant = ColorProvider(light.surfaceVariant, dark.surfaceVariant),
        onSurfaceVariant = ColorProvider(light.onSurfaceVariant, dark.onSurfaceVariant),
        outline = ColorProvider(light.outline, dark.outline),
        inverseOnSurface = ColorProvider(light.inverseOnSurface, dark.inverseOnSurface),
        inverseSurface = ColorProvider(light.inverseSurface, dark.inverseSurface),
        inversePrimary = ColorProvider(light.inversePrimary, dark.inversePrimary),
    )
}

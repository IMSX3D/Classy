package com.nevoit.glasense.material

import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.effect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect


fun BackdropEffectScope.multiLayerGlass(
    cornerRadii: FloatArray,
    firstBlurRadius: Float,
    firstOpacity: Float,
    firstRefractionHeight: Float,
    firstRefractionAmount: Float,
    secondBlurRadius: Float,
    secondOpacity: Float,
    secondRefractionHeight: Float,
    secondRefractionAmount: Float
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    effect(
        multiLayerGlassEffect(
            width = size.width,
            height = size.height,
            cornerRadii = cornerRadii,
            firstBlurRadius = firstBlurRadius,
            firstOpacity = firstOpacity,
            firstRefractionHeight = firstRefractionHeight,
            firstRefractionAmount = firstRefractionAmount,
            secondBlurRadius = secondBlurRadius,
            secondOpacity = secondOpacity,
            secondRefractionHeight = secondRefractionHeight,
            secondRefractionAmount = secondRefractionAmount
        )
    )
}

/** Builds independent system-blur branches and composites them into one RenderEffect. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun multiLayerGlassEffect(
    width: Float,
    height: Float,
    cornerRadii: FloatArray,
    firstBlurRadius: Float,
    firstOpacity: Float,
    firstRefractionHeight: Float,
    firstRefractionAmount: Float,
    secondBlurRadius: Float,
    secondOpacity: Float,
    secondRefractionHeight: Float,
    secondRefractionAmount: Float
): ComposeRenderEffect {
    val first = glassBranch(
        width = width,
        height = height,
        cornerRadii = cornerRadii,
        blurRadius = firstBlurRadius,
        opacity = firstOpacity,
        refractionHeight = firstRefractionHeight,
        refractionAmount = firstRefractionAmount
    )
    val second = glassBranch(
        width = width,
        height = height,
        cornerRadii = cornerRadii,
        blurRadius = secondBlurRadius,
        opacity = secondOpacity,
        refractionHeight = secondRefractionHeight,
        refractionAmount = secondRefractionAmount
    )

    return RenderEffect.createBlendModeEffect(
        first,
        second,
        BlendMode.SRC_OVER
    ).asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun glassBranch(
    width: Float,
    height: Float,
    cornerRadii: FloatArray,
    blurRadius: Float,
    opacity: Float,
    refractionHeight: Float,
    refractionAmount: Float
): RenderEffect {
    val blur = RenderEffect.createBlurEffect(
        blurRadius,
        blurRadius,
        Shader.TileMode.DECAL
    )
    val lensShader = RuntimeShader(LENS_SHADER).apply {
        setFloatUniform("size", width, height)
        setFloatUniform("cornerRadii", cornerRadii)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", 0f)
    }
    val lens = RenderEffect.createRuntimeShaderEffect(lensShader, "content")
    val blurredAndRefracted = RenderEffect.createChainEffect(lens, blur)

    val alpha = opacity.coerceIn(0f, 1f)
    val matrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, alpha, 0f
        )
    )
    return RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(matrix),
        blurredAndRefracted
    )
}

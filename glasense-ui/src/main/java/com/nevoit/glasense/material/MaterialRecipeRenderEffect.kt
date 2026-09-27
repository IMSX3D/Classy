package com.nevoit.glasense.material

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.nevoit.glasense.core.utility.supportsRuntimeShaderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

@Composable
fun rememberMaterialRenderEffectOrNull(recipe: MaterialRecipe): ComposeRenderEffect? {
    return if (supportsRuntimeShaderEffect()) {
        rememberMaterialRenderEffect(recipe)
    } else {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun rememberMaterialRenderEffect(recipe: MaterialRecipe): ComposeRenderEffect {
    return remember(recipe) {
        recipe.toRenderEffect()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun MaterialRecipe.toRenderEffect(): ComposeRenderEffect {
    val shader = RuntimeShader(AGSL_CODE)

    shader.setFloatUniform("p0", luminanceMapCurve.p0)
    shader.setFloatUniform("p1", luminanceMapCurve.p1)
    shader.setFloatUniform("p2", luminanceMapCurve.p2)
    shader.setFloatUniform("p3", luminanceMapCurve.p3)
    shader.setFloatUniform("mapIntensity", luminanceMapIntensity)
    shader.setFloatUniform("saturation", saturation)
    shader.setFloatUniform("brightness", extraBrightness)
    shader.setFloatUniform("ditherStrength", 1.0f)

    return RenderEffect.createRuntimeShaderEffect(
        shader,
        "image"
    ).asComposeRenderEffect()
}

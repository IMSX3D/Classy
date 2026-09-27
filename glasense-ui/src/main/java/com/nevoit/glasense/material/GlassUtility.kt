package com.nevoit.glasense.material

import android.graphics.BlurMaskFilter
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangularShape
import com.nevoit.glasense.R
import kotlin.math.PI
import kotlin.math.ceil

private val VerticalHighLight = Highlight.Default.copy(
    style = HighlightStyle.Default(
        angle = 90f
    ),
    alpha = 0.35f
)

data class GlassStyle(
    val firstBlurRadius: Dp = 2.dp,
    val firstOpacity: Float = 1f,
    val firstRefractionHeight: Dp = 16.dp,
    val firstRefractionAmount: Dp = 48.dp,
    val secondBlurRadius: Dp = 16.dp,
    val secondOpacity: Float = 0.8f,
    val secondRefractionHeight: Dp = 16.dp,
    val secondRefractionAmount: Dp = 48.dp,
    val rimHeight: Dp = 16.dp,
    val rimAlpha: Float = 0.75f,
    val rimBlendMode: BlendMode = BlendMode.Plus
)

@Composable
fun Modifier.glassDecorations(
    shape: Shape,
    style: GlassStyle = GlassStyle(),
): Modifier {
    val rimLightImage = ImageBitmap.imageResource(R.drawable.glass_top_rim_light)
    val highlight = VerticalHighLight
    return this.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val drawDecorations: DrawScope.() -> Unit = {
            drawKyantHighlight(
                shape = shape,
                width = highlight.width,
                blurRadius = highlight.blurRadius,
                alpha = highlight.alpha,
                style = highlight.style
            )
            drawGlassRim(
                image = rimLightImage,
                height = style.rimHeight,
                alpha = style.rimAlpha,
                blendMode = style.rimBlendMode
            )
        }
        when (outline) {
            is Outline.Rounded -> clipPath(
                Path().apply { addRoundRect(outline.roundRect) },
                block = drawDecorations
            )

            is Outline.Generic -> clipPath(outline.path, block = drawDecorations)
            is Outline.Rectangle -> clipRect(
                outline.rect.left,
                outline.rect.top,
                outline.rect.right,
                outline.rect.bottom,
                block = drawDecorations
            )
        }
    }
}

private fun DrawScope.drawKyantHighlight(
    shape: Shape,
    width: Dp,
    blurRadius: Dp,
    alpha: Float,
    style: HighlightStyle
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val paint = Paint().apply {
        this.style = PaintingStyle.Stroke
        color = style.color
        strokeWidth = ceil(
            width.toPx().fastCoerceAtMost(size.minDimension / 2f)
        ) * 2f
        this.alpha = alpha.coerceIn(0f, 1f)
        blendMode = style.blendMode
        if (blurRadius.value > 0f) {
            nativePaint.maskFilter = BlurMaskFilter(
                blurRadius.toPx(),
                BlurMaskFilter.Blur.NORMAL
            )
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && style is HighlightStyle.Default) {
        val maxRadius = size.minDimension / 2f
        val radii = shape.cornerRadii(this, maxRadius)
        val shader = RuntimeShader(DEFAULT_HIGHLIGHT_SHADER).apply {
            setFloatUniform("size", size.width, size.height)
            setFloatUniform("cornerRadii", radii)
            setColorUniform("color", style.color.copy(alpha = 1f).toArgb())
            setFloatUniform("angle", style.angle * (PI / 180f).toFloat())
            setFloatUniform("falloff", style.falloff)
        }
        paint.nativePaint.shader = shader
    }
    drawIntoCanvas { canvas ->
        when (outline) {
            is Outline.Rounded -> {
                val path = Path().apply { addRoundRect(outline.roundRect) }
                canvas.drawPath(path, paint)
            }

            is Outline.Generic -> canvas.drawPath(outline.path, paint)
            is Outline.Rectangle -> canvas.drawRect(outline.rect, paint)
        }
    }
}

private fun Shape.cornerRadii(scope: DrawScope, maxRadius: Float): FloatArray {
    val outline = createOutline(scope.size, scope.layoutDirection, scope)
    if (outline !is Outline.Rounded) {
        return FloatArray(4) { maxRadius }
    }
    val r = outline.roundRect
    return floatArrayOf(
        r.topLeftCornerRadius.x.coerceAtMost(maxRadius),
        r.topRightCornerRadius.x.coerceAtMost(maxRadius),
        r.bottomRightCornerRadius.x.coerceAtMost(maxRadius),
        r.bottomLeftCornerRadius.x.coerceAtMost(maxRadius)
    )
}

@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: RoundedRectangularShape,
    style: GlassStyle = GlassStyle(),
    materialEffect: RenderEffect? = null,
    shadow: (() -> Shadow?)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    drawRim: Boolean = true
): Modifier {
    val rimLightImage = if (drawRim) {
        ImageBitmap.imageResource(R.drawable.glass_top_rim_light)
    } else null

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = { VerticalHighLight },
        shadow = shadow,
        effects = {
            val corners = shape.corners(size, layoutDirection, this)
            multiLayerGlass(
                cornerRadii = floatArrayOf(
                    corners.topLeft,
                    corners.topRight,
                    corners.bottomRight,
                    corners.bottomLeft
                ),
                firstBlurRadius = style.firstBlurRadius.toPx(),
                firstOpacity = style.firstOpacity,
                firstRefractionHeight = style.firstRefractionHeight.toPx(),
                firstRefractionAmount = style.firstRefractionAmount.toPx(),
                secondBlurRadius = style.secondBlurRadius.toPx(),
                secondOpacity = style.secondOpacity,
                secondRefractionHeight = style.secondRefractionHeight.toPx(),
                secondRefractionAmount = style.secondRefractionAmount.toPx()
            )
            materialEffect?.let { effect(it) }
        },
        onDrawSurface = {
            onDrawSurface?.invoke(this)
            rimLightImage?.let {
                drawGlassRim(
                    image = it,
                    height = style.rimHeight,
                    alpha = style.rimAlpha,
                    blendMode = style.rimBlendMode
                )
            }
        },
        layerBlock = layerBlock
    )
}

fun DrawScope.drawGlassRim(
    image: ImageBitmap,
    height: Dp,
    alpha: Float = 0.75f,
    blendMode: BlendMode = BlendMode.Plus
) {
    val rimHeightPx = height.toPx()
    val sourceSize = IntSize(image.width, image.height)
    val destinationSize = IntSize(size.width.toInt(), rimHeightPx.toInt())

    drawImage(
        image = image,
        srcSize = sourceSize,
        dstSize = destinationSize,
        blendMode = blendMode,
        alpha = alpha
    )
    withTransform({
        scale(scaleX = 1f, scaleY = -1f)
    }) {
        drawImage(
            image = image,
            srcSize = sourceSize,
            dstSize = destinationSize,
            blendMode = blendMode,
            alpha = alpha
        )
    }
}


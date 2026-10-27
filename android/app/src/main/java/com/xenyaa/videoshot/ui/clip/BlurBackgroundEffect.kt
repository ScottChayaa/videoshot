package com.xenyaa.videoshot.ui.clip

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** 背景在幾分之一的解析度上模糊。 */
private const val BLUR_DOWNSCALE = 8

/**
 * 模糊背景＋正中央的清晰原圖，一個效果畫完（剪映那種「放大蓋滿再模糊」）。
 *
 * 不用 Media3 內建的 `GaussianBlurWithFrameOverlaid`：它在整張畫布上做大半徑卷積，蓋滿又要先把畫布放大到
 * 輸出框的 3 倍，手機 GPU 吃不消（2026-10-27 實機：播放時主執行緒被卡 1～2 秒、換段時停頓）。
 * 這裡背景先等比蓋滿、縮到 1/[BLUR_DOWNSCALE] 解析度再模糊，最後放大鋪滿；輸入原樣疊在正中央。
 *
 * 輸入是已經縮放裁切好的清晰原圖（[sharpSize] 的大小），輸出 [outWidth]×[outHeight]。
 */
@UnstableApi
class BlurBackgroundEffect(
    private val outWidth: Int,
    private val outHeight: Int,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        try {
            BlurBackgroundProgram(outWidth, outHeight, useHdr)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
}

/**
 * 每一幀：① 輸入等比蓋滿、縮成小圖（四點平均，減少縮小時的閃爍）；② 小圖橫向、縱向模糊兩輪；
 * ③ 回到輸出框：小圖放大鋪滿當背景，輸入疊在正中央。
 */
@UnstableApi
private class BlurBackgroundProgram(
    private val outWidth: Int,
    private val outHeight: Int,
    private val useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, 1) {

    private val copy = GlProgram(VERTEX, FRAGMENT_COPY)
    private val down = GlProgram(VERTEX, FRAGMENT_DOWNSAMPLE)
    private val blur = GlProgram(VERTEX, FRAGMENT_BLUR)

    private val smallWidth = (outWidth / BLUR_DOWNSCALE).coerceAtLeast(1)
    private val smallHeight = (outHeight / BLUR_DOWNSCALE).coerceAtLeast(1)
    private var smallTex = IntArray(2) { -1 }
    private var smallFbo = IntArray(2) { -1 }
    private var inputWidth = 0
    private var inputHeight = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        return Size(outWidth, outHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // BaseGlShaderProgram 已經把輸出框綁好了，畫完小圖要綁回去。要在建小貼圖之前讀：
            // 建 FBO 會改掉目前綁定的框，第一格會畫到別處、輸出全黑（暫停在第一格時看得到）
            val outFbo = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, it, 0) }[0]
            ensureSmallTextures()
            val identity = GlUtil.create4x4IdentityMatrix()

            // ① 蓋滿小圖
            GlUtil.focusFramebufferUsingCurrentContext(smallFbo[0], smallWidth, smallHeight)
            val bg = coverCrop(inputWidth.toFloat() / inputHeight, outWidth.toFloat() / outHeight)
            down.use()
            down.setSamplerTexIdUniform("uTex", inputTexId, 0)
            down.setFloatsUniform("uTexCrop", bg)
            // 一個小圖像素在輸入上佔多大（貼圖座標），四個取樣點落在它的四個象限
            down.setFloatsUniform("uHalfPixel", floatArrayOf(bg[0] / smallWidth / 4, bg[1] / smallHeight / 4))
            drawQuad(down, identity)

            // ② 橫向、縱向模糊兩輪（一輪的半徑太小，步距拉大又會出現疊影）
            repeat(2) {
                blurPass(smallTex[0], smallFbo[1], floatArrayOf(1f / smallWidth, 0f), identity)
                blurPass(smallTex[1], smallFbo[0], floatArrayOf(0f, 1f / smallHeight), identity)
            }

            // ③ 背景鋪滿，輸入置中
            GlUtil.focusFramebufferUsingCurrentContext(outFbo, outWidth, outHeight)
            copy.use()
            copy.setSamplerTexIdUniform("uTex", smallTex[0], 0)
            copy.setFloatsUniform("uTexCrop", floatArrayOf(1f, 1f, 0f, 0f))
            drawQuad(copy, identity)
            copy.setSamplerTexIdUniform("uTex", inputTexId, 0)
            val center = GlUtil.create4x4IdentityMatrix().also {
                Matrix.scaleM(it, 0, inputWidth.toFloat() / outWidth, inputHeight.toFloat() / outHeight, 1f)
            }
            drawQuad(copy, center)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun blurPass(srcTex: Int, dstFbo: Int, step: FloatArray, identity: FloatArray) {
        GlUtil.focusFramebufferUsingCurrentContext(dstFbo, smallWidth, smallHeight)
        blur.use()
        blur.setSamplerTexIdUniform("uTex", srcTex, 0)
        blur.setFloatsUniform("uStep", step)
        drawQuad(blur, identity)
    }

    private fun drawQuad(program: GlProgram, transform: FloatArray) {
        program.setFloatsUniform("uTransform", transform)
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    private fun ensureSmallTextures() {
        if (smallTex[0] != -1) return
        for (i in 0..1) {
            smallTex[i] = GlUtil.createTexture(smallWidth, smallHeight, useHdr)
            smallFbo[i] = GlUtil.createFboForTexture(smallTex[i])
        }
    }

    override fun release() {
        super.release()
        try {
            copy.delete()
            down.delete()
            blur.delete()
            for (i in 0..1) {
                if (smallFbo[i] != -1) GlUtil.deleteFbo(smallFbo[i])
                if (smallTex[i] != -1) GlUtil.deleteTexture(smallTex[i])
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

/**
 * 長寬比 [srcAspect] 的貼圖等比蓋滿長寬比 [dstAspect] 的框時，要取的貼圖範圍：
 * （寬比例, 高比例, 左邊起點, 下邊起點），都是 0～1 的貼圖座標。純計算。
 */
internal fun coverCrop(srcAspect: Float, dstAspect: Float): FloatArray =
    if (srcAspect > dstAspect) {
        val w = dstAspect / srcAspect
        floatArrayOf(w, 1f, (1f - w) / 2, 0f)
    } else {
        val h = srcAspect / dstAspect
        floatArrayOf(1f, h, 0f, (1f - h) / 2)
    }

private const val VERTEX = """
attribute vec4 aFramePosition;
uniform mat4 uTransform;
varying vec2 vTexCoords;
void main() {
  gl_Position = uTransform * aFramePosition;
  vTexCoords = aFramePosition.xy * 0.5 + 0.5;
}
"""

/** uTexCrop = (寬比例, 高比例, 左, 下)：畫面座標 0～1 對到貼圖的這一塊。 */
private const val FRAGMENT_COPY = """
precision highp float;
uniform sampler2D uTex;
uniform vec4 uTexCrop;
varying vec2 vTexCoords;
void main() {
  gl_FragColor = texture2D(uTex, vTexCoords * uTexCrop.xy + uTexCrop.zw);
}
"""

private const val FRAGMENT_DOWNSAMPLE = """
precision highp float;
uniform sampler2D uTex;
uniform vec4 uTexCrop;
uniform vec2 uHalfPixel;
varying vec2 vTexCoords;
void main() {
  vec2 c = vTexCoords * uTexCrop.xy + uTexCrop.zw;
  gl_FragColor = 0.25 * (
      texture2D(uTex, c + vec2(-uHalfPixel.x, -uHalfPixel.y)) +
      texture2D(uTex, c + vec2(uHalfPixel.x, -uHalfPixel.y)) +
      texture2D(uTex, c + vec2(-uHalfPixel.x, uHalfPixel.y)) +
      texture2D(uTex, c + vec2(uHalfPixel.x, uHalfPixel.y)));
}
"""

/** 9 點高斯（利用雙線性取樣，5 次取樣）。uStep = 一個像素（含方向）。 */
private const val FRAGMENT_BLUR = """
precision highp float;
uniform sampler2D uTex;
uniform vec2 uStep;
varying vec2 vTexCoords;
void main() {
  vec2 s = uStep;
  vec4 c = texture2D(uTex, vTexCoords) * 0.2270270270;
  c += (texture2D(uTex, vTexCoords + s * 1.3846153846) + texture2D(uTex, vTexCoords - s * 1.3846153846)) * 0.3162162162;
  c += (texture2D(uTex, vTexCoords + s * 3.2307692308) + texture2D(uTex, vTexCoords - s * 3.2307692308)) * 0.0702702703;
  gl_FragColor = c;
}
"""

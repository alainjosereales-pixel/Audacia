package com.audacia.orion.vision

/**
 * Analizador canónico de profundidad y segmentación espacial para ORION v6.
 *
 * Reproduce exactamente la formulación matemática de versions/v5_web_xiao_esp32s3/main.py (L186-L225)
 * y tools/reference_pipeline.py.
 */
object DepthAnalyzer {

    fun normalize(
        depthRaw: FloatArray,
        invert: Boolean = false,
        outArray: FloatArray? = null
    ): FloatArray {
        if (depthRaw.isEmpty()) return FloatArray(0)

        var minVal = depthRaw[0]
        var maxVal = depthRaw[0]
        for (i in 1 until depthRaw.size) {
            val v = depthRaw[i]
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }

        val span = maxVal - minVal
        val output = if (outArray != null && outArray.size == depthRaw.size) {
            outArray
        } else {
            FloatArray(depthRaw.size)
        }

        if (span < 1e-6f) {
            // Escena plana / oscuridad: devolver ceros para no amplificar ruido
            if (output === outArray) {
                output.fill(0f)
            }
            return output
        }

        val invSpan = 1.0f / span
        if (invert) {
            for (i in depthRaw.indices) {
                output[i] = 1.0f - ((depthRaw[i] - minVal) * invSpan)
            }
        } else {
            for (i in depthRaw.indices) {
                output[i] = (depthRaw[i] - minVal) * invSpan
            }
        }

        return output
    }

    fun analyze(
        depthNorm: FloatArray,
        width: Int,
        height: Int,
        nearThreshold: Float = 0.60f,
        sampleStep: Int = 4
    ): ZoneResult {
        val step = maxOf(1, sampleStep)
        val sampledH = height / step
        val sampledW = width / step

        if (sampledH == 0 || sampledW == 0 || depthNorm.isEmpty()) {
            return ZoneResult(Action.FORWARD, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, null)
        }

        val third = sampledW / 3

        var leftSum = 0.0
        var centerSum = 0.0
        var rightSum = 0.0
        var overallSum = 0.0

        var leftCount = 0
        var centerCount = 0
        var rightCount = 0
        var overallCount = 0

        for (r in 0 until sampledH) {
            val origR = r * step
            val rowOffset = origR * width

            for (c in 0 until sampledW) {
                val origC = c * step
                val value = depthNorm[rowOffset + origC].toDouble()

                overallSum += value
                overallCount++

                when {
                    c < third -> {
                        leftSum += value
                        leftCount++
                    }
                    c < 2 * third -> {
                        centerSum += value
                        centerCount++
                    }
                    else -> {
                        rightSum += value
                        rightCount++
                    }
                }
            }
        }

        val leftMean = if (leftCount > 0) (leftSum / leftCount).toFloat() else 0.0f
        val centerMean = if (centerCount > 0) (centerSum / centerCount).toFloat() else 0.0f
        val rightMean = if (rightCount > 0) (rightSum / rightCount).toFloat() else 0.0f
        val overallMean = if (overallCount > 0) (overallSum / overallCount).toFloat() else 0.0f

        val isStop = (overallMean > 0.9f || centerMean > nearThreshold)
        val action = if (isStop) Action.STOP else Action.FORWARD

        val intensity = if (isStop) {
            val reference = maxOf(centerMean, overallMean)
            val span = maxOf(1e-6f, 1.0f - nearThreshold)
            minOf(1.0f, maxOf(0.0f, (reference - nearThreshold) / span))
        } else {
            0.0f
        }

        val candidates = listOf(
            Zone.LEFT to leftMean,
            Zone.CENTER to centerMean,
            Zone.RIGHT to rightMean
        )
        val best = candidates.maxByOrNull { it.second }
        val nearestZone = if (best != null && best.second > nearThreshold) best.first else null

        return ZoneResult(
            action = action,
            intensity = intensity,
            left = leftMean,
            center = centerMean,
            right = rightMean,
            overall = overallMean,
            nearestZone = nearestZone
        )
    }
}

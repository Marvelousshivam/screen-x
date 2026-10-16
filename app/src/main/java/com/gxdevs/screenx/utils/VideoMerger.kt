package com.gxdevs.screenx.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Losslessly merges multiple sequential MP4 video files into a single output file
 * using MediaExtractor and MediaMuxer without re-encoding.
 */
object VideoMerger {
    private const val TAG = "VideoMerger"
    private const val BUFFER_SIZE = 2 * 1024 * 1024 // 2 MB buffer

    /**
     * Losslessly concatenates sequential MP4 files [inputFiles] into [outputFile].
     * Rebases timestamps so each segment plays gaplessly after the previous one.
     *
     * @param inputFiles Ordered list of video files to concatenate.
     * @param outputFile Target merged file.
     * @param progressCallback Optional progress callback (0.0f to 1.0f).
     * @return true if merge succeeded and outputFile exists with non-zero size.
     */
    fun mergeSegments(
        inputFiles: List<File>,
        outputFile: File,
        progressCallback: (Float) -> Unit = {}
    ): Boolean {
        val validFiles = inputFiles.filter { it.exists() && it.length() > 1024L }
        if (validFiles.isEmpty()) {
            Log.w(TAG, "mergeSegments: No valid input files found.")
            return false
        }

        if (validFiles.size == 1) {
            val src = validFiles[0]
            return try {
                if (src.absolutePath == outputFile.absolutePath) {
                    true
                } else {
                    if (outputFile.exists()) outputFile.delete()
                    if (src.renameTo(outputFile)) {
                        true
                    } else {
                        src.copyTo(outputFile, overwrite = true).exists()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "mergeSegments single-file transfer failed", e)
                false
            }
        }

        // 1. Probe the first segment to configure the target muxer tracks
        val firstProbe = MediaExtractor()
        try {
            firstProbe.setDataSource(validFiles[0].absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read first segment datasource: ${validFiles[0].name}", e)
            return false
        }

        val trackCount = firstProbe.trackCount
        if (trackCount == 0) {
            Log.e(TAG, "First segment contains 0 tracks.")
            firstProbe.release()
            return false
        }

        val firstFormats = (0 until trackCount).map { firstProbe.getTrackFormat(it) }
        val videoTrackIdx = (0 until trackCount).firstOrNull {
            firstFormats[it].getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: 0

        val baseWidth = if (firstFormats[videoTrackIdx].containsKey(MediaFormat.KEY_WIDTH)) {
            firstFormats[videoTrackIdx].getInteger(MediaFormat.KEY_WIDTH)
        } else -1
        val baseHeight = if (firstFormats[videoTrackIdx].containsKey(MediaFormat.KEY_HEIGHT)) {
            firstFormats[videoTrackIdx].getInteger(MediaFormat.KEY_HEIGHT)
        } else -1

        firstProbe.release()

        // 2. Validate resolution consistency across all segments
        for (i in 1 until validFiles.size) {
            val probe = MediaExtractor()
            try {
                probe.setDataSource(validFiles[i].absolutePath)
                for (t in 0 until probe.trackCount) {
                    val fmt = probe.getTrackFormat(t)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                        val w = if (fmt.containsKey(MediaFormat.KEY_WIDTH)) fmt.getInteger(MediaFormat.KEY_WIDTH) else -1
                        val h = if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) fmt.getInteger(MediaFormat.KEY_HEIGHT) else -1
                        if (w != baseWidth || h != baseHeight) {
                            Log.w(TAG, "Segment $i dimension mismatch (${w}x${h} vs base ${baseWidth}x${baseHeight}). Aborting merge to prevent container corruption.")
                            return false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not probe segment $i: ${e.message}")
            } finally {
                probe.release()
            }
        }

        if (outputFile.exists()) {
            outputFile.delete()
        }

        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMapping = firstFormats.map { muxer.addTrack(it) }

            // Apply orientation hint if present
            try {
                if (firstFormats[videoTrackIdx].containsKey(MediaFormat.KEY_ROTATION)) {
                    val rot = firstFormats[videoTrackIdx].getInteger(MediaFormat.KEY_ROTATION)
                    muxer.setOrientationHint(rot)
                }
            } catch (_: Exception) {}

            muxer.start()
            muxerStarted = true

            var currentBaseOffsetUs = 0L
            val lastPtsWritten = LongArray(trackCount) { 0L }

            validFiles.forEachIndexed { fileIndex, file ->
                var segmentMaxPtsUs = 0L
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    val activeTrackIndices = mutableListOf<Int>()
                    for (t in 0 until minOf(trackCount, extractor.trackCount)) {
                        extractor.selectTrack(t)
                        activeTrackIndices.add(t)
                    }

                    val trackFirstPts = LongArray(extractor.trackCount) { Long.MIN_VALUE }

                    while (true) {
                        val trackIndex = extractor.sampleTrackIndex
                        if (trackIndex < 0 || trackIndex >= trackCount) break

                        val dstTrack = trackMapping[trackIndex]
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break

                        val rawPts = extractor.sampleTime
                        if (trackFirstPts[trackIndex] == Long.MIN_VALUE) {
                            trackFirstPts[trackIndex] = rawPts.coerceAtLeast(0L)
                        }

                        val relPts = (rawPts - trackFirstPts[trackIndex]).coerceAtLeast(0L)
                        val candidatePts = relPts + currentBaseOffsetUs

                        // Strictly ensure non-decreasing timestamps for MediaMuxer
                        val finalPts = if (candidatePts <= lastPtsWritten[trackIndex]) {
                            lastPtsWritten[trackIndex] + 1000L
                        } else {
                            candidatePts
                        }
                        lastPtsWritten[trackIndex] = finalPts

                        bufferInfo.presentationTimeUs = finalPts
                        bufferInfo.flags = extractor.sampleFlags

                        if (bufferInfo.size > 0) {
                            buffer.position(0)
                            buffer.limit(bufferInfo.size)
                            muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                            if (finalPts > segmentMaxPtsUs) {
                                segmentMaxPtsUs = finalPts
                            }
                        }
                        extractor.advance()
                    }
                } finally {
                    extractor.release()
                }

                // Advance base timestamp for next segment
                currentBaseOffsetUs = if (segmentMaxPtsUs > currentBaseOffsetUs) {
                    segmentMaxPtsUs + 33333L // ~33ms frame interval gap
                } else {
                    currentBaseOffsetUs + 180_000_000L // 3 min fallback
                }

                val progress = (fileIndex + 1).toFloat() / validFiles.size
                progressCallback(progress)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "mergeSegments failed: ${e.message}", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return false
        } finally {
            if (muxer != null && muxerStarted) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Muxer stop exception: ${e.message}")
                }
                try {
                    muxer.release()
                } catch (_: Exception) {}
            }
        }
    }
}

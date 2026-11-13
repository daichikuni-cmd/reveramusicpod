package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.player.MonoAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MonoAudioProcessorTest {

    @Test
    fun `MonoAudioProcessor averages left and right channels when mono is enabled`() {
        val processor = MonoAudioProcessor()
        processor.isMonoEnabled = true

        val inputFormat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(inputFormat)
        assertEquals(inputFormat, outputFormat)

        val inputBuffer = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        // Frame 1: Left = 1000, Right = 3000 -> Mono = 2000
        inputBuffer.putShort(1000.toShort())
        inputBuffer.putShort(3000.toShort())
        // Frame 2: Left = -2000, Right = 4000 -> Mono = 1000
        inputBuffer.putShort((-2000).toShort())
        inputBuffer.putShort(4000.toShort())
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertEquals(8, outputBuffer.remaining())
        val outLeft1 = outputBuffer.short
        val outRight1 = outputBuffer.short
        val outLeft2 = outputBuffer.short
        val outRight2 = outputBuffer.short

        assertEquals(2000.toShort(), outLeft1)
        assertEquals(2000.toShort(), outRight1)
        assertEquals(1000.toShort(), outLeft2)
        assertEquals(1000.toShort(), outRight2)
    }

    @Test
    fun `MonoAudioProcessor passes through stereo audio when mono is disabled`() {
        val processor = MonoAudioProcessor()
        processor.isMonoEnabled = false

        val inputFormat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(inputFormat)

        val inputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        inputBuffer.putShort(1000.toShort())
        inputBuffer.putShort(3000.toShort())
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertEquals(4, outputBuffer.remaining())
        assertEquals(1000.toShort(), outputBuffer.short)
        assertEquals(3000.toShort(), outputBuffer.short)
    }
}

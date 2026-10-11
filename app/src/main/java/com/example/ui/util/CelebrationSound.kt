package com.example.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.R

/** Plays the short celebration chime, but never when the phone is on silent or vibrate. */
object CelebrationSound {

    fun play(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        try {
            val player = MediaPlayer.create(
                context,
                R.raw.celebration_chime,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                audio.generateAudioSessionId()
            ) ?: return
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (e: Exception) {
            // A sound is a nicety; never let it break the celebration.
        }
    }
}

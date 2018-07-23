package com.example.niamhtohill.sbvideosb

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import java.io.File


//possible media types of the source
enum class SourceType{ local_audio, local_video, http_audio, http_video, playlist;}

//condition of the player
data class PlayerState(var window: Int = 0, var position:Long=0, var whenReady: Boolean = true, var source:SourceType = SourceType.local_video)

//player with default options that can be attached to the PlayerView
class PlayerHolder(val context: Context, val playerView:PlayerView, val playerState: PlayerState, val file: File) {

    private val player:ExoPlayer

    init {
        player = ExoPlayerFactory.newSimpleInstance(
                //renders audio, video, text(subtitles) content
                DefaultRenderersFactory(context),
                //choose best media track from available sources
                DefaultTrackSelector(),
                //manage buffering and loading data over network
                DefaultLoadControl()).also {
            //binds to the view
            playerView.player = it
        }
    }

    fun start(){
        //load Media
        player.prepare(buildMediaSource(Uri.fromFile(file)))
        //saving state to release player resources if backgrounded
        with(playerState){
            player.playWhenReady =true
            player.seekTo(window,position)
        }
    }

    //determines the media source to set on the player
    private fun buildMediaSource(uri:Uri):ExtractorMediaSource{
        return ExtractorMediaSource.Factory(DefaultDataSourceFactory(context, "videoapp")).createMediaSource(uri)
    }

    fun stop(){
        //saving state of player to release player resources if app is backgrounded
        with(player){
            with(playerState){
                position = currentPosition
                window=currentWindowIndex
                whenReady =playWhenReady
            }
        }
        player.stop(true)
    }
    fun release(){
        player.release()
    }

}
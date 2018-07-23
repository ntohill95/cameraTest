package com.example.niamhtohill.camerasb

import android.app.Activity
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import com.example.niamhtohill.sbvideosb.PlayerHolder
import com.example.niamhtohill.sbvideosb.PlayerState
import kotlinx.android.synthetic.main.play_video.*
import java.io.File

class PlaybackActivity:AppCompatActivity(){

    lateinit var playerHolder:PlayerHolder
    var state = PlayerState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.play_video)

        var bundle = intent.extras
        var file = bundle.get("FILENAME")
        println("*****FILENAME***** $file")
        playerHolder = PlayerHolder(this, edit_video_player, state, file as File)
    }

    override fun onStart() {
        super.onStart()
        playerHolder.start()
    }

    override fun onStop() {
        super.onStop()
        playerHolder.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerHolder.release()
    }
}
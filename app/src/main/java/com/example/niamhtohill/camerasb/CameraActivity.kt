package com.example.niamhtohill.camerasb

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.example.niamhtohill.camerasb.ui.camera.CameraFragment


class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)
        val cameraFragment = CameraFragment()
        if(null == savedInstanceState){
            supportFragmentManager.beginTransaction().replace(R.id.container, cameraFragment.newInstance()).commit()

        }
    }


}

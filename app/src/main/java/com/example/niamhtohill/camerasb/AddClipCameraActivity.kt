package com.example.niamhtohill.camerasb

import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import com.example.niamhtohill.camerasb.ui.camera.CameraFragment

class AddClipCameraActivity:AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)
        val cameraFragment = CameraFragment()
        val intent= intent.extras
        val projectId = intent.get("currentProjectID") as Long
        val bundle = Bundle()
        bundle.putLong("projectID", projectId)
        cameraFragment.arguments = bundle

        if(null == savedInstanceState){
            supportFragmentManager.beginTransaction().replace(R.id.container, cameraFragment).commit()
        }

    }
}
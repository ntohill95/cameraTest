package com.example.niamhtohill.camerasb

import android.app.Activity
import android.os.Bundle
import android.widget.TableRow
import android.widget.TextView
import kotlinx.android.synthetic.main.saved_videos.*
import java.io.File

class SavedVideos:Activity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.saved_videos)

        val files = getFiles(filesDir)
        for(file in files){
            val tableRow = TableRow(this)
            val videoTitleTV = TextView(this)
            videoTitleTV.text = file.name
            videoTitleTV.setPadding(10,10,0,0)
            videoTitleTV.textSize = 20f
            videoTitleTV.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,TableRow.LayoutParams.WRAP_CONTENT)
            tableRow.addView(videoTitleTV)
            video_table.addView(tableRow)
        }
    }

    private fun getFiles(parentDir :File): List<File>{
        val inFiles = ArrayList<File>()
        val files = parentDir.listFiles()
        for(file in files){
            if(file.isDirectory){
                inFiles.add(file)
            }else{
                if(file.name.endsWith(".mp4")){
                    inFiles.add(file)
                }
            }
        }
        return inFiles
    }

}
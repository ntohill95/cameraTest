package com.example.niamhtohill.camerasb

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.view.Gravity
import android.support.v7.app.AlertDialog
import android.view.ViewGroup
import android.widget.*
import kotlinx.android.synthetic.main.saved_videos.*
import java.io.File




class SavedVideos:Activity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.saved_videos)

        val files = getFiles(filesDir)
        for(file in files){
            val tableRow = TableRow(this)
            tableRow.setBackgroundResource(R.drawable.table_row_shape)
            val videoTitleTV = TextView(this)
            videoTitleTV.text = (file.name).replace(".mp4",  "")
            videoTitleTV.setPadding(20,20,10,0)
            videoTitleTV.setTextColor(Color.BLACK)
            videoTitleTV.textSize = 20f
            videoTitleTV.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,TableRow.LayoutParams.WRAP_CONTENT)
            var deleteButton = Button(this)
            deleteButton.text = "Delete"
            deleteButton.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            deleteButton.setPadding(5,5,5,5)
            deleteButton.gravity = Gravity.CENTER

            var renameButton = Button(this)
            renameButton.text = "Rename"
            renameButton.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            renameButton.setPadding(5,5,5,5)
            renameButton.gravity = Gravity.CENTER

            tableRow.addView(videoTitleTV)
            tableRow.addView(deleteButton)
            tableRow.addView(renameButton)
            tableRow.setOnClickListener {
                var intent = Intent(this, PlaybackActivity::class.java)
                intent.putExtra("FILENAME", file)
                startActivity(intent)
            }
            video_table.addView(tableRow)
            deleteButton.setOnClickListener{

                val alertDialog = AlertDialog.Builder(this)
                alertDialog.setTitle("Alert")
                alertDialog.setMessage("Are you sure you want to delete this video?")
                alertDialog.setPositiveButton("Yes"){ dialog, which ->  
                    file.delete()
                    video_table.removeView(tableRow)
                }
                alertDialog.setNegativeButton("No"){dialog, which ->
                    Toast.makeText(this, "Delete Canceled", Toast.LENGTH_SHORT).show()
                }
                alertDialog.show()
            }
            renameButton.setOnClickListener(){
                val alertDialog = AlertDialog.Builder(this)
                alertDialog.setTitle("Change Video Title")
                alertDialog.setMessage("Enter the new title for the video")
                val editText = EditText(this)
                editText.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                alertDialog.setView(editText)
                alertDialog.setPositiveButton("Done"){dialog, which ->
                    if(editText.text.toString() == ""){
                        Toast.makeText(this, "You did not enter a title", Toast.LENGTH_LONG).show()
                    }else{
                        val newTitle = File(editText.text.toString() + ".mp4")
                        val changedSuccessfully = file.renameTo(newTitle)
                        /**Checking if the file was successfully renamed*/
                        if(changedSuccessfully){
                            println("WOOOOOOOOOOOOOOOOOOOOO")
                        }else{
                            println("NOOOOOOOOOOOO")

                        }
                        videoTitleTV.text = (file.name).replace(".mp4", "")

                    }
                }
                alertDialog.setNegativeButton("No"){dialog, which ->
                    Toast.makeText(this, "Rename Canceled", Toast.LENGTH_SHORT).show()
                }
                alertDialog.show()
            }
        }
    }

    private fun getFiles(parentDir :File): List<File>{
        val inFiles = ArrayList<File>()
        val files = parentDir.listFiles()
        for(file in files){
            //was saving the instant run file so "ends with" used to filter it from being stored
            if(file.isDirectory && file.name.endsWith(".mp4")){
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
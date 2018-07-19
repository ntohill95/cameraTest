package com.example.niamhtohill.camerasb.ui.camera

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AutoFitTextureView(context: Context?, attributeSet: AttributeSet?) : TextureView(context, attributeSet) {

    var ratioWidth = 0
    var ratioHeight = 0

    fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative")
        }
        ratioWidth = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (0 == ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(width, height)
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height)

            } else {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth)
            }
        }
    }
}
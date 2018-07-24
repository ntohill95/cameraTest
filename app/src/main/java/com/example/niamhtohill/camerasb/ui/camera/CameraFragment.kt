package com.example.niamhtohill.camerasb.ui.camera

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.support.v4.app.Fragment
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.opengl.Visibility
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.support.v4.app.ActivityCompat

import android.support.v4.app.DialogFragment
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.Toast
import com.example.niamhtohill.camerasb.R
import com.example.niamhtohill.camerasb.SavedVideos
import kotlinx.android.synthetic.main.camera_fragment.*
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

private val VIDEO_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
private val REQUEST_VIDEO_PERMISSIONS = 1

open class CameraFragment : Fragment(), View.OnClickListener {

    private val TAG = "CameraFragment"
    var isRecodingVideo: Boolean = false
    private var backgroundHandler : Handler? =null
    private var backgroundHandlerThread: HandlerThread? = null
    private val FRAGMENT_DIALOG = "dialog"
    private var mediaRecorder : MediaRecorder? = null
    private var previewSize:Size? = null
    private var previewBuilder:CaptureRequest.Builder? = null
    private var previewSession:CameraCaptureSession? = null
    private var mNextVideoAbsolutePath:String?= null
    private var videoSize:Size? = null
    private var sensorOrientation:Int? = null
    private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
    private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
    private var cameraIdButton = 0
    var projectID: Long = 9999

    private val DEFAULT_ORIENTATIONS = object :SparseIntArray(){
        init {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90,0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
    }
    private val INVERSE_ORIENTATIONS = object :SparseIntArray(){
        init{
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
        }
    }

    fun newInstance():CameraFragment{
        return CameraFragment()
    }

    /**handle lifecycle events of TextureListener **/
    private val surfaceTextureListener = object:TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, width: Int, height: Int) {
            configureTransform(width, height)
        }
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {

        }
        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean {
            return true
        }
        override fun onSurfaceTextureAvailable(p0: SurfaceTexture?, width: Int, height: Int) {
            openCamera(width, height)
        }
    }

    /**to prevent the app exiting before closing the camera **/
    val cameraOpenCloseLock=  Semaphore(Int.MAX_VALUE)
    var mCameraDevice:CameraDevice? = null

    /**called if the state of the camera changes **/
    private val stateCallback = object: CameraDevice.StateCallback(){
        override fun onOpened(cameraDevice: CameraDevice){
            mCameraDevice = cameraDevice
            startPreview()
            cameraOpenCloseLock.release()
            if(null != texture){
                configureTransform(texture.width, texture.height)
            }
        }
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }
        override fun onError(cameraDevice: CameraDevice, p1: Int) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            val activity = activity
            if(null != activity){
                activity.finish()
            }
        }
    }

    /**here we are choosing a video with a 3x4 aspect ratio - can alter for 1600x900 ratio
    mediaRecorder can't handle larger than 1080p - is this an issue? **/
    private fun chooseVideoSize(choices:Array<Size>):Size{
        for (size in choices){
            if(size.width == size.height * 4/3 && size.width <= 1080){
                return size
            }
        }
        Log.e(TAG, "Couldn't find suitable video size")
        return choices[choices.size-1]
    }

    /**choose optimal size based on support of camera **/
    private fun chooseOptimalSize(choices: Array<Size>, width:Int, height:Int, aspectRatio:Size):Size{
        val bigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for(option in choices){
            if((option.height == option.width * h/w &&
                            option.width >= width && option.height >= height)){
                bigEnough.add(option)
            }
        }
        return if(bigEnough.size > 0){
            Collections.min(bigEnough, CompareSizesByArea())
        }else{
            Log.e(TAG, "Couldn't find any suitable preview size")
            choices[0]
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val bundle = this.arguments
        if(bundle != null) {
            projectID = bundle.getLong("projectID")
        } else {
            projectID = System.currentTimeMillis()
        }
        println("*********" + projectID.toString() +"**********")
        return inflater.inflate(R.layout.camera_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        video.setOnClickListener(this)
        texture.setOnClickListener(this)
        savedVideos.setOnClickListener(this)
        changeCamera.setOnClickListener(this)
    }

    override fun onResume(){
        super.onResume()
        startBackgroundThread()
        if(texture.isAvailable){
            openCamera(texture.width, texture.height)
        } else{
            texture.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause(){
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /**if the button has been clicked on the record screen determine whether the video has just begun or ended **/
    override fun onClick(view: View?) {
        when(view!!.id){
            R.id.video -> {
                println("The PLAY?STOP button was pressed")
                if(isRecodingVideo){
                    stopRecordingVideo()
                    //show the change camera button when not recording - prevents being able to change while filming (Same as SB)
                    changeCamera.visibility = View.VISIBLE
                }else{
                    startRecordingVideo()
                    //hide the change camera button
                    changeCamera.visibility = View.GONE
                }
            }
            R.id.texture -> {
                println("The TEXTUREVIEW is there")
            }
            R.id.savedVideos -> {
                println("***********SAVE PRESSED")
                val intent = Intent(context, SavedVideos::class.java)

                startActivity(intent)

            }
            R.id.changeCamera -> {
                println("***********Change Camera")
                when(cameraIdButton){
                    0 -> cameraIdButton =1
                    1 -> cameraIdButton =0
                }
                closeCamera()
                stopBackgroundThread()
                startBackgroundThread()
                if(texture.isAvailable){
                    openCamera(texture.width, texture.height)
                }else{
                    texture.surfaceTextureListener
                }
            }
        }
    }

    /** create a background thread to run the camera on - too much time if not available **/
    private fun startBackgroundThread(){
        backgroundHandlerThread = HandlerThread("CameraBackground")
        backgroundHandlerThread!!.start()
        backgroundHandler = Handler(backgroundHandlerThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread!!.quitSafely()
        try {
            backgroundHandlerThread!!.join()
            backgroundHandlerThread = null
            backgroundHandler = null
        } catch (e:InterruptedException) {
            e.printStackTrace()
        }
    }

    /** determine whether the app needs to request access to camera etc **/
    private fun shouldShowRequestPermission(permissions: Array<String>):Boolean{
        for(permission in permissions){
            if(this.shouldShowRequestPermissionRationale(permission)){
                return true
            }
        }
        return false
    }

    /** Requests permissions needed for recording video.*/
    private fun requestVideoPermissions() {
        if (shouldShowRequestPermission(VIDEO_PERMISSIONS)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        }else {
            requestPermissions(VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS)
        }
    }

    /** Creates a fragment dialog box on top of activity window */
   class ConfirmationDialog:DialogFragment() {
        override fun onCreateDialog(savedInstanceState:Bundle):Dialog {
            val parent = parentFragment
            return AlertDialog.Builder(activity)
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, object: DialogInterface.OnClickListener {
                        override fun onClick(dialog:DialogInterface, which:Int) {
                            requestPermissions(VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS)
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            object:DialogInterface.OnClickListener {
                                override fun onClick(dialog:DialogInterface, which:Int) {
                                    parent!!.activity!!.finish()
                                }
                            }).create()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,permissions:Array<String>, grantResults:IntArray){
        Log.d(TAG, "onRequestPermissionsResult")
        if(requestCode == REQUEST_VIDEO_PERMISSIONS){
            if(grantResults.size == VIDEO_PERMISSIONS.size){
                for(result in grantResults){
                    if(result != PackageManager.PERMISSION_GRANTED){
                        ErrorDialog.newInstance(getString(R.string.permission_request)).show(childFragmentManager, FRAGMENT_DIALOG)
                        break
                    }
                }
            }else{
                ErrorDialog.newInstance(getString(R.string.permission_request)).show(childFragmentManager, FRAGMENT_DIALOG)
            }
        }else{
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    class ErrorDialog:DialogFragment(){
        override fun onCreateDialog(savedInstanceState: Bundle?):Dialog{
            val activity = activity
            return AlertDialog.Builder(activity).setMessage(arguments!!.getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, object : DialogInterface.OnClickListener {
                        override fun onClick(dialogInterface: DialogInterface, i:Int){
                            activity!!.finish()
                        }
                    }).create()
        }companion object {
            const val ARG_MESSAGE = "message"
            fun newInstance(message:String):ErrorDialog{
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.arguments = args
                return dialog
            }
        }
    }

    private fun hasPermissionsGranted(permissions:Array<String>):Boolean {
        for (permission in permissions) {
            if ((ActivityCompat.checkSelfPermission(this.activity!!, permission) != PackageManager.PERMISSION_GRANTED)) {
                return false
            }
        }
        return true
    }
    /** the suppressed permissions needs checked
     result listened to by state call back*/
    /**BUG HERE - crashes on initial install - first permission.
     * Occurring bug on github issues too - https://github.com/googlesamples/android-Camera2Basic/issues/39*/

    @SuppressWarnings("MissingPermission")
    private fun openCamera(width:Int, height: Int){
        if(!hasPermissionsGranted(VIDEO_PERMISSIONS)){
            requestVideoPermissions()
            return
        }
        val activity = activity
        if(null == activity || activity.isFinishing){
            return
        }
        configureTransform(width, height)
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try{
            Log.d(TAG, "tryAcquire")
            if(!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)){
                throw RuntimeException("Time out while waiting to lock camera opening")
            }
            val cameraId = manager.cameraIdList[cameraIdButton]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if(map ==null){
                throw RuntimeException("Cannot get available preview/video size")
            }
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height, videoSize!!)
            val orientation = resources.configuration.orientation
            if(orientation == Configuration.ORIENTATION_LANDSCAPE){
                texture.setAspectRatio(previewSize!!.width, previewSize!!.height)
            }else{
                texture.setAspectRatio(previewSize!!.height, previewSize!!.width)
            }
            configureTransform(width, height)
            mediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        }catch (e:CameraAccessException){
            Toast.makeText(activity, "Cannot access camera", Toast.LENGTH_LONG).show()
            activity.finish()
        }catch (e:NullPointerException){
            ErrorDialog.newInstance(getString(R.string.camera_error)).show(childFragmentManager, FRAGMENT_DIALOG)
        }catch (e:InterruptedException){
            throw RuntimeException("Interrupted while trying to lock camera opening")
        }
    }

    private fun closeCamera(){
        try{
            cameraOpenCloseLock.acquire()
            closePreviewSession()
            /** THIS DOESN'T SEEM RIGHT */
            if(null != mCameraDevice){
                mCameraDevice!!.close()
                mCameraDevice=null
            }
            //val mMediaRecorder = mediaRecorder
            if(null!=mediaRecorder){
                mediaRecorder!!.release()
                mediaRecorder = null
            }
        }catch (e:InterruptedException){
            throw RuntimeException("Interrupted while trying to lock camera opening")
        }
    }
    /** start the camera preview */
    private fun startPreview(){
        if(null == mCameraDevice || !texture.isAvailable || null == previewSize){
            return
        }
        try{
            closePreviewSession()
            val surfaceTexture = texture.surfaceTexture
            assert(surfaceTexture != null)
            surfaceTexture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            previewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            var previewSurface = Surface(surfaceTexture)

            previewBuilder!!.addTarget(previewSurface)
            mCameraDevice!!.createCaptureSession(Collections.singletonList(previewSurface), object : CameraCaptureSession.StateCallback(){
                override fun onConfigured(session: CameraCaptureSession){
                    previewSession = session
                    updatePreview()
                }
                override fun onConfigureFailed(session: CameraCaptureSession){
                    val activity = activity
                    if(null!=activity){
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }, backgroundHandler)
        } catch (e:CameraAccessException){
            e.printStackTrace()
        }
    }

    /** update preview of camera */
    private fun updatePreview() {
        if (null == mCameraDevice)
        { return }
        try
        { setUpCaptureRequestBuilder(previewBuilder!!)
            val thread = HandlerThread("CameraPreview")
            thread.start()
            previewSession!!.setRepeatingRequest(previewBuilder!!.build(), null, backgroundHandler)
        } catch (e:CameraAccessException) {
            e.printStackTrace()
        }
    }
    private fun setUpCaptureRequestBuilder(builder:CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    private fun configureTransform(viewWidth:Int, viewHeight:Int) {
        val activity = activity
        if (null == texture || null == previewSize || null == activity)
        { return }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0.toFloat(), 0.toFloat(), previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation)
        {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize!!.height,
                    viewWidth.toFloat() / previewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        }
        texture.setTransform(matrix)
    }

    private fun setUpMediaRecorder(){
        val activity = activity
        if(null==activity){
            return
        }
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        if(mNextVideoAbsolutePath == null || mNextVideoAbsolutePath!!.isEmpty()){
            mNextVideoAbsolutePath = getVideoFilePath(activity, projectID)
        }
        mediaRecorder!!.setOutputFile(mNextVideoAbsolutePath)
        mediaRecorder!!.setVideoEncodingBitRate(10000000)
        mediaRecorder!!.setVideoFrameRate(30)
        mediaRecorder!!.setVideoSize(videoSize!!.width, videoSize!!.height)
        mediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        val rotation = activity.windowManager.defaultDisplay.rotation
        when(sensorOrientation){
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mediaRecorder!!.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mediaRecorder!!.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }
        mediaRecorder!!.prepare()
    }

    private fun getVideoFilePath(context: Context, projectIDToSave:Long):String{
        val directory = context.filesDir
        var directoryString:String
        if (directory==null){
            directoryString = ""
        }else{
            directoryString = directory.absolutePath + "/"
        }
        directoryString += projectIDToSave.toString() + "/"
        val newDirectory = File(directoryString)
        newDirectory.mkdir()
        val videoPath = newDirectory.absolutePath + "/" + System.currentTimeMillis() +".mp4"
        return (videoPath)
    }

    private fun startRecordingVideo(){
        if(null==mCameraDevice || !texture.isAvailable || null == previewSize){
            return
        }
        try{
            timer.base = SystemClock.elapsedRealtime()
            timer.start()
            closePreviewSession()
            setUpMediaRecorder()
            val surfaceTexture = texture.surfaceTexture
            assert(surfaceTexture != null)
            surfaceTexture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            previewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces = arrayListOf<Surface>()
            /** Set up surface for camera preview*/
            val previewSurface = Surface(surfaceTexture)
            surfaces.add(previewSurface)
            previewBuilder!!.addTarget(previewSurface)
            /** Set up surface for MediaRecorder*/
            val recorderSurface = mediaRecorder!!.surface
            surfaces.add(recorderSurface)
            previewBuilder!!.addTarget(recorderSurface)
            /**Start a Capture Session - once it starts update the UI and start recording*/
            mCameraDevice!!.createCaptureSession(surfaces, object :CameraCaptureSession.StateCallback(){
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession){
                    previewSession = cameraCaptureSession
                    updatePreview()
                    activity!!.runOnUiThread(object:Runnable {
                        override fun run() {
                            video.setImageResource(R.drawable.stop)
                            isRecodingVideo = true
                            mediaRecorder!!.start()

                            /** Need to display the time every second - reset to 0 when the video is stopped*/
                        }
                    })
                }
                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession){
                    val activity = activity
                    if(null !=activity){
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT)
                    }
                }
            }, backgroundHandler)
        }catch (e:CameraAccessException){
            e.printStackTrace()
        }catch (e:IOException){
            e.printStackTrace()
        }
    }
    private fun closePreviewSession(){
        if(previewSession != null){
            previewSession!!.close()
            previewSession = null
        }
    }

    private fun stopRecordingVideo(){
        try{
            previewSession!!.stopRepeating()
        }catch (e:CameraAccessException){
            e.printStackTrace()
        }

        /**UI*/
        timer.stop()
        timer.base = SystemClock.elapsedRealtime()
        isRecodingVideo = false
        video.setImageResource(R.drawable.play)
        mediaRecorder!!.stop()
        mediaRecorder!!.reset()
        val activity = activity
        if(null != activity){
            Toast.makeText(activity, "Video saved: $mNextVideoAbsolutePath", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Video saved: $mNextVideoAbsolutePath")
        }
        mNextVideoAbsolutePath = null
        startPreview()
    }
    /**comparing the sizes based on area*/
    internal class CompareSizesByArea:Comparator<Size> {
        override fun compare(lhs:Size, rhs:Size):Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum((lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height))
        }
    }



}


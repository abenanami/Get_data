package com.example.getdata


//import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import com.google.api.services.drive.model.File
import com.google.common.io.ByteStreams.toByteArray
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var googleSignInClient: GoogleSignInClient? = null
    private var drive: Drive? = null

    private val fileName = "data.txt"

    private var sensorManager: SensorManager? = null
    private var textView: TextView? = null

    //google drive login
    private val driveContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                // ログイン成功
                connectDrive(it.data!!)
            } else {
                // ログイン失敗orキャンセル
            }
        }

    private fun loginToGoogle() {
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)
        val intent = googleSignInClient?.signInIntent
        driveContent.launch(intent)
    }

    private fun connectDrive(intent: Intent) {
        GoogleSignIn.getSignedInAccountFromIntent(intent)
            .addOnSuccessListener {
                //ログイン成功
                val credential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccount = it.account
                drive = Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory(),
                    credential
                ).setApplicationName(getString(R.string.app_name)).build()
            }
            .addOnFailureListener {
                //ログイン失敗
                Log.w("Sign in failed", it.toString())
            }
    }

    //ファイルIDの生成
    fun createDriveIdTask(
        parents: List<String>,
        mimeType: String,
        fileName: String
    ): Task<String> {
        val taskCompletionSource = TaskCompletionSource<String>()
        val metadata = File().setName(fileName)
            .setParents(parents)
            .setMimeType(mimeType)
            .setName(fileName)
        val file =
            drive?.files()?.create(metadata)?.execute() ?: throw IOException("Result is null")
        taskCompletionSource.setResult(file.id)
        return taskCompletionSource.task
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loginToGoogle()

        // Get an instance of the SensorManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Get an instance of the TextView
        textView = findViewById(R.id.text_view)
    }

    override fun onResume() {
        super.onResume()
        //ボタン
        val textMessage = findViewById<TextView>(R.id.message)
        val startbutton = findViewById<Button>(R.id.start)

        //記録中
        startbutton.setOnClickListener {
            textMessage.setText(R.string.message_start)
            // Listenerの登録
            val accel = sensorManager!!.getDefaultSensor(
                Sensor.TYPE_ACCELEROMETER
            )
            sensorManager!!.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
            //sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST);
            //sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            //sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }
    }

    override fun onPause() {
        super.onPause()

        // Listenerを解除
        sensorManager!!.unregisterListener(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SdCardPath")
    override fun onSensorChanged(event: SensorEvent) {

        val sensorX: Float
        val sensorY: Float
        val sensorZ: Float

        //ボタン
        val textMessage = findViewById<TextView>(R.id.message)
        val resetbutton = findViewById<Button>(R.id.reset)
        val buttonRead: Button = findViewById(R.id.data)
        val stopbutton = findViewById<Button>(R.id.stop)
        val uploadbutton = findViewById<Button>(R.id.upload)

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            sensorX = event.values[0]
            sensorY = event.values[1]
            sensorZ = event.values[2]
            val strTmp = " ${getNowDate(event)} X: $sensorX Y: $sensorY Z: $sensorZ,"
            textView!!.text = strTmp
            savefile(strTmp)
        }

        //記録停止中
        stopbutton.setOnClickListener {
            textMessage.setText(R.string.message_stop)
            // Listenerを解除
            sensorManager!!.unregisterListener(this)
        }

        //ファイルを削除
        resetbutton.setOnClickListener {
            val path = Paths.get("/data/data/com.example.getdata/files/data.txt")

            try {
                Files.deleteIfExists(path)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        //読み出し
        buttonRead.setOnClickListener {
            val str = readFile()
            if (str != null) {
                textMessage.text = str
            } else {
                textMessage.setText(R.string.read_error)
            }
        }

        //アップロード
        uploadbutton.setOnClickListener {
            val str = readFile()
            val byteArray = str?.toByteArray()
            if (byteArray != null) {
                backupToDrive("data.txt", "text/plain", byteArray)
            }
        }
    }

    // ファイルを保存
    private fun savefile(str: String) {

        try {
            openFileOutput(fileName, MODE_APPEND).use { fileOutputstream ->
                fileOutputstream.write(
                    str.toByteArray()
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    //ファイルIDの指定、保存、更新
    fun saveFile(
        fileId: String,
        fileName: String,
        contentType: String,
        data: ByteArray
    ): Task<Unit> {
        val taskCompletionSource = TaskCompletionSource<Unit>()
        val metadata = File().setName(fileName)
        val contentStream = ByteArrayContent(contentType, data)
        drive?.files()?.update(fileId, metadata, contentStream)?.execute()
        taskCompletionSource.setResult(null)
        return taskCompletionSource.task
    }

    //Driveにファイルをバックアップ
    fun backupToDrive(fileName: String, mimeType: String, fileContent: ByteArray) {
        val coroutineScope = CoroutineScope(Job() + Dispatchers.IO)
        coroutineScope.launch {
            createDriveIdTask(
                listOf("root"),
                mimeType,
                fileName
            ).addOnSuccessListener {
                // ファイルIDの生成成功
                val fileId = it
                saveFile(
                    fileId,
                    fileName,
                    mimeType,
                    fileContent
                ).addOnSuccessListener {
                    // バックアップ成功
                    Log.d("Success", "Backup succeeded")
                }.addOnFailureListener { exception ->
                    // バックアップ失敗
                    Log.w("Failure", exception.toString())
                }
            }.addOnFailureListener { exception ->
                // ファイルIDの生成失敗
                Log.w("Failure", exception.toString())
            }
        }
    }

    // ファイルを読み出し
    private fun readFile(): String? {
        var text: String? = null

        try {
            openFileInput(fileName).use { fileInputStream ->
                BufferedReader(
                    InputStreamReader(fileInputStream, StandardCharsets.UTF_8)
                ).use { reader ->
                    var lineBuffer: String?
                    while (reader.readLine().also { lineBuffer = it } != null) {
                        text = lineBuffer
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return text
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    companion object {
        private fun getNowDate(event: SensorEvent): String {
            val df: DateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
            df.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            val date = Date(System.currentTimeMillis())
            return df.format(date)
        }
    }
}
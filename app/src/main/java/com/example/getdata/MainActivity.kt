package com.example.getdata

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.Intent.EXTRA_EMAIL
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity(), SensorEventListener {

    private val fileName = "data.txt"

    private var sensorManager: SensorManager? = null
    private var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get an instance of the SensorManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Get an instance of the TextView
        textView = findViewById(R.id.text_view)

//        insertData
        val insertbutton = findViewById<Button>(R.id.insertData)
        insertbutton.setOnClickListener{
            sendCsv()
        }
    }
    // CSVを生成して送信用ダイアログを表示させる
    private fun sendCsv() {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, readFile())
//            putExtra(Intent.EXTRA_EMAIL, arrayOf("B21P002@akita-pu.ac.jp"))
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
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
    }

    // ファイルを保存
    private fun savefile(str: String) {
        val mediaDir = externalMediaDirs.firstOrNull()?.let{
            File(it, "").apply { mkdir() }
        }

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
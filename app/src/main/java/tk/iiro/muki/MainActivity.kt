package tk.iiro.muki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColor
import androidx.core.view.WindowCompat
import androidx.navigation.ui.AppBarConfiguration
import com.google.android.material.snackbar.Snackbar
import com.muki.core.MukiCupApi
import com.muki.core.MukiCupCallback
import com.muki.core.model.Action
import com.muki.core.model.DeviceInfo
import com.muki.core.model.ErrorCode
import com.muki.core.model.ImageProperties
import com.muki.core.util.ImageUtils
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import tk.iiro.muki.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        askForPermissions()

        val textOutput = findViewById<TextView>(R.id.textOutput)
        val api = MukiCupApi(applicationContext, object: MukiCupCallback {
            override fun onCupConnected() {
                showToast("Connected")
            }

            override fun onCupDisconnected() {
                showToast("Disconnected")
            }

            override fun onDeviceInfo(deviceInfo: DeviceInfo) {
                textOutput.text = (deviceInfo.toString())
            }

            override fun onImageCleared() {
                showToast("Image cleared")
            }

            override fun onImageSent() {
                showToast("Image sent")
            }

            override fun onError(action: Action?, errorCode: ErrorCode?) {
                showToast("Error: $errorCode on action: $action")
            }
        })

        val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                /*val image = uriToBitmap(uri)
                val mImage = ImageUtils.cropImage(image, Point(100, 0))
                val result = Bitmap.createBitmap(mImage)
                ImageUtils.convertImageToCupImage(result, ImageProperties.DEFAULT_CONTRACT)
                //val test = ImageUtils.cropImage(image, Point(0,0))*/
                cropImage(uri)
                //findViewById<ImageView>(R.id.imageView).setImageBitmap(result)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

        // Get values from settings
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        val mukiName = sharedPref.getString(getString(R.string.setting_key_mukiName), getString(R.string.muki_id))
        findViewById<EditText>(R.id.mukiName).setText(mukiName)

        // Set click actions for buttons
        val buttonGetInfo = findViewById<Button>(R.id.button_getInfo)
        buttonGetInfo.setOnClickListener {
            api.getDeviceInfo(cupId())
        }
        findViewById<Button>(R.id.photoPicker).setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.sendPhoto).setOnClickListener {
            val image = findViewById<ImageView>(R.id.imageView).drawable.toBitmap()
            api.sendImage(image, cupId())
        }
        findViewById<Button>(R.id.saveMukiName).setOnClickListener {
            saveMukiName()
            showToast("Muki name saved successfully")
        }
    }

    // Modified from https://hamzaasif-mobileml.medium.com/android-capturing-images-from-camera-or-gallery-as-bitmaps-kotlin-50e9dc3e7bd3
    private fun uriToBitmap(uri: Uri): Bitmap? {
        try {
            val descriptor = contentResolver.openFileDescriptor(uri, "r")
            val image = BitmapFactory.decodeFileDescriptor(descriptor!!.fileDescriptor)
            descriptor.close()
            return image
        } catch (e: IOException) {
            e.printStackTrace()
        }
       return null
    }

    private val cropLauncher =
        registerForActivityResult(CropImage()) {
            val uri = it ?: return@registerForActivityResult // this is the output Uri
            val image = uriToBitmap(uri)
            image?.let {
                val temp = Bitmap.createScaledBitmap(image, 176, 264, false)
                val result = temp.copy(temp.config, true)

                // Color mode is for images with multiple colors, BW mode is for 2-color images
                val colorMode = findViewById<Switch>(R.id.toggle_color).isChecked
                if (colorMode) {
                    ImageUtils.convertImageToCupImage(result, ImageProperties.DEFAULT_CONTRACT)
                } else {
                    convertToMuki(result)
                }

                findViewById<ImageView>(R.id.imageView).setImageBitmap(result)
            }
        }

    fun cupId(): String {
        val id = findViewById<EditText>(R.id.mukiName).text.toString()
        return "PAULIG_MUKI_${id}"
    }


    fun cropImage(source: Uri) {
        val destination = Uri.fromFile(File(cacheDir, "cropped"))
        cropLauncher.launch(Pair(source, destination))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                askForPermissions()
                true
            }
            R.id.action_help -> {
                val newHelp = Intent(this, HelpActivity::class.java)
                startActivity(newHelp)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun showToast(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    fun saveMukiName() {
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        val newName = findViewById<EditText>(R.id.mukiName).text.toString()
        with (sharedPref.edit()) {
            putString(getString(R.string.setting_key_mukiName), newName)
            apply()
        }
    }

    fun convertToMuki(image: Bitmap) {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val value = image.getPixel(x, y)
                val color = value.toColor()
                val newValue = (color.red() + color.green() + color.blue()) / 3
                if (newValue <= 0.5) {
                    image.setPixel(x, y, Color.BLACK)
                } else {
                    image.setPixel(x, y, Color.WHITE)
                }
            }
        }
    }

    fun askForPermissions() {
        println("Asking for permissions")
        val listOfPermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                println("New Android")
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                println("Old Android")
                arrayOf(
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }

        val hasPermissions = listOfPermissions.all {
            ActivityCompat.checkSelfPermission(applicationContext, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            requestPermissionLauncher.launch(listOfPermissions)
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a late init var in your onAttach() or onCreate() method.
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}


}
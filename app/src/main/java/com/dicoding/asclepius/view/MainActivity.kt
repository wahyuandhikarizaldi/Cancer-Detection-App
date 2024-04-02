package com.dicoding.asclepius.view

import android.content.Context
import android.content.Intent
import com.android.volley.toolbox.Volley
import com.android.volley.toolbox.JsonObjectRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.android.volley.RequestQueue
import com.dicoding.asclepius.R
import com.dicoding.asclepius.data.ItemImageSlider
import com.dicoding.asclepius.database.Note
import com.dicoding.asclepius.databinding.ActivityMainBinding
import com.dicoding.asclepius.helper.ImageClassifierHelper
import com.dicoding.asclepius.repository.NoteRepository
import org.tensorflow.lite.task.vision.classifier.Classifications
import java.text.NumberFormat
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var mNoteRepository: NoteRepository
    private lateinit var binding: ActivityMainBinding

    private var currentImageUri: Uri? = null

    private lateinit var imageClassifierHelper: ImageClassifierHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar4)
        toolbar.title = "Cancer Detection App"
        setSupportActionBar(toolbar)

        mNoteRepository = NoteRepository(application)

        binding.galleryButton.setOnClickListener { startGallery() }

        binding.analyzeButton.setOnClickListener {
            currentImageUri?.let {
                analyzeImage(it)
            } ?: run {
                showToast(getString(R.string.empty_image_warning))
            }
        }
        fetchNewsArticles(this)
    }

    private fun fetchNewsArticles(context: Context) {
        val queue: RequestQueue = Volley.newRequestQueue(context)
        val url = "https://newsapi.org/v2/top-headlines?q=cancer&category=health&language=en&apiKey=f279eac59f494e07a29e2ce491623846"

        val request = object : JsonObjectRequest(
            Method.GET, url, null,
            { response ->
                val newsList = mutableListOf<ItemImageSlider>()
                try {
                    val articles = response.getJSONArray("articles")
                    for (i in 0 until articles.length()) {
                        val article = articles.getJSONObject(i)
                        val title = article.optString("title")
                        if (title != "[Removed]") {
                            val imageUrl = article.optString("urlToImage")
                            val description = article.optString("description")
                            val linkUrl = article.optString("url")
                            val newsArticle = ItemImageSlider(title, description, imageUrl, linkUrl)
                            newsList.add(newsArticle)
                        }
                    }
                    setupViewPager(newsList)

                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "error while parsing the jsonObject/array",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            { error ->
                error.printStackTrace()
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["User-Agent"] = "Mozilla/5.0"
                return headers
            }
        }
        queue.add(request)
    }

    private fun setupViewPager(newsList: List<ItemImageSlider>) {
        val viewPagerAdapter = AutoImageSliderAdapter(this, newsList)
        binding.viewPager.adapter = viewPagerAdapter
        viewPagerAdapter.autoslide(binding.viewPager)

        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startGallery() {
        launcherGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private val launcherGallery = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            startUCrop(uri)
        } else {
            Log.d("Photo Picker", "No media selected")
        }
    }

    private fun startUCrop(uri: Uri) {
        val options = UCrop.Options()

        val tempFile = File.createTempFile("cropped_image", ".jpg", cacheDir)

        UCrop.of(uri, Uri.fromFile(tempFile))
            .withOptions(options)
            .start(this)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            resultUri?.let {
                currentImageUri = it
                showImage(it)
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(data!!)
            error?.let {
                Log.e("UCrop Error", "Error: $error")
            }
        }
    }

    private fun showImage(uri: Uri) {
        binding.previewImageView.setImageURI(null)
        binding.previewImageView.setImageURI(uri)
    }

    private fun analyzeImage(uri: Uri) {
        val timestamp: String = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())

        val imagePath = getPathFromUri(uri)
        Log.d("imagePath", imagePath)

        val cachePath = File(cacheDir, "cropped_image")
        if (cachePath.exists() && !cachePath.isDirectory) {
            cachePath.delete()
        }
        Log.d("cachePath", cachePath.toString())
        if (!cachePath.exists()) {
            cachePath.mkdir()
        }
        val uniqueImagePath = "$cachePath/$timestamp"

        val file = File(imagePath)
        file.copyTo(File(uniqueImagePath))

        val uniqueUri = Uri.fromFile(File(uniqueImagePath))

        imageClassifierHelper = ImageClassifierHelper(
            context = this,
            classifierListener = object : ImageClassifierHelper.ClassifierListener {
                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(results: List<Classifications>?, inferenceTime: Long) {
                    runOnUiThread {
                        results?.let { it ->
                            val displayResult = if (it.isNotEmpty() && it[0].categories.isNotEmpty()) {
                                val sortedCategories = it[0].categories.sortedByDescending { it?.score }
                                val topCategory = sortedCategories.firstOrNull()
                                topCategory?.let {
                                    "${it.label} " + NumberFormat.getPercentInstance().format(it.score).trim()
                                } ?: "No results"
                            } else {
                                "No results"
                            }

                            var actionCompleted = false

                            mNoteRepository.getNoteByTimestamp(timestamp).observe(this@MainActivity) { note ->
                                if (!actionCompleted) {
                                    if (note == null) {
                                        val newNote = Note().apply {
                                            this.timestamp = timestamp
                                            this.imageUrl = uniqueUri.toString() // Simpan path file lokal ke basis data
                                            this.result = displayResult
                                        }
                                        mNoteRepository.insert(newNote)
                                    } else {
                                        showToast(getString(R.string.mNoteNull))
                                    }

                                    actionCompleted = true
                                }
                            }
                            moveToResult(displayResult, timestamp)
                        }
                    }
                }
            }
        )
        imageClassifierHelper.classifyStaticImage(uri, this)
    }

    private fun getPathFromUri(uri: Uri): String {
        val filePath: String
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor == null) {
            filePath = uri.path.toString()
        } else {
            cursor.moveToFirst()
            val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            filePath = cursor.getString(index)
            cursor.close()
        }
        return filePath
    }
    
    private fun moveToResult(displayResult: String, timestamp: String) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_IMAGE_URI, currentImageUri.toString())
            putExtra(ResultActivity.EXTRA_RESULT, displayResult)
            putExtra(ResultActivity.EXTRA_TIMESTAMP, timestamp)
        }
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
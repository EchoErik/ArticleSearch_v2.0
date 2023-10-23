package com.codepath.articlesearch

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codepath.articlesearch.databinding.ActivityMainBinding
import com.codepath.asynchttpclient.AsyncHttpClient
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Headers
import org.json.JSONException

fun createJson() = Json {
    isLenient = true
    ignoreUnknownKeys = true
    useAlternativeNames = false
}

private const val TAG = "MainActivity/"
private const val SEARCH_API_KEY = BuildConfig.API_KEY
private const val ARTICLE_SEARCH_URL =
    "https://api.nytimes.com/svc/search/v2/articlesearch.json?api-key=${SEARCH_API_KEY}"

class MainActivity : AppCompatActivity() {
    private val articles = mutableListOf<DisplayArticle>()
    private lateinit var articlesRecyclerView: RecyclerView
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        articlesRecyclerView = findViewById(R.id.articles)
        val articleAdapter = ArticleAdapter(this, articles)
        articlesRecyclerView.adapter = articleAdapter
        articlesRecyclerView.layoutManager = LinearLayoutManager(this).also {
            val dividerItemDecoration = DividerItemDecoration(this, it.orientation)
            articlesRecyclerView.addItemDecoration(dividerItemDecoration)
        }





        val client = AsyncHttpClient()
        client.get(ARTICLE_SEARCH_URL, object : JsonHttpResponseHandler() {
            override fun onFailure(
                statusCode: Int,
                headers: Headers?,
                response: String?,
                throwable: Throwable?
            ) {
                Log.e(TAG, "Failed to fetch articles: $statusCode")
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onSuccess(statusCode: Int, headers: Headers, json: JSON) {
                Log.i(TAG, "Successfully fetched articles: $json")
                try {
                    //Create the parsedJSON
                    val parsedJson = createJson().decodeFromString(
                        SearchNewsResponse.serializer(),
                        json.jsonObject.toString()
                    )

                    // Clear the existing cache and insert new data
                    parsedJson.response?.docs?.let { list ->
                        lifecycleScope.launch(Dispatchers.IO) { // Use Dispatchers.IO for database operations
                            val articleDao = (application as ArticleApplication).db.articleDao()
                            articleDao.deleteAll()
                            articleDao.insertAll(list.map {
                                ArticleEntity(
                                    headline = it.headline?.main,
                                    articleAbstract = it.abstract,
                                    byline = it.byline?.original,
                                    mediaImageUrl = it.mediaImageUrl
                                )
                            })

                            withContext(Dispatchers.Main) { // Switch back to the main thread to update the UI
                                articles.clear()
                                articles.addAll(list.map {
                                    DisplayArticle(
                                        headline = it.headline?.main,
                                        abstract = it.abstract,
                                        byline = it.byline?.original,
                                        mediaImageUrl = it.mediaImageUrl
                                    )
                                })
                                articleAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Exception: $e")
                }
            }

        })

        // Observe changes in the database and update the UI
        lifecycleScope.launch {
            (application as ArticleApplication).db.articleDao().getAll().collect { databaseList ->
                val mappedList = databaseList.map { entity ->
                    DisplayArticle(
                        entity.headline,
                        entity.articleAbstract,
                        entity.byline,
                        entity.mediaImageUrl
                    )
                }
                articles.clear()
                articles.addAll(mappedList)
                articleAdapter.notifyDataSetChanged()
            }
        }
    }
}


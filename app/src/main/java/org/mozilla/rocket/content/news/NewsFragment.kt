package org.mozilla.rocket.content.news

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import dagger.android.support.DaggerFragment
import org.mozilla.focus.R
import org.mozilla.focus.navigation.ScreenNavigator
import org.mozilla.lite.partner.NewsItem
import org.mozilla.rocket.content.ContentPortalViewState
import org.mozilla.rocket.content.activityViewModelProvider
import org.mozilla.rocket.content.portal.ContentFeature
import org.mozilla.rocket.content.portal.ContentPortalListener
import org.mozilla.rocket.widget.BottomSheetBehavior

class NewsFragment : DaggerFragment(), ContentPortalListener, NewsViewContract {

    @javax.inject.Inject
    lateinit var viewModelFactory: NewsViewModelFactory

    override fun getCategory(): String {
        return arguments?.getString(ContentFeature.TYPE_KEY) ?: "top-news"
    }

    override fun getLanguage(): String {
        return arguments?.getString(ContentFeature.EXTRA_NEWS_LANGUAGE) ?: "english"
    }

    companion object {
        private const val NEWS_THRESHOLD = 10

        fun newInstance(bottomSheetBehavior: BottomSheetBehavior<View>): NewsFragment {
            return NewsFragment().also { it.bottomSheetBehavior = bottomSheetBehavior }
        }

        fun newInstance(category: String, language: String): NewsFragment {
            val args = Bundle().apply {
                putString(ContentFeature.TYPE_KEY, category)
                putString(ContentFeature.EXTRA_NEWS_LANGUAGE, language)
            }
            return NewsFragment().apply {
                arguments = args
            }
        }
    }

    interface NewsListListener {
        fun loadMore()
        fun onShow(context: Context)
    }

    private var newsPresenter: NewsPresenter? = null
    private var recyclerView: RecyclerView? = null
    private var newsListListener: NewsListListener? = null
    private var newsEmptyView: View? = null
    private var newsProgressCenter: ProgressBar? = null
    private var newsAdapter: NewsAdapter<NewsItem>? = null
    private var newsListLayoutManager: LinearLayoutManager? = null
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.content_news, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val newsViewModel: NewsViewModel = activityViewModelProvider(viewModelFactory)
        newsPresenter = NewsPresenter(this, newsViewModel)
        newsPresenter?.setupNewsViewModel(activity, getCategory(), getLanguage())
        newsListListener = newsPresenter
        newsListListener?.onShow(context!!)

        view.findViewById<Button>(R.id.news_try_again)?.setOnClickListener {
            newsListListener?.loadMore()
        }
        recyclerView = view.findViewById(R.id.news_list)
        newsEmptyView = view.findViewById(R.id.empty_view_container)
        newsProgressCenter = view.findViewById(R.id.news_progress_center)
        newsAdapter = NewsAdapter(this)

        recyclerView?.adapter = newsAdapter
        newsListLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView?.layoutManager = newsListLayoutManager
        newsListLayoutManager?.let {
            recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val totalItemCount = it.itemCount
                    val visibleItemCount = it.childCount
                    val lastVisibleItem = it.findLastVisibleItemPosition()
                    if (visibleItemCount + lastVisibleItem + NEWS_THRESHOLD >= totalItemCount) {
                        newsListListener?.loadMore()
                    }
                }
            })
        }
    }

    override fun updateNews(items: List<NewsItem>?) {
        onStatus(items)

        newsAdapter?.submitList(items)
        ContentPortalViewState.lastNewsPos?.let {
            val size = items?.size
            if (size != null && size > it) {
                newsListLayoutManager?.scrollToPosition(it)
                // forget about last scroll position
                ContentPortalViewState.lastNewsPos = null
            }
        }
    }

    override fun onItemClicked(url: String) {
        ScreenNavigator.get(context).showBrowserScreen(url, true, false)

        // use findFirstVisibleItemPosition so we don't need to remember offset
        newsListLayoutManager?.findFirstVisibleItemPosition()?.let {
            ContentPortalViewState.lastNewsPos = it
        }
    }

    override fun onStatus(items: List<NewsItem>?) {
        when {
            items == null -> {
                recyclerView?.visibility = View.GONE
                newsEmptyView?.visibility = View.GONE
                newsProgressCenter?.visibility = View.VISIBLE
                bottomSheetBehavior?.skipCollapsed = true
            }
            items.isEmpty() -> {
                recyclerView?.visibility = View.GONE
                newsEmptyView?.visibility = View.VISIBLE
                newsProgressCenter?.visibility = View.GONE
                bottomSheetBehavior?.skipCollapsed = true
            }
            else -> {
                recyclerView?.visibility = View.VISIBLE
                newsEmptyView?.visibility = View.GONE
                newsProgressCenter?.visibility = View.GONE
            }
        }
    }
}
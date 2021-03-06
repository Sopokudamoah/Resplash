package com.b_lam.resplash.ui.user

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.observe
import com.b_lam.resplash.R
import com.b_lam.resplash.data.user.model.User
import com.b_lam.resplash.ui.base.BaseActivity
import com.b_lam.resplash.ui.base.BaseSwipeRecyclerViewFragment
import com.b_lam.resplash.util.*
import com.b_lam.resplash.util.customtabs.CustomTabsHelper
import com.b_lam.resplash.util.livedata.observeEvent
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_search.tab_layout
import kotlinx.android.synthetic.main.activity_search.view_pager
import kotlinx.android.synthetic.main.activity_user.*
import kotlinx.android.synthetic.main.activity_user.app_bar
import org.koin.androidx.viewmodel.ext.android.viewModel

class UserActivity : BaseActivity() {

    override val viewModel: UserViewModel by viewModel()

    private lateinit var fragmentPagerAdapter: UserFragmentPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        val user = intent.getParcelableExtra<User>(EXTRA_USER)
        val username = intent.getStringExtra(EXTRA_USERNAME)

        when {
            user != null -> viewModel.setUser(user)
            username != null -> viewModel.getUser(username)
            else -> finish()
        }

        viewModel.userLiveData.observe(this) { setup(it) }
        viewModel.getUserResultLiveData.observeEvent(this) {
            if (it !is Result.Success) {
                toast(R.string.oops)
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_user, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_open_portfolio_link)?.isVisible =
            !viewModel.userLiveData.value?.portfolio_url.isNullOrBlank()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_portfolio_link -> {
                viewModel.userLiveData.value?.portfolio_url?.let { openUrlInBrowser(it) }
                true
            }
            R.id.action_open_in_browser -> {
                viewModel.userLiveData.value?.links?.html?.let { openUrlInBrowser(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setup(user: User) {
        setupActionBar(R.id.toolbar) {
            title = user.username
            setDisplayHomeAsUpEnabled(true)
        }
        fragmentPagerAdapter = UserFragmentPagerAdapter(this, supportFragmentManager, user)
        view_pager.apply {
            adapter = fragmentPagerAdapter
            offscreenPageLimit = 2
        }
        tab_layout.apply {
            setupWithViewPager(view_pager)
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {}
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {
                    fragmentPagerAdapter.getFragment(tab?.position ?: 0)?.scrollToTop()
                }
            })
        }
        toolbar.setOnClickListener { app_bar.setExpanded(true) }
        invalidateOptionsMenu()

        content_loading_layout.hide()
        user_content_layout.isVisible = true

        user_image_view.loadProfilePicture(user)
        user_name_text_view.text = user.name

        photos_count_text_view.text = user.total_photos?.toPrettyString()
        likes_count_text_view.text = user.total_likes?.toPrettyString()
        collections_count_text_view.text = user.total_collections?.toPrettyString()

        photos_count_container.setOnClickListener { goToTab(UserFragmentPagerAdapter.UserFragment.PHOTO) }
        likes_count_container.setOnClickListener { goToTab(UserFragmentPagerAdapter.UserFragment.LIKES) }
        collections_count_container.setOnClickListener { goToTab(UserFragmentPagerAdapter.UserFragment.COLLECTION) }

        location_text_view.setTextAndVisibility(user.location)
        location_text_view.setOnClickListener { openLocationInMaps(user.location) }

        bio_text_view.setTextAndVisibility(user.bio?.trimEnd())

        user.username?.let { username -> viewModel.getUserListings(username) }
    }

    private fun openUrlInBrowser(url: String) {
        CustomTabsHelper.openCustomTab(this, Uri.parse(url), sharedPreferencesRepository.theme)
    }

    private fun goToTab(type: UserFragmentPagerAdapter.UserFragment) {
        val position = fragmentPagerAdapter.getFragmentIndexOfType(type)
        if (position != -1) {
            view_pager.currentItem = position
            app_bar.setExpanded(false)
        }
    }

    private class UserFragmentPagerAdapter(
        private val context: Context,
        private val fm: FragmentManager,
        user: User
    ) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private val fragmentTypes = mutableListOf<UserFragment>()
        private val fragmentTags = SparseArray<String>()

        init {
            if (user.total_photos != 0) fragmentTypes.add(UserFragment.PHOTO)
            if (user.total_likes != 0) fragmentTypes.add(UserFragment.LIKES)
            if (user.total_collections != 0) fragmentTypes.add(UserFragment.COLLECTION)
        }

        enum class UserFragment(val titleRes: Int) {
            PHOTO(R.string.photos),
            LIKES(R.string.likes),
            COLLECTION(R.string.collections)
        }

        fun getFragment(position: Int) =
            fm.findFragmentByTag(fragmentTags.get(position)) as? BaseSwipeRecyclerViewFragment<*>

        fun getItemType(position: Int) = fragmentTypes[position]

        fun getFragmentIndexOfType(type: UserFragment) = fragmentTypes.indexOf(type)

        override fun getItem(position: Int): Fragment {
            return when (getItemType(position)) {
                UserFragment.PHOTO -> UserPhotoFragment.newInstance()
                UserFragment.LIKES -> UserLikesFragment.newInstance()
                UserFragment.COLLECTION -> UserCollectionFragment.newInstance()
            }
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val fragment = super.instantiateItem(container, position)
            (fragment as? Fragment)?.tag?.let { fragmentTags.put(position, it) }
            return fragment
        }

        override fun getPageTitle(position: Int) = context.getString(getItemType(position).titleRes)

        override fun getCount() = fragmentTypes.size
    }

    companion object {

        const val EXTRA_USER = "extra_user"
        const val EXTRA_USERNAME = "extra_username"
    }
}

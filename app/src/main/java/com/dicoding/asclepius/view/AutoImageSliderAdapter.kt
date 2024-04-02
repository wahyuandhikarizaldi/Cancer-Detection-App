package com.dicoding.asclepius.view

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.dicoding.asclepius.R
import com.dicoding.asclepius.data.ItemImageSlider

class AutoImageSliderAdapter(private val context: Context, private var imageList: List<ItemImageSlider>) : PagerAdapter() {

    private var currentPage = 0
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    override fun getCount() = imageList.size

    fun autoslide(viewPager: ViewPager){
        handler= Handler()
        runnable = object : Runnable {
            override fun run() {
                if (currentPage == viewPager.adapter?.count?.minus(1)) {
                    currentPage = 0
                } else {
                    currentPage++
                }
                viewPager.setCurrentItem(currentPage, true)
                handler.postDelayed(this, 5000)
            }
        }
        Handler().postDelayed(runnable,2500)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view: View =  (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
                as LayoutInflater).inflate(R.layout.image_slider_item, null)
        val ivImages = view.findViewById<ImageView>(R.id.imageView)
        val heading=view.findViewById<TextView>(R.id.tvHeading)
        val subHeading=view.findViewById<TextView>(R.id.tvSubHeading)
        Glide.with(context)
            .load(imageList[position].imageUrl)
            .into(ivImages)
        heading.text=imageList[position].title
        subHeading.text= imageList[position].description

        view.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(imageList[position].linkUrl))
            context.startActivity(intent)
        }

        val vp = container as ViewPager
        vp.addView(view, 0)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val vp = container as ViewPager
        val view = `object` as View
        vp.removeView(view)
    }
}
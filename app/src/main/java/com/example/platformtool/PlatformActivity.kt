package com.example.platformtool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.max

/** Material 3 edge-to-edge shell used by XML pages during the Compose migration. */
open class PlatformActivity : AppCompatActivity() {
    private lateinit var shell: LinearLayout
    private lateinit var contentHost: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        installShell()
    }

    override fun setContentView(@LayoutRes layoutResID: Int) {
        setPageContent(LayoutInflater.from(this).inflate(layoutResID, contentHost, false))
    }

    override fun setContentView(view: View?) {
        requireNotNull(view) { "PlatformActivity content view cannot be null" }
        setPageContent(view)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        requireNotNull(view) { "PlatformActivity content view cannot be null" }
        if (params == null) {
            setPageContent(view)
            return
        }
        contentHost.removeAllViews()
        contentHost.addView(view, params)
    }

    private fun installShell() {
        shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColorFromAttribute(com.google.android.material.R.attr.colorSurface))
        }
        val toolbar = MaterialToolbar(this).apply {
            id = View.generateViewId()
            setBackgroundColor(getColorFromAttribute(com.google.android.material.R.attr.colorSurface))
        }
        contentHost = FrameLayout(this)
        shell.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resolveActionBarHeight(),
            ),
        )
        shell.addView(
            contentHost,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        super.setContentView(shell)
        setSupportActionBar(toolbar)
        installInsets()
    }

    private fun setPageContent(view: View) {
        contentHost.removeAllViews()
        contentHost.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun installInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, windowInsets ->
            val safe = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = safe.left,
                top = safe.top,
                right = safe.right,
                bottom = max(safe.bottom, ime.bottom),
            )
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(shell)
    }

    private fun resolveActionBarHeight(): Int {
        val values = intArrayOf(android.R.attr.actionBarSize)
        return obtainStyledAttributes(values).let { attributes ->
            try {
                attributes.getDimensionPixelSize(0, 0)
            } finally {
                attributes.recycle()
            }
        }
    }

    private fun getColorFromAttribute(attribute: Int): Int {
        val values = intArrayOf(attribute)
        return obtainStyledAttributes(values).let { attributes ->
            try {
                attributes.getColor(0, 0)
            } finally {
                attributes.recycle()
            }
        }
    }
}

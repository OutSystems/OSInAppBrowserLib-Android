package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.R

object OSIABUIHelper {

    /**
     * Creates a RelativeLayout.LayoutParams instance with the common settings (height, width and
     * alignment)
     * @return new instance of RelativeLayout.LayoutParams
     */
    fun createCommonLayout(): RelativeLayout.LayoutParams {
        val custom = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        custom.addRule(RelativeLayout.CENTER_VERTICAL)
        return custom
    }

    /**
     * Creates the URL preview
     * @param url the url text
     * @param isLeftRight dictates the placement of the url
     * @return the URL TextView
     */
    fun createUrlText(context: Context, url: String, isLeftRight: Boolean): TextView {
        val params = createCommonLayout().apply {
            if (isLeftRight) {
                addRule(RelativeLayout.END_OF, R.id.navigation_buttons)
                addRule(RelativeLayout.START_OF, R.id.close_button)
            } else {
                addRule(RelativeLayout.END_OF, R.id.close_button)
                addRule(RelativeLayout.START_OF, R.id.navigation_buttons)
            }
        }
        return createTextView(context, url, R.style.URLBar, params)
    }

    /**
     * Creates a TextView, with the given text, style and layout params
     * @param withText the display text
     * @param style the view's style id
     * @param params relative layout params
     * @return a new Text View
     */
    fun createTextView(
        context: Context,
        withText: String,
        @StyleRes style: Int,
        params: RelativeLayout.LayoutParams
    ): TextView {
        return TextView(ContextThemeWrapper(context, style)).apply {
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            text = withText
            layoutParams = params
        }
    }

    /**
     * Creates a TextView, with the given style and layout params
     * @param style the view's style id
     * @param params relative layout params
     * @return a new Image Button
     */
    fun createImageButton(
        context: Context,
        @StyleRes style: Int,
        params: RelativeLayout.LayoutParams
    ): ImageButton {
        return ImageButton(ContextThemeWrapper(context, style)).apply {
            layoutParams = params
            isEnabled = false
        }
    }
}
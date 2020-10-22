package com.souvikbiswas.helloespresso

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

class ShowTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_text)

        // Get the message from the Intent.
        val intent = intent
        val message: String = intent.getStringExtra(KEY_EXTRA_MESSAGE).toString()

        // Show message.
        (findViewById<View>(R.id.show_text_view) as TextView).text = message
    }

    companion object {
        const val KEY_EXTRA_MESSAGE = "com.souvikbiswas.helloespresso.MESSAGE"

        fun newStartIntent(
            context: Context?,
            message: String?
        ): Intent {
            val newIntent = Intent(context, ShowTextActivity::class.java)
            newIntent.putExtra(KEY_EXTRA_MESSAGE, message)
            return newIntent
        }
    }
}
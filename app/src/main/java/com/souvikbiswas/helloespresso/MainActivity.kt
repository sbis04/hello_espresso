package com.souvikbiswas.helloespresso

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity(), View.OnClickListener {
    private var mTextView: TextView? = null
    private var mEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.changeTextBt).setOnClickListener(this)
        findViewById<View>(R.id.activityChangeTextBtn).setOnClickListener(this)

        mTextView = findViewById<View>(R.id.textToBeChanged) as TextView
        mEditText = findViewById<View>(R.id.editTextUserInput) as EditText
    }

    override fun onClick(view: View) {
        val text = mEditText!!.text.toString()
        val changeTextBtId = R.id.changeTextBt
        val activityChangeTextBtnId = R.id.activityChangeTextBtn

        if (view.id == changeTextBtId) {
            mTextView!!.text = text
        } else if (view.id == activityChangeTextBtnId) {
            val intent: Intent = ShowTextActivity.newStartIntent(this, text)
            startActivity(intent)
        }
    }
}
package com.example.turducken

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKWebView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FanMakerSDK.initialize("bb460452f81404b2cdf8d5691714115bb1b25905d337cc4ea50c89f327d7209d")
    }

    fun setupIdentifiers() {
        val memberID: String = findViewById<EditText>(R.id.memberID).text.toString()
        if (memberID != "") FanMakerSDK.memberID = memberID

        val studentID: String = findViewById<EditText>(R.id.studentID).text.toString()
        if (studentID != "") FanMakerSDK.studentID = studentID

        val ticketmasterID: String = findViewById<EditText>(R.id.ticketmasterID).text.toString()
        if (ticketmasterID != "") FanMakerSDK.ticketmasterID = ticketmasterID

        val yinzID: String = findViewById<EditText>(R.id.yinzID).text.toString()
        if (yinzID != "") FanMakerSDK.yinzid = yinzID

        val pushToken: String = findViewById<EditText>(R.id.pushToken).text.toString()
        if (pushToken != "") FanMakerSDK.pushNotificationToken = pushToken
    }

    fun openFanMakerSDKWebView(view: View) {
        setupIdentifiers()
        val intent = Intent(this, FanMakerSDKWebView::class.java)
        startActivity(intent)
    }

    fun openFanMakerSDKWebViewFragment(view: View) {
        setupIdentifiers()
        val intent = Intent(this, FanMakerActivity::class.java)
        startActivity(intent)
    }
}
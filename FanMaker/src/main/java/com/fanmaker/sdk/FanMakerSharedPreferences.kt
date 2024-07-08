package com.fanmaker.sdk

import android.content.Context
import android.content.SharedPreferences

class FanMakerSharedPreferences(context: Context, apiToken: String) {
  val fanmakerSharedPreferences: SharedPreferences = context.getSharedPreferences(apiToken, Context.MODE_PRIVATE)

  fun putString(key: String, value: String) {
      val editor = fanmakerSharedPreferences.edit()
      editor.putString(key, value)
      editor.apply()
  }

  fun getString(key: String, defaultValue: String): String? {
      return fanmakerSharedPreferences.getString(key, defaultValue)
  }

  fun putInt(key: String, value: Int) {
    val editor = fanmakerSharedPreferences.edit()
    editor.putInt(key, value)
    editor.apply()
}

  fun getInt(key: String, defaultValue: Int): Int {
      return fanmakerSharedPreferences.getInt(key, defaultValue)
  }

  fun putBoolean(key: String, value: Boolean) {
      val editor = fanmakerSharedPreferences.edit()
      editor.putBoolean(key, value)
      editor.apply()
  }

  fun getBoolean(key: String, defaultValue: Boolean): Boolean {
      return fanmakerSharedPreferences.getBoolean(key, defaultValue)
  }

  fun putFloat(key: String, value: Float) {
      val editor = fanmakerSharedPreferences.edit()
      editor.putFloat(key, value)
      editor.apply()
  }

  fun getFloat(key: String, defaultValue: Float): Float {
      return fanmakerSharedPreferences.getFloat(key, defaultValue)
  }

  fun putLong(key: String, value: Long) {
      val editor = fanmakerSharedPreferences.edit()
      editor.putLong(key, value)
      editor.apply()
  }

  fun getLong(key: String, defaultValue: Long): Long {
      return fanmakerSharedPreferences.getLong(key, defaultValue)
  }

  fun commit() {
    fanmakerSharedPreferences.edit().commit()
  }

  fun getSharedPreferences(): SharedPreferences {
    return fanmakerSharedPreferences
  }
}

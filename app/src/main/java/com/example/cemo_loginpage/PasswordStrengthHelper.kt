package com.example.cemo_loginpage

import android.content.res.ColorStateList
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt

object PasswordStrengthHelper {

    enum class Level { NONE, WEAK, MEDIUM, STRONG }

    data class Result(val level: Level, val progress: Int, val label: String, val color: String)

    // Same scoring as the web admin panel (settings.js)
    fun evaluate(password: String): Result {
        if (password.isEmpty()) return Result(Level.NONE, 0, "", "#000000")

        var score = 0
        if (password.length >= 6) score++
        if (password.length >= 10) score++
        if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        val progress = (score.coerceAtMost(5) * 100) / 5

        return when {
            score <= 2 -> Result(Level.WEAK, progress, "Weak password", "#dc2626")
            score <= 3 -> Result(Level.MEDIUM, progress, "Medium password", "#e0a83e")
            else       -> Result(Level.STRONG, progress, "Strong password", "#16a34a")
        }
    }

    fun isWeak(password: String) = evaluate(password).level == Level.WEAK

    fun bind(password: String, bar: ProgressBar, label: TextView) {
        val r = evaluate(password)
        if (r.level == Level.NONE) {
            bar.visibility = View.GONE
            label.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        label.visibility = View.VISIBLE
        bar.progress = r.progress
        bar.progressTintList = ColorStateList.valueOf(r.color.toColorInt())
        label.text = r.label
        label.setTextColor(r.color.toColorInt())
    }
}
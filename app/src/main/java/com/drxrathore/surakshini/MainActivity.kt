package com.drxrathore.surakshini

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var layoutPhone: LinearLayout
    private lateinit var layoutOtp: LinearLayout
    private lateinit var etPhoneNumber: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var btnVerifyOtp: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSubtitle: TextView

    private lateinit var otp1: EditText
    private lateinit var otp2: EditText
    private lateinit var otp3: EditText
    private lateinit var otp4: EditText
    private lateinit var otp5: EditText
    private lateinit var otp6: EditText

    private val MOCK_OTP = "123456"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("SurakshiniPrefs", Context.MODE_PRIVATE)
        if (sharedPref.getBoolean("isLoggedIn", false)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        layoutPhone = findViewById(R.id.layoutPhone)
        layoutOtp = findViewById(R.id.layoutOtp)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        tvSubtitle = findViewById(R.id.tvSubtitle)

        otp1 = findViewById(R.id.otp1)
        otp2 = findViewById(R.id.otp2)
        otp3 = findViewById(R.id.otp3)
        otp4 = findViewById(R.id.otp4)
        otp5 = findViewById(R.id.otp5)
        otp6 = findViewById(R.id.otp6)

        setupOtpAutoForward()

        btnSendOtp.setOnClickListener {
            val phone = etPhoneNumber.text.toString().trim()
            if (phone.length < 10) {
                Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSendOtp.isEnabled = false

            Handler(Looper.getMainLooper()).postDelayed({
                layoutPhone.visibility = View.GONE
                layoutOtp.visibility = View.VISIBLE
                tvSubtitle.text = "Verify Your Number"
                Toast.makeText(this, "OTP: 123456", Toast.LENGTH_LONG).show()
            }, 1500)
        }

        btnVerifyOtp.setOnClickListener {
            val enteredOtp = "${otp1.text}${otp2.text}${otp3.text}${otp4.text}${otp5.text}${otp6.text}"

            if (enteredOtp.length < 6) {
                Toast.makeText(this, "Complete the OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (enteredOtp == MOCK_OTP) {
                sharedPref.edit().putBoolean("isLoggedIn", true).apply()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOtpAutoForward() {
        fun addAutoForward(current: EditText, next: EditText?) {
            current.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) next?.requestFocus()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        addAutoForward(otp1, otp2)
        addAutoForward(otp2, otp3)
        addAutoForward(otp3, otp4)
        addAutoForward(otp4, otp5)
        addAutoForward(otp5, otp6)
    }
}
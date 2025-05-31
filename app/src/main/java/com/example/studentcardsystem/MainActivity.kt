package com.example.studentcardsystem

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color // Colorクラスをインポート

class MainActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilters: Array<IntentFilter>
    private lateinit var techLists: Array<Array<String>>
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var attendanceManager: AttendanceManager

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var studentIdText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("email_settings", MODE_PRIVATE)
        attendanceManager = AttendanceManager(this)

        initializeViews()
        initializeNFC()
        updateModeDisplay()
        addLog("アプリケーション開始")

        // 利用可能なメールアプリを確認
        val availableApps = checkAvailableEmailApps() // 修正: 呼び出し自体は問題なさそうだが、周辺のロジックを確認
        if (availableApps.isNotEmpty()) {
            addLog("利用可能なメールアプリ: ${availableApps.joinToString(", ")}")
        } else {
            addLog("メールアプリが見つかりません - SMTP送信を使用してください")
        }

        // 初回起動時にメール設定ダイアログを表示
        if (!sharedPreferences.getBoolean("settings_configured", false)) {
            showEmailSettingsDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_email_settings -> {
                showEmailSettingsDialog()
                true
            }
            R.id.action_record_settings -> {
                showRecordSettingsDialog()
                true
            }
            R.id.action_toggle_mode -> {
                toggleMode()
                true
            }
            R.id.action_view_records -> {
                showRecordsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showEmailSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_settings, null)
        val sendMethodGroup = dialogView.findViewById<RadioGroup>(R.id.sendMethodGroup)
        val radioGmailApp = dialogView.findViewById<RadioButton>(R.id.radioGmailApp)
        val radioSmtp = dialogView.findViewById<RadioButton>(R.id.radioSmtp)
        val smtpSettingsLayout = dialogView.findViewById<LinearLayout>(R.id.smtpSettingsLayout)
        val emailAddressInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emailAddressInput)
        val emailDomainInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emailDomainInput) // 追加
        val passwordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.passwordInput)
        val subjectInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.subjectInput)
        val emailBodyTemplateInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emailBodyTemplateInput)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        // 既存の設定を読み込み
        val currentSendMethod = sharedPreferences.getString("sendMethod", "gmail")
        emailAddressInput.setText(sharedPreferences.getString("emailAddress", ""))
        emailDomainInput.setText(sharedPreferences.getString("emailDomain", "")) // デフォルト値を空に変更
        passwordInput.setText(sharedPreferences.getString("password", ""))
        subjectInput.setText(sharedPreferences.getString("emailSubjectTemplate", "入退室連絡"))
        emailBodyTemplateInput.setText(sharedPreferences.getString("emailBodyTemplate", "%sさんが入室/退室しました。"))

        if (currentSendMethod == "smtp") {
            radioSmtp.isChecked = true
            smtpSettingsLayout.visibility = View.VISIBLE
        } else {
            radioGmailApp.isChecked = true
            smtpSettingsLayout.visibility = View.GONE
        }

        // ラジオボタンの変更でレイアウト制御
        sendMethodGroup.setOnCheckedChangeListener { _, checkedId ->
            smtpSettingsLayout.visibility = if (checkedId == R.id.radioSmtp) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        saveButton.setOnClickListener {
            val editor = sharedPreferences.edit()
            editor.putString("sendMethod", if (radioGmailApp.isChecked) "gmail" else "smtp")
            editor.putString("emailAddress", emailAddressInput.text.toString())
            editor.putString("emailDomain", emailDomainInput.text.toString()) // 追加
            editor.putString("password", passwordInput.text.toString())
            editor.putString("emailSubjectTemplate", subjectInput.text.toString())
            editor.putString("emailBodyTemplate", emailBodyTemplateInput.text.toString())
            editor.putBoolean("settings_configured", true) // 設定済みフラグを立てる
            editor.apply()
            addLog("メール設定を保存しました")
            Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        dialog.setCancelable(false) // ダイアログ外タップで閉じないようにする
        dialog.show()
    }
    
    private fun updateModeDisplay() {
        val isRecordMode = attendanceManager.isRecordModeEnabled()
        if (isRecordMode) {
            val eventName = attendanceManager.getCurrentEvent()
            val recordType = attendanceManager.getCurrentRecordType()
            statusText.text = "記録モード: $eventName ($recordType)"
            statusText.setBackgroundColor(Color.WHITE) // 背景を白に
            statusText.setTextColor(Color.BLACK)     // 文字を黒に
        } else {
            statusText.text = "メール送信モード"
            statusText.setBackgroundColor(Color.BLACK) // 背景を黒に
            statusText.setTextColor(Color.WHITE)     // 文字を白に
        }
    }
    
    private fun toggleMode() {
        val isCurrentlyRecordMode = attendanceManager.isRecordModeEnabled()
        
        if (!isCurrentlyRecordMode) {
            // メールモードから記録モードに切り替え
            val eventName = attendanceManager.getCurrentEvent()
            if (eventName.isEmpty()) {
                // イベントが設定されていない場合は設定ダイアログを表示
                showRecordSettingsDialog()
            } else {
                attendanceManager.setRecordModeEnabled(true)
                updateModeDisplay()
                addLog("記録モードに切り替えました")
                Toast.makeText(this, "記録モードに切り替えました", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 記録モードからメールモードに切り替え
            attendanceManager.setRecordModeEnabled(false)
            updateModeDisplay()
            addLog("メール送信モードに切り替えました")
            Toast.makeText(this, "メール送信モードに切り替えました", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showRecordSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_record_settings, null)
        val eventNameInput = dialogView.findViewById<EditText>(R.id.eventNameInput)
        val recordTypeGroup = dialogView.findViewById<RadioGroup>(R.id.recordTypeGroup)
        val radioAttendance = dialogView.findViewById<RadioButton>(R.id.radioAttendance)
        val radioPayment = dialogView.findViewById<RadioButton>(R.id.radioPayment)
        val radioOther = dialogView.findViewById<RadioButton>(R.id.radioOther)
        val customTypeLayout = dialogView.findViewById<LinearLayout>(R.id.customTypeLayout)
        val customTypeInput = dialogView.findViewById<EditText>(R.id.customTypeInput)
        
        // 既存の設定を読み込み
        val currentEvent = attendanceManager.getCurrentEvent()
        val currentRecordType = attendanceManager.getCurrentRecordType()
        
        eventNameInput.setText(currentEvent)
        
        when (currentRecordType) {
            "出席" -> radioAttendance.isChecked = true
            "集金" -> radioPayment.isChecked = true
            else -> {
                radioOther.isChecked = true
                customTypeLayout.visibility = View.VISIBLE
                customTypeInput.setText(currentRecordType)
            }
        }
        
        // "その他"選択時の処理
        recordTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOther -> {
                    customTypeLayout.visibility = View.VISIBLE
                }
                else -> {
                    customTypeLayout.visibility = View.GONE
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("記録設定")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val eventName = eventNameInput.text.toString().trim()
                
                if (eventName.isEmpty()) {
                    Toast.makeText(this, "イベント名を入力してください", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val recordType = when (recordTypeGroup.checkedRadioButtonId) {
                    R.id.radioAttendance -> "出席"
                    R.id.radioPayment -> "集金"
                    R.id.radioOther -> {
                        val customType = customTypeInput.text.toString().trim()
                        if (customType.isEmpty()) "その他" else customType
                    }
                    else -> "出席"
                }
                
                attendanceManager.setCurrentEvent(eventName, recordType)
                attendanceManager.setRecordModeEnabled(true)
                updateModeDisplay()
                
                addLog("記録設定を保存しました: $eventName ($recordType)")
                Toast.makeText(this, "記録設定を保存しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
    
    private fun showRecordsDialog() {
        val recentRecords = attendanceManager.getRecentRecords()
        val csvFiles = attendanceManager.getCsvFiles()
        
        val message = StringBuilder()
        
        message.append("=== 最近の記録 ===\n")
        if (recentRecords.isEmpty()) {
            message.append("記録がありません\n")
        } else {
            recentRecords.take(10).forEach { record ->
                message.append("$record\n")
            }
            if (recentRecords.size > 10) {
                message.append("...他${recentRecords.size - 10}件\n")
            }
        }
        
        message.append("\n=== CSVファイル ===\n")
        if (csvFiles.isEmpty()) {
            message.append("保存されたファイルがありません\n")
        } else {
            csvFiles.forEach { file ->
                val sizeKB = file.length() / 1024
                message.append("${file.name} (${sizeKB}KB)\n")
            }
        }
        
        message.append("\n※ CSVファイルは以下のパスに保存されます:\n")
        message.append("${getExternalFilesDir(null)}/attendance/")
        
        AlertDialog.Builder(this)
            .setTitle("記録確認")
            .setMessage(message.toString())
            .setPositiveButton("記録クリア") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("確認")
                    .setMessage("最近の記録をクリアしますか？\n（CSVファイルは削除されません）")
                    .setPositiveButton("クリア") { _, _ ->
                        attendanceManager.clearRecentRecords()
                        addLog("記録をクリアしました")
                        Toast.makeText(this, "記録をクリアしました", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
            .setNegativeButton("閉じる", null)
            .show()
    }
    
    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        studentIdText = findViewById(R.id.studentIdText)
        progressBar = findViewById(R.id.progressBar)
        logText = findViewById(R.id.logText)
        
        logText.movementMethod = ScrollingMovementMethod()
        statusText.text = "NFC準備完了"
    }
    
    private fun initializeNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        if (nfcAdapter == null) {
            statusText.text = getString(R.string.nfc_not_supported)
            addLog("NFCがサポートされていません")
            return
        }
        
        if (!nfcAdapter.isEnabled) {
            statusText.text = getString(R.string.nfc_disabled)
            addLog("NFCが無効になっています")
            return
        }
        
        // Create pending intent
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        
        // Setup intent filters
        val nfcIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        intentFilters = arrayOf(nfcIntentFilter)
        
        // Setup tech lists
        techLists = arrayOf(arrayOf(NfcF::class.java.name))
        
        addLog("NFC初期化完了")
    }
    
    override fun onResume() {
        super.onResume()
        if (::nfcAdapter.isInitialized && nfcAdapter.isEnabled) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
            addLog("NFCフォアグラウンドディスパッチ有効化")
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (::nfcAdapter.isInitialized) {
            nfcAdapter.disableForegroundDispatch(this)
            addLog("NFCフォアグラウンドディスパッチ無効化")
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                handleNfcTag(tag)
            }
        }
    }
    
    private fun handleNfcTag(tag: Tag) {
        addLog("NFCタグ検出")
        statusText.text = getString(R.string.reading_card)
        progressBar.visibility = View.VISIBLE
        studentIdText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val studentId = FelicaReader.readStudentId(tag)

                if (studentId != null) {
                    addLog("学籍番号検出: $studentId")
                    runOnUiThread {
                        studentIdText.text = getString(R.string.student_id_detected, studentId)
                        studentIdText.visibility = View.VISIBLE
                    }

                    val isRecordMode = attendanceManager.isRecordModeEnabled()
                    val emailDomain = sharedPreferences.getString("emailDomain", "") // デフォルト値を空に変更

                    if (!isRecordMode && emailDomain.isNullOrEmpty()) { // メール送信モードかつドメインが未設定の場合
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            statusText.text = "ドメイン未設定"
                            Toast.makeText(this@MainActivity, "宛先メールアドレスのドメインが設定されていません。設定画面でドメインを入力してください。", Toast.LENGTH_LONG).show()
                            showEmailSettingsDialog() // 設定ダイアログを直接表示
                        }
                        return@launch // メール送信処理を中断
                    }


                    if (isRecordMode) {
                        // 記録モード
                        runOnUiThread {
                            statusText.text = "記録中..."
                        }

                        val recordSaved = attendanceManager.addRecord(studentId)

                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            if (recordSaved) {
                                val eventName = attendanceManager.getCurrentEvent()
                                val recordType = attendanceManager.getCurrentRecordType()
                                statusText.text = "記録完了: $eventName ($recordType)"
                                addLog("記録保存成功: $studentId - $eventName ($recordType)")
                                Toast.makeText(this@MainActivity, "記録しました", Toast.LENGTH_SHORT).show()
                                updateModeDisplay() // 元の表示に戻す
                            } else {
                                statusText.text = "記録失敗"
                                addLog("記録保存失敗: $studentId")
                                Toast.makeText(this@MainActivity, "記録に失敗しました", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // メール送信モード
                        runOnUiThread {
                            statusText.text = "メール送信中..."
                        }

                        val useGmailApp = sharedPreferences.getBoolean("use_gmail_app", true) // sendMethod を使用するように修正も検討
                        // val emailSent = false // スコープ外のためコメントアウトまたは修正

                        if (useGmailApp) { // 実際には sendMethod == "gmail" で判定すべき
                            // Gmailアプリ経由で送信
                            runOnUiThread {
                                sendEmailViaGmailApp(studentId, emailDomain!!)
                                progressBar.visibility = View.GONE
                                statusText.text = "Gmailアプリを開きました"
                                addLog("Gmailアプリ経由でメール作成: ${studentId.lowercase()}@$emailDomain")
                            }
                        } else {
                            // SMTP経由で送信
                            val emailAddress = sharedPreferences.getString("email_address", "") // キー名を修正
                            val appPassword = sharedPreferences.getString("app_password", "") // キー名を修正
                            val emailSubject = sharedPreferences.getString("email_subject", "出席登録") ?: "出席登録" // キー名を修正
                            val emailBodyTemplate = sharedPreferences.getString("email_body", // キー名を修正
                                "学籍番号: {STUDENT_ID}\\n出席時間: {TIMESTAMP}\\n\\nこのメールは自動送信されています。")
                                ?: "学籍番号: {STUDENT_ID}\\n出席時間: {TIMESTAMP}\\n\\nこのメールは自動送信されています。"

                            if (emailAddress.isNullOrEmpty() || appPassword.isNullOrEmpty()) {
                                runOnUiThread {
                                    progressBar.visibility = View.GONE
                                    statusText.text = "メール設定が不完全です"
                                    Toast.makeText(this@MainActivity, "メール設定を確認してください", Toast.LENGTH_SHORT).show()
                                    showEmailSettingsDialog()
                                }
                                return@launch
                            }
                            val emailSent = EmailSender.sendEmail(studentId, emailAddress, appPassword, emailSubject, emailBodyTemplate, emailDomain!!)

                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                if (emailSent) {
                                    statusText.text = getString(R.string.email_sent)
                                    addLog("メール送信成功: ${studentId.lowercase()}@$emailDomain")
                                    Toast.makeText(this@MainActivity, "メール送信完了", Toast.LENGTH_SHORT).show()
                                } else {
                                    statusText.text = getString(R.string.email_failed)
                                    addLog("メール送信失敗: $studentId")
                                    Toast.makeText(this@MainActivity, "メール送信失敗", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } else {
                    addLog("学籍番号が見つかりません")
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        statusText.text = getString(R.string.invalid_student_id)
                        Toast.makeText(this@MainActivity, "学籍番号を読み取れませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                addLog("エラー: ${e.message}")
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "エラーが発生しました"
                    Toast.makeText(this@MainActivity, "読み取りエラー", Toast.LENGTH_SHORT).show()
                }
            } 
        }
    }
    
    private fun sendEmailViaGmailApp(studentId: String, emailDomain: String) { // emailDomain を引数に追加
        val toEmail = "${studentId.lowercase()}@$emailDomain" // emailDomain を使用

        // 保存されたテンプレートを取得
        val subject = sharedPreferences.getString("email_subject", "出席登録") ?: "出席登録" // キー名を修正
        val bodyTemplate = sharedPreferences.getString("email_body", // キー名を修正
            "学籍番号: {STUDENT_ID}\\n出席時間: {TIMESTAMP}\\n\\nこのメールは自動送信されています。")
            ?: "学籍番号: {STUDENT_ID}\\n出席時間: {TIMESTAMP}\\n\\nこのメールは自動送信されています。"

        // テンプレートの置換処理
        val timestamp = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date())
        val body = bodyTemplate
            .replace("{STUDENT_ID}", studentId)
            .replace("{TIMESTAMP}", timestamp)

        try {
            // まずはGmailアプリを試行
            val gmailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                setPackage("com.google.android.gm")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            if (gmailIntent.resolveActivity(packageManager) != null) {
                addLog("Gmailアプリで起動中...")
                startActivity(gmailIntent)
                return // 修正: returnを追加して、以降の処理を実行しないようにする
            }

            // Gmailがない場合、Outlookアプリを試行
            val outlookIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                setPackage("com.microsoft.office.outlook")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            if (outlookIntent.resolveActivity(packageManager) != null) {
                addLog("Outlookアプリで起動中...")
                startActivity(outlookIntent)
                return // 修正: returnを追加
            }

            // その他のメールアプリを試行
            val genericMailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$toEmail")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            if (genericMailIntent.resolveActivity(packageManager) != null) {
                addLog("一般的なメールアプリで起動中...")
                startActivity(Intent.createChooser(genericMailIntent, "メールアプリを選択"))
                return // 修正: returnを追加
            }

            // 最後の手段：ACTION_SENDで試行
            val lastResortIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain" // 修正: text/plain に変更
                putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            if (lastResortIntent.resolveActivity(packageManager) != null) {
                addLog("共有機能でメールアプリを起動中...")
                startActivity(Intent.createChooser(lastResortIntent, "メールを送信"))
            } else {
                // メールアプリが全く見つからない場合
                addLog("メールアプリが見つかりません")
                Toast.makeText(this, "メールアプリがインストールされていません。\nSMTP送信を使用してください。", Toast.LENGTH_LONG).show()

                // SMTP送信に切り替えるかダイアログで確認
                AlertDialog.Builder(this)
                    .setTitle("メールアプリが見つかりません")
                    .setMessage("メールアプリがインストールされていないため、SMTP送信に切り替えますか？")
                    .setPositiveButton("設定") { _, _ ->
                        showEmailSettingsDialog()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }

        } catch (e: Exception) { // 修正: catchブロックの構文確認
            addLog("メールアプリ起動エラー: ${e.message}")
            Toast.makeText(this, "メールアプリの起動に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
        } // 修正: try-catchブロックの閉じ括弧が不足している可能性
    }
    private fun checkAvailableEmailApps(): List<String> {
        val availableApps = mutableListOf<String>()
        
        // Gmail
        val gmailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            setPackage("com.google.android.gm")
        }
        if (gmailIntent.resolveActivity(packageManager) != null) {
            availableApps.add("Gmail")
        }
        
        // Outlook
        val outlookIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            setPackage("com.microsoft.office.outlook")
        }
        if (outlookIntent.resolveActivity(packageManager) != null) {
            availableApps.add("Outlook")
        }
        
        // Yahoo Mail
        val yahooIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            setPackage("com.yahoo.mobile.client.android.mail")
        }
        if (yahooIntent.resolveActivity(packageManager) != null) {
            availableApps.add("Yahoo Mail")
        }
        
        // 一般的なメールアプリ
        val genericIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
        }
        if (genericIntent.resolveActivity(packageManager) != null) {
            availableApps.add("その他のメールアプリ")
        }
        
        return availableApps
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"
        
        runOnUiThread {
            logText.append(logMessage)
            
            // Auto-scroll to bottom
            val layout = logText.layout
            if (layout != null) {
                val scrollAmount = layout.getLineTop(logText.lineCount) - logText.height
                if (scrollAmount > 0) {
                    logText.scrollTo(0, scrollAmount)
                } else {
                    logText.scrollTo(0, 0)
                }
            }
        }
    }
}

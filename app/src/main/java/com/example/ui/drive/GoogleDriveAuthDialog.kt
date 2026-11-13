package com.example.ui.drive

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.drive.DriveRoot
import com.example.data.drive.GoogleDriveClient
import com.example.ui.AccentGreen
import com.example.ui.DarkSurface
import com.example.ui.DarkSurfaceVariant
import java.net.URLEncoder

/**
 * Google Drive 認証 & 接続ダイアログ。
 * フルスクリーンサイズのWebViewによるGoogle公式ログイン画面。
 * トークン手動入力は廃止し、標準サイズのWeb認証画面に一本化。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleDriveAuthDialog(
    currentRoot: DriveRoot,
    onDismiss: () -> Unit,
    onTokenObtained: (token: String, root: DriveRoot) -> Unit
) {
    var selectedDriveRoot by remember { mutableStateOf(currentRoot) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isWebViewLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val clientId = GoogleDriveClient.OAUTH_CLIENT_ID
    val redirectUri = "https://ais-dev-cucyrt7xkb3nmihyxxb52f-406625588047.asia-northeast1.run.app"
    val scope = "https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file"

    val authUrl = remember {
        "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "redirect_uri=$redirectUri&" +
                "response_type=token&" +
                "scope=${URLEncoder.encode(scope, "UTF-8")}"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = DarkSurface,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // ヘッダーバー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.drive_auth_title),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.drive_auth_desc),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                isWebViewLoading = true
                                webViewInstance?.loadUrl(authUrl)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.cloud_sync_full),
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.LightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 同期先ルート選択（マイドライブ / 共有ドライブ）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "同期対象:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    DriveRoot.entries.forEach { root ->
                        FilterChip(
                            selected = selectedDriveRoot == root,
                            onClick = { selectedDriveRoot = root },
                            label = { Text(root.displayNameJa, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = DarkSurfaceVariant,
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFF3B1515),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // フルサイズ WebView による Google ログイン
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                                webViewClient = object : WebViewClient() {
                                    private fun checkToken(url: String?): Boolean {
                                        if (url == null) return false
                                        if (url.startsWith(redirectUri) || url.contains("access_token=")) {
                                            val fragment = url.substringAfter('#', "").ifEmpty { url.substringAfter('?', "") }
                                            val params = fragment.split('&').associate {
                                                val parts = it.split('=')
                                                if (parts.size == 2) parts[0] to parts[1] else "" to ""
                                            }
                                            val token = params["access_token"]
                                            if (!token.isNullOrBlank()) {
                                                onTokenObtained(token, selectedDriveRoot)
                                                return true
                                            }
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isWebViewLoading = true
                                        checkToken(url)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isWebViewLoading = false
                                        checkToken(url)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return checkToken(request?.url?.toString())
                                    }
                                }
                                loadUrl(authUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isWebViewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x88000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentGreen)
                        }
                    }
                }
            }
        }
    }
}

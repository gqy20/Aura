package com.xiaoqi.companion

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.xiaoqi.companion.feature.chat.AuraHomeScreen
import com.xiaoqi.companion.feature.chat.ChatScreen
import com.xiaoqi.companion.feature.chat.ChatViewModel
import com.xiaoqi.companion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CompanionTheme {
                var isChatOpen by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = isChatOpen) {
                    isChatOpen = false
                }

                if (isChatOpen) {
                    ChatScreen(viewModel = chatViewModel)
                } else {
                    AuraHomeScreen(
                        viewModel = chatViewModel,
                        onOpenChat = { isChatOpen = true },
                    )
                }
            }
        }
    }
}

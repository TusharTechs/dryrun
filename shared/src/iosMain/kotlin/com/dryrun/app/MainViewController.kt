package com.dryrun.app

import androidx.compose.ui.window.ComposeUIViewController
import com.dryrun.app.notifications.LocalNotifier

fun MainViewController() = ComposeUIViewController { App(LocalNotifier()) }

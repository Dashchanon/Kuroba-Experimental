package com.github.adamantcheese.chan.ui.controller

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu

class FloatingListMenuController(
  context: Context,
  private val items: List<FloatingListMenu.FloatingListMenuItem>,
  private val listener: (item: FloatingListMenu.FloatingListMenuItem) -> Unit
) : BaseFloatingController(context) {
  private lateinit var floatingListMenu: FloatingListMenu
  private lateinit var clickableArea: ConstraintLayout

  override fun onCreate() {
    super.onCreate()

    floatingListMenu = view.findViewById(R.id.floating_list_menu)
    clickableArea = view.findViewById(R.id.clickable_area)

    clickableArea.setOnClickListener { stopPresenting(true) }

    floatingListMenu.setItems(items)
    floatingListMenu.setClickListener { clickedItem ->
      stopPresenting(true)
      listener.invoke(clickedItem)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    floatingListMenu.setClickListener(null)
  }

  override fun onBack(): Boolean {
    stopPresenting(true)
    return true
  }

  override fun getLayoutId(): Int = R.layout.controller_floating_list_menu
}
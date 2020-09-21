package com.github.k1rakishou.chan.core.model

import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

data class SiteBoards(
  val siteDescriptor: SiteDescriptor,
  val boards: List<ChanBoard>
)
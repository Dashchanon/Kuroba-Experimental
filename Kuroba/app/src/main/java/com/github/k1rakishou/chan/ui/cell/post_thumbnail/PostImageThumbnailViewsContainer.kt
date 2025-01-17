package com.github.k1rakishou.chan.ui.cell.post_thumbnail

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.model.data.post.ChanPostImage
import java.util.*

class PostImageThumbnailViewsContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : ViewGroup(context, attributeSet, defAttrStyle) {
  private var thumbnailViews: MutableList<PostImageThumbnailViewContract>? = null
  private var postCellThumbnailCallbacks: PostCellThumbnailCallbacks? = null
  private var horizPaddingPx = 0

  private val cachedThumbnailViewContainerInfoArray = arrayOf(
    // PRE_BIND
    CachedThumbnailViewContainerInfo(),
    // BIND
    CachedThumbnailViewContainerInfo()
  )

  fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    val thumbnails = thumbnailViews
      ?: return null

    for (thumbnailViewContract in thumbnails) {
      if (thumbnailViewContract.equalUrls(postImage)) {
        return thumbnailViewContract.getThumbnailView()
      }
    }

    return null
  }

  fun preBind(
    postCellThumbnailCallbacks: PostCellThumbnailCallbacks,
    postCellData: PostCellData,
    horizPaddingPx: Int,
    vertPaddingPx: Int
  ) {
    if (thumbnailViews != null && postCellDataIsTheSame(PRE_BIND, postCellData)) {
      // Images are already bound and haven't changed since the last bind, do nothing
      return
    }

    this.postCellThumbnailCallbacks = postCellThumbnailCallbacks
    this.horizPaddingPx = horizPaddingPx
    cachedThumbnailViewContainerInfoArray[PRE_BIND].updateFrom(postCellData)

    when {
      postCellData.post.postImages.size == 1 -> {
        this.setVisibilityFast(View.VISIBLE)
        this.updatePadding(
          left = horizPaddingPx,
          right = horizPaddingPx,
          top = vertPaddingPx,
          bottom = vertPaddingPx
        )
      }
      postCellData.post.postImages.size > 1 -> {
        this.setVisibilityFast(View.VISIBLE)
        this.updatePadding(
          left = horizPaddingPx,
          right = horizPaddingPx,
          top = MULTIPLE_THUMBNAILS_VERTICAL_PADDING,
          bottom = MULTIPLE_THUMBNAILS_VERTICAL_PADDING
        )
      }
      else -> {
        this.setVisibilityFast(View.GONE)
      }
    }
  }

  fun bindPostImages(postCellData: PostCellData) {
    if (thumbnailViews != null && postCellDataIsTheSame(BIND, postCellData)) {
      // Images are already bound and haven't changed since the last bind, do nothing
      return
    }

    cachedThumbnailViewContainerInfoArray[BIND].updateFrom(postCellData)

    if (postCellData.postImages.isEmpty() || ChanSettings.textOnly.get()) {
      unbindPostImages()
      return
    }

    unbindPostImages()

    if (postCellData.postImages.size <= 1 || postCellData.postMultipleImagesCompactMode) {
      bindZeroOrOneImage(postCellData)
      return
    }

    val postCellDataWidthNoPaddings = cachedThumbnailViewContainerInfoArray[BIND].postCellDataWidthNoPaddings

    // postCellDataWidthNoPaddings is the width of the recyclerview where the posts are displayed.
    // But each post has paddings and we need to account for them, otherwise when displaying multiple
    // thumbnails that may not fit into the container.
    var actualWidth = postCellDataWidthNoPaddings - this.paddingLeft - this.paddingRight

    // Don't forget to account for the "go to post" button which is shown when opening post replies
    // or search.
    if (postCellData.postViewMode.canShowGoToPostButton()) {
      actualWidth -= GO_TO_POST_BUTTON_WIDTH
    }

    bindMoreThanOneImage(actualWidth, postCellData)
  }

  fun unbindContainer() {
    unbindPostImages()

    cachedThumbnailViewContainerInfoArray[PRE_BIND].unbindEverything()
    cachedThumbnailViewContainerInfoArray[BIND].unbindEverything()
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun bindMoreThanOneImage(postCellWidth: Int, postCellData: PostCellData) {
    check(postCellWidth > 0) { "Bad postCellWidth: ${postCellWidth}" }

    check(postCellData.post.postImages.size > 1) {
      "Bad post images count: ${postCellData.post.postImages.size}"
    }

    val prevChanPostImages = cachedThumbnailViewContainerInfoArray[BIND].prevChanPostImages

    if (thumbnailViews != null || (prevChanPostImages != null && imagesAreTheSame(childCount, prevChanPostImages))) {
      // The post was unbound while we were waiting for the layout to happen
      return
    }

    val postAlignmentMode = postCellData.postAlignmentMode
    val postCellCallback = postCellData.postCellCallback
    val resultThumbnailViews = mutableListOf<PostImageThumbnailViewContract>()
    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()

    for ((index, postImage) in postCellData.postImages.withIndex()) {
      if (postImage.imageUrl == null && postImage.actualThumbnailUrl == null) {
        continue
      }

      val (thumbnailView, needAddToParent) = getOrCreateThumbnailView(index, postAlignmentMode)

      thumbnailView.setViewId(View.generateViewId())
      thumbnailView.bindActualThumbnailSizes(cellPostThumbnailSize)

      thumbnailView.bindPostImage(postImage, true, ThumbnailView.ThumbnailViewOptions(drawRipple = false))
      thumbnailView.bindPostInfo(postCellData, postImage)

      if (postCellData.isSelectionMode) {
        thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN, null)
        thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN, null)

        // We need to explicitly set clickable/long clickable to false here because calling
        // setOnClickListener/setOnLongClickListener will automatically set them to true even if
        // the listeners are null.
        thumbnailView.setImageClickable(false)
        thumbnailView.setImageLongClickable(false)
      } else {
        thumbnailView.setImageClickable(true)
        thumbnailView.setImageLongClickable(true)

        // Always set the click listener to avoid check the file cache (which will touch the
        // disk and if you are not lucky enough it may freeze for quite a while). We do all
        // the necessary checks when clicking an image anyway, so no point in doing them
        // twice and more importantly inside RecyclerView bind call
        thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN) {
          postCellCallback?.onThumbnailClicked(
            postCellData = postCellData,
            postImage = postImage
          )
        }
        thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN) {
          postCellThumbnailCallbacks?.requestParentDisallowInterceptTouchEvents(true)
          postCellCallback?.onThumbnailLongClicked(
            chanDescriptor = postCellData.chanDescriptor,
            postImage = postImage
          )
          return@setImageLongClickListener true
        }
      }

      if (needAddToParent) {
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        this.addView(thumbnailView, layoutParams)
      }

      resultThumbnailViews += thumbnailView
    }

    removeExtraViewsIfNeeded(resultThumbnailViews.size)
    thumbnailViews = resultThumbnailViews
  }

  private fun imagesAreTheSame(childCount: Int, prevChanPostImages: MutableList<ChanPostImage>): Boolean {
    if (childCount != prevChanPostImages.size) {
      return false
    }

    for (index in 0 until childCount) {
      val postImageThumbnailViewContainer = getChildAt(index) as? PostImageThumbnailViewContainer
        ?: return false

      val imageUrl = prevChanPostImages[index].imageUrl?.toString()
      val actualThumbnailUrl = prevChanPostImages[index].actualThumbnailUrl?.toString()
      val spoilerThumbnailUrl = prevChanPostImages[index].spoilerThumbnailUrl?.toString()
      val cachedImageUrl = postImageThumbnailViewContainer.actualThumbnailView.imageUrl()

      if (cachedImageUrl != imageUrl && cachedImageUrl != actualThumbnailUrl && cachedImageUrl != spoilerThumbnailUrl) {
        return false
      }
    }

    return true
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun bindZeroOrOneImage(postCellData: PostCellData) {
    if (postCellData.postImages.isEmpty()) {
      return
    }

    val postImage = postCellData.firstImage
      ?: return

    val postAlignmentMode = postCellData.postAlignmentMode
    val postCellCallback = postCellData.postCellCallback
    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()
    val resultThumbnailViews = mutableListOf<PostImageThumbnailViewContract>()

    if (postImage.imageUrl == null && postImage.actualThumbnailUrl == null) {
      return
    }

    val (thumbnailView, needAddToParent) = getOrCreateThumbnailView(0, postAlignmentMode)

    thumbnailView.bindActualThumbnailSizes(cellPostThumbnailSize)
    thumbnailView.setViewId(View.generateViewId())
    thumbnailView.bindPostImage(postImage, true, ThumbnailView.ThumbnailViewOptions())
    thumbnailView.bindPostInfo(postCellData, postImage)

    if (postCellData.isSelectionMode) {
      thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN, null)
      thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN, null)

      // We need to explicitly set clickable/long clickable to false here because calling
      // setOnClickListener/setOnLongClickListener will automatically set them to true even if
      // the listeners are null.
      thumbnailView.setImageClickable(false)
      thumbnailView.setImageLongClickable(false)
    } else {
      thumbnailView.setImageClickable(true)
      thumbnailView.setImageLongClickable(true)

      // Always set the click listener to avoid check the file cache (which will touch the
      // disk and if you are not lucky enough it may freeze for quite a while). We do all
      // the necessary checks when clicking an image anyway, so no point in doing them
      // twice and more importantly inside RecyclerView bind call
      thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN) {
        postCellCallback?.onThumbnailClicked(
          postCellData = postCellData,
          postImage = postImage
        )
      }
      thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN) {
        postCellThumbnailCallbacks?.requestParentDisallowInterceptTouchEvents(true)
        postCellCallback?.onThumbnailLongClicked(
          chanDescriptor = postCellData.chanDescriptor,
          postImage = postImage
        )
        return@setImageLongClickListener true
      }
      thumbnailView.setImageOmittedFilesClickListener(THUMBNAIL_OMITTED_FILES_CLICK_TOKEN) {
        postCellCallback?.onThumbnailOmittedFilesClicked(
          postCellData = postCellData,
          postImage = postImage
        )
      }
    }

    if (needAddToParent) {
      val layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      this.addView(thumbnailView, layoutParams)
    }

    resultThumbnailViews += thumbnailView

    removeExtraViewsIfNeeded(resultThumbnailViews.size)
    thumbnailViews = resultThumbnailViews
  }

  private fun removeExtraViewsIfNeeded(newViewsCount: Int) {
    if (newViewsCount >= childCount) {
      return
    }

    var toDelete = childCount - newViewsCount
    var childIndexToDelete = childCount - 1

    while (toDelete > 0) {
      removeViewAt(childIndexToDelete)

      --childIndexToDelete
      --toDelete
    }
  }

  private fun getOrCreateThumbnailView(
    index: Int,
    postAlignmentMode: ChanSettings.PostAlignmentMode
  ): Pair<PostImageThumbnailViewContainer, Boolean> {
    val reversed = postAlignmentMode == ChanSettings.PostAlignmentMode.AlignLeft

    var view = getChildAt(index)
    if (view != null && view is PostImageThumbnailViewContainer && view.reversed == reversed) {
      return view to false
    }

    if (view != null) {
      removeViewAt(index)
    }

    view = PostImageThumbnailViewContainer(context, reversed)
    return view to true
  }

  private fun unbindPostImages() {
    thumbnailViews?.forEach { thumbnailView ->
      thumbnailView.unbindPostImage()
    }

    thumbnailViews?.clear()
    thumbnailViews = null
  }

  private fun postCellDataIsTheSame(index: Int, postCellData: PostCellData): Boolean {
    return cachedThumbnailViewContainerInfoArray[index].isTheSame(postCellData)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val imagesCount = childCount
    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()

    if (imagesCount <= 1) {
      if (imagesCount == 0) {
        setMeasuredDimension(0, 0)
      } else {
        val child = getChildAt(0)

        child.measure(
          MeasureSpec.makeMeasureSpec(cellPostThumbnailSize, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(cellPostThumbnailSize, MeasureSpec.EXACTLY),
        )

        setMeasuredDimension(
          cellPostThumbnailSize + paddingLeft + paddingRight,
          cellPostThumbnailSize + paddingTop + paddingBottom
        )
      }

      return
    }

    val isMirrored = cachedThumbnailViewContainerInfoArray.get(BIND)
      .postAlignmentMode == ChanSettings.PostAlignmentMode.AlignLeft

    val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
    val neededWidthPerImage = cellPostThumbnailSize + POST_THUMBNAIL_FILE_INFO_SIZE
    val columnsPerRow = (viewWidth / neededWidthPerImage).coerceAtLeast(1)
    val rowsCount = Math.ceil(imagesCount.toDouble() / columnsPerRow.toDouble()).toInt()
    val actualImageWidth = viewWidth / columnsPerRow
    var totalHeight = paddingTop + paddingBottom

    for (rowIndex in 0 until rowsCount) {
      var highestChildOfRow = 0

      for (columnIndex in 0 until columnsPerRow) {
        val actualChildIndex = (rowIndex * columnsPerRow) + columnIndex
        val child: View? = getChildAt(actualChildIndex)

        if (child == null) {
          break
        }

        if (isMirrored) {
          child.updatePadding(left = MULTIPLE_THUMBNAILS_INTERNAL_PADDING, bottom = MULTIPLE_THUMBNAILS_INTERNAL_PADDING)
        } else {
          child.updatePadding(right = MULTIPLE_THUMBNAILS_INTERNAL_PADDING, bottom = MULTIPLE_THUMBNAILS_INTERNAL_PADDING)
        }

        child.measure(
          MeasureSpec.makeMeasureSpec(actualImageWidth - MULTIPLE_THUMBNAILS_INTERNAL_PADDING, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        highestChildOfRow = Math.max(highestChildOfRow, (child.measuredHeight + MULTIPLE_THUMBNAILS_INTERNAL_PADDING))
      }

      totalHeight += highestChildOfRow
    }

    setMeasuredDimension(
      MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY)
    )
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val imagesCount = childCount
    if (imagesCount <= 0) {
      return
    }

    if (imagesCount == 1) {
      val child = getChildAt(0)
      child.layout(paddingLeft, paddingTop, measuredWidth - paddingRight, measuredHeight - paddingBottom)
      return
    }

    val isMirrored = cachedThumbnailViewContainerInfoArray.get(BIND)
      .postAlignmentMode == ChanSettings.PostAlignmentMode.AlignLeft

    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()
    val neededWidthPerImage = if (imagesCount == 1) {
      cellPostThumbnailSize
    } else {
      cellPostThumbnailSize + POST_THUMBNAIL_FILE_INFO_SIZE
    }
    val columnsPerRow = (this.measuredWidth / neededWidthPerImage).coerceAtLeast(1)
    val rowsCount = Math.ceil(imagesCount.toDouble() / columnsPerRow.toDouble()).toInt()
    val actualImageWidth = this.measuredWidth / columnsPerRow

    var curTop = 0
    var curWidth = 0
    var curHeight = 0

    val horizPadding = if (imagesCount > 1) {
      MULTIPLE_THUMBNAILS_INTERNAL_PADDING
    } else {
      0
    }

    for (rowIndex in 0 until rowsCount) {
      var highestChildOfRow = 0

      // Measure children of this row and calculate the highest child which will be the row's height
      for (columnIndex in 0 until columnsPerRow) {
        val actualChildIndex = (rowIndex * columnsPerRow) + columnIndex
        val child: View? = getChildAt(actualChildIndex)

        if (child == null) {
          break
        }

        if (child.visibility == GONE) {
          continue
        }

        if (imagesCount > 1) {
          if (isMirrored) {
            child.updatePadding(left = horizPadding, bottom = horizPadding)
          } else {
            child.updatePadding(right = horizPadding, bottom = horizPadding)
          }
        }

        child.measure(
          MeasureSpec.makeMeasureSpec(actualImageWidth - horizPadding, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        highestChildOfRow = Math.max(highestChildOfRow, child.measuredHeight)
      }

      var curLeft = if (isMirrored) {
        val thumbnailsWidthSum = (0 until columnsPerRow)
          .sumOf { index -> getChildAt(index)?.measuredWidth ?: 0 }

        this.measuredWidth - thumbnailsWidthSum - horizPadding
      } else {
        0
      }

      // Layout the children
      for (columnIndex in 0 until columnsPerRow) {
        val actualChildIndex = (rowIndex * columnsPerRow) + columnIndex
        val child: View? = getChildAt(actualChildIndex)

        if (child == null) {
          break
        }

        if (child.visibility == GONE) {
          continue
        }

        curWidth = child.measuredWidth
        curHeight = highestChildOfRow

        child.layout(curLeft, curTop, curLeft + curWidth, curTop + curHeight)
        curLeft += curWidth
      }

      if (isMirrored) {
        curTop += highestChildOfRow
      } else {
        curTop += highestChildOfRow
      }
    }
  }

  data class CachedThumbnailViewContainerInfo(
    var prevChanPostImages: MutableList<ChanPostImage>? = null,
    var prevBoardPostViewMode: ChanSettings.BoardPostViewMode? = null,
    var postFileInfosHash: MurmurHashUtils.Murmur3Hash? = null,
    var postAlignmentMode: ChanSettings.PostAlignmentMode? = null,
    var postCellDataWidthNoPaddings: Int = 0,
    var postCellThumbnailSizePercents: Int = 0,
    var canShowGoToPostButton: Boolean = false,
  ) {

    fun updateFrom(postCellData: PostCellData) {
      this.prevChanPostImages = postCellData.postImages.toMutableList()
      this.prevBoardPostViewMode = postCellData.boardPostViewMode
      this.postAlignmentMode = postCellData.postAlignmentMode
      this.postFileInfosHash = postCellData.postFileInfoMapHash.copy()
      this.postCellDataWidthNoPaddings = postCellData.postCellDataWidthNoPaddings
      this.postCellThumbnailSizePercents = postCellData.postCellThumbnailSizePercents
      this.canShowGoToPostButton = postCellData.postViewMode.canShowGoToPostButton()
    }

    fun isTheSame(postCellData: PostCellData): Boolean {
      return this.prevChanPostImages != null
        && this.prevChanPostImages == postCellData.postImages
        && this.prevBoardPostViewMode == postCellData.boardPostViewMode
        && this.postAlignmentMode == postCellData.postAlignmentMode
        && this.postFileInfosHash == postCellData.postFileInfoMapHash
        && this.postCellDataWidthNoPaddings == postCellData.postCellDataWidthNoPaddings
        && this.postCellThumbnailSizePercents == postCellData.postCellThumbnailSizePercents
        && this.canShowGoToPostButton == postCellData.postViewMode.canShowGoToPostButton()
    }

    fun unbindEverything() {
      prevChanPostImages = null
      prevBoardPostViewMode = null
      postAlignmentMode = null
      postFileInfosHash = null
      postCellDataWidthNoPaddings = 0
      postCellThumbnailSizePercents = 0
      canShowGoToPostButton = false
    }
  }

  interface PostCellThumbnailCallbacks {
    fun requestParentDisallowInterceptTouchEvents(disallow: Boolean)
  }

  companion object {
    private val MULTIPLE_THUMBNAILS_VERTICAL_PADDING = dp(4f)
    private val MULTIPLE_THUMBNAILS_INTERNAL_PADDING = dp(6f)
    private val GO_TO_POST_BUTTON_WIDTH = getDimen(R.dimen.go_to_post_button_width)
    private val POST_THUMBNAIL_FILE_INFO_SIZE = getDimen(R.dimen.cell_post_thumbnail_container_file_info_size)
    private val CELL_POST_THUMBNAIL_SIZE_MAX = getDimen(R.dimen.cell_post_thumbnail_size_max).toFloat()

    const val THUMBNAIL_CLICK_TOKEN = "POST_THUMBNAIL_VIEW_CLICK"
    const val THUMBNAIL_LONG_CLICK_TOKEN = "POST_THUMBNAIL_VIEW_LONG_CLICK"
    const val THUMBNAIL_OMITTED_FILES_CLICK_TOKEN = "THUMBNAIL_OMITTED_FILES_CLICK_TOKEN"

    const val PRE_BIND = 0
    const val BIND = 1

    fun calculatePostCellSingleThumbnailSize(): Int {
      val postCellThumbnailSizePercent = CELL_POST_THUMBNAIL_SIZE_MAX / 100f
      val newSize = ChanSettings.postCellThumbnailSizePercents.get() * postCellThumbnailSizePercent

      return newSize.toInt()
    }
  }

}
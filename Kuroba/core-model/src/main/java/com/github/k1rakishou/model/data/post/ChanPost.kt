package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.common.copy
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

open class ChanPost(
  val chanPostId: Long,
  val postDescriptor: PostDescriptor,
  val postImages: List<ChanPostImage>,
  val postIcons: List<ChanPostHttpIcon>,
  val repliesTo: Set<PostDescriptor>,
  val timestamp: Long = -1L,
  val postComment: PostComment,
  val subject: CharSequence?,
  val fullTripcode: CharSequence?,
  val name: String? = null,
  val posterId: String? = null,
  val moderatorCapcode: String? = null,
  val isSavedReply: Boolean = false,
  repliesFrom: Set<PostDescriptor>? = null,
  deleted: Boolean = false
) {
  /**
   * We use this array to avoid infinite loops when binding posts since after all post content
   * loaders have done their jobs we update the post via notifyItemChange, which triggers
   * onPostBind() again.
   */
  private val onDemandContentLoadedArray = Array<Boolean>(LoaderType.COUNT) { false }

  @get:Synchronized
  @set:Synchronized
  var isDeleted: Boolean = deleted

  @get:Synchronized
  val repliesFrom = mutableSetOf<PostDescriptor>()

  @Synchronized
  fun postNo(): Long = postDescriptor.postNo
  @Synchronized
  fun postSubNo(): Long = postDescriptor.postSubNo
  @Synchronized
  fun firstImage(): ChanPostImage? = postImages.firstOrNull()

  @Synchronized
  fun isOP(): Boolean = postDescriptor.isOP()

  @get:Synchronized
  val repliesFromCount: Int
    get() = repliesFrom.size
  @get:Synchronized
  val postImagesCount: Int
    get() = postImages.size

  @get:Synchronized
  open val catalogRepliesCount: Int
    get() = 0
  @get:Synchronized
  open val catalogImagesCount: Int
    get() = 0
  @get:Synchronized
  open val uniqueIps: Int
    get() = 0

  val boardDescriptor: BoardDescriptor
    get() = postDescriptor.boardDescriptor()

  @get:Synchronized
  val actualTripcode: String? by lazy {
    val tripcodeString = if (fullTripcode.isNullOrEmpty()) {
      return@lazy null
    } else {
      fullTripcode.trim()
    }

    val index = tripcodeString.lastIndexOf(" ")
    if (index < 0) {
      return@lazy null
    }

    val actualTripcodeMaybe = tripcodeString.substring(startIndex = index + 1)
    if (!actualTripcodeMaybe.startsWith("!")) {
      return@lazy null
    }

    return@lazy actualTripcodeMaybe
  }

  init {
    for (loaderType in LoaderType.values()) {
      onDemandContentLoadedArray[loaderType.arrayIndex] = false
    }

    repliesFrom?.let { replies -> this.repliesFrom.addAll(replies) }
  }

  open fun deepCopy(overrideDeleted: Boolean? = null): ChanPost {
    return ChanPost(
      chanPostId = chanPostId,
      postDescriptor = postDescriptor,
      postImages = postImages,
      postIcons = postIcons,
      repliesTo = repliesTo,
      timestamp = timestamp,
      postComment = postComment.copy(),
      subject = subject.copy(),
      fullTripcode = fullTripcode.copy(),
      name = name,
      posterId = posterId,
      moderatorCapcode = moderatorCapcode,
      isSavedReply = isSavedReply,
      repliesFrom = repliesFrom,
      deleted = overrideDeleted ?: isDeleted
    ).also { newPost ->
      newPost.replaceOnDemandContentLoadedArray(this.copyOnDemandContentLoadedArray())
    }
  }

  @Synchronized
  open fun isContentLoadedForLoader(loaderType: LoaderType): Boolean {
    return onDemandContentLoadedArray[loaderType.arrayIndex]
  }

  @Synchronized
  open fun setContentLoadedForLoader(
    loaderType: LoaderType,
    loaded: Boolean = true
  ) {
    onDemandContentLoadedArray[loaderType.arrayIndex] = loaded
  }

  @Synchronized
  open fun allLoadersCompletedLoading(): Boolean {
    return onDemandContentLoadedArray
      .all { loaderContentLoadState -> loaderContentLoadState }
  }

  @Synchronized
  fun copyOnDemandContentLoadedArray(): Array<Boolean> {
    val newArray = Array<Boolean>(LoaderType.COUNT) { false }

    onDemandContentLoadedArray.forEachIndexed { index, loaderContentLoadState ->
      newArray[index] = loaderContentLoadState
    }

    return newArray
  }

  @Synchronized
  fun replaceOnDemandContentLoadedArray(newArray: Array<Boolean>) {
    for (index in onDemandContentLoadedArray.indices) {
      onDemandContentLoadedArray[index] = newArray[index]
    }
  }

  @Synchronized
  fun onDemandContentLoadedMapsDiffer(
    thisArray: Array<Boolean>,
    otherArray: Array<Boolean>
  ): Boolean {
    if (thisArray.size != otherArray.size) {
      return true
    }

    for (index in thisArray.indices) {
      val thisLoaderState = thisArray[index]
      val otherLoaderState = otherArray[index]

      if (thisLoaderState != otherLoaderState) {
        return true
      }
    }

    return false
  }

  @Synchronized
  open fun iterateRepliesFrom(iterator: (PostDescriptor) -> Unit) {
    for (replyNo in repliesFrom) {
      iterator.invoke(replyNo)
    }
  }

  @Synchronized
  fun firstPostImageOrNull(predicate: (ChanPostImage) -> Boolean): ChanPostImage? {
    for (postImage in postImages) {
      if (predicate.invoke(postImage)) {
        return postImage
      }
    }

    return null
  }

  @Synchronized
  fun iteratePostImages(iterator: (ChanPostImage) -> Unit) {
    for (postImage in postImages) {
      iterator.invoke(postImage)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChanPost) return false

    if (postDescriptor != other.postDescriptor) {
      return false
    }
    if (!arePostImagesTheSame(other)) {
      return false
    }
    if (!arePostIconsTheSame(other)) {
      return false
    }
    if (postComment != other.postComment) {
      return false
    }
    if (subject != other.subject) {
      return false
    }
    if (name != other.name) {
      return false
    }
    if (fullTripcode != other.fullTripcode) {
      return false
    }
    if (posterId != other.posterId) {
      return false
    }
    if (moderatorCapcode != other.moderatorCapcode) {
      return false
    }
    if (isSavedReply != other.isSavedReply) {
      return false
    }

    if (!onDemandContentLoadedMapsDiffer(onDemandContentLoadedArray, other.onDemandContentLoadedArray)) {
      return false
    }

    return true
  }

  private fun arePostIconsTheSame(other: ChanPost): Boolean {
    if (postIcons.size != other.postIcons.size) {
      return false
    }

    return postIcons.indices.none { postIcons[it] != other.postIcons[it] }
  }

  private fun arePostImagesTheSame(other: ChanPost): Boolean {
    if (postImages.size != other.postImages.size) {
      return false
    }

    return postImages.indices.none { postImages[it] != other.postImages[it] }
  }

  override fun hashCode(): Int {
    var result = chanPostId.hashCode()
    result = 31 * result + postDescriptor.hashCode()
    result = 31 * result + repliesTo.hashCode()
    result = 31 * result + postImages.hashCode()
    result = 31 * result + postComment.hashCode()
    result = 31 * result + subject.hashCode()
    result = 31 * result + (name?.hashCode() ?: 0)
    result = 31 * result + fullTripcode.hashCode()
    result = 31 * result + (posterId?.hashCode() ?: 0)
    result = 31 * result + (moderatorCapcode?.hashCode() ?: 0)
    result = 31 * result + isSavedReply.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanPost{" +
      "chanPostId=" + chanPostId +
      ", postDescriptor=" + postDescriptor +
      ", postImages=" + postImages.size +
      ", subject='" + subject + '\'' +
      ", postComment=" + postComment.originalComment().take(64) +
      '}'
  }

}
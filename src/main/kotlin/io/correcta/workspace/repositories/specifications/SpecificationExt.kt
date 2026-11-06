package io.correcta.workspace.repositories.specifications

object SpecificationExt {

  const val LIKE_ESCAPE_CHAR = '\\'

  fun String.toContainsPattern(): String {
    val escaped = trim()
      .lowercase()
      .replace("\\", "\\\\")
      .replace("%", "\\%")
      .replace("_", "\\_")

    return "%$escaped%"
  }
}
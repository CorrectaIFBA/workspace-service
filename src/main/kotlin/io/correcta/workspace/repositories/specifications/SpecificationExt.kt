package io.correcta.workspace.repositories.specifications

/** Helpers for `LIKE` based specifications. */
object SpecificationExt {

  /** Escape character to pass to `CriteriaBuilder.like` together with [toContainsPattern]. */
  const val LIKE_ESCAPE_CHAR = '\\'

  /** Lowercase `%value%` pattern with backslash, `%` and `_` escaped, for a case-insensitive contains match. */
  fun String.toContainsPattern(): String {
    val escaped = trim()
      .lowercase()
      .replace("\\", "\\\\")
      .replace("%", "\\%")
      .replace("_", "\\_")

    return "%$escaped%"
  }
}
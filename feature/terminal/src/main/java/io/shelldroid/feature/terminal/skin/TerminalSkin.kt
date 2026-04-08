package io.shelldroid.feature.terminal.skin

/**
 * A terminal visual theme: typography + 16-color ANSI palette + special
 * foreground / background / cursor colors.
 *
 * Every color is an ARGB int (0xAARRGGBB). The alpha byte is typically
 * `0xff` for fully opaque.
 *
 * This data structure is intentionally plain so it can later be:
 *  - persisted in DataStore as JSON,
 *  - shipped as a built-in set,
 *  - imported from user-supplied `.itermcolors` / `.Xresources` / Termux
 *    `colors.properties` files.
 *
 * Built-in skins live in [BuiltInSkins]. New skins can be added there or
 * through a future [TerminalSkinRepository.save] once DataStore is wired.
 */
data class TerminalSkin(
    val id: String,
    val name: String,
    val textSizeSp: Float,
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    /** Exactly 16 entries — ANSI 0..7 regular + 8..15 bright. */
    val ansi: IntArray,
) {
    init {
        require(ansi.size == 16) { "ansi palette must have 16 entries, got ${ansi.size}" }
    }

    // data class with IntArray: override equals/hashCode to use contentEquals.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalSkin) return false
        return id == other.id &&
            name == other.name &&
            textSizeSp == other.textSizeSp &&
            background == other.background &&
            foreground == other.foreground &&
            cursor == other.cursor &&
            ansi.contentEquals(other.ansi)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + textSizeSp.hashCode()
        result = 31 * result + background
        result = 31 * result + foreground
        result = 31 * result + cursor
        result = 31 * result + ansi.contentHashCode()
        return result
    }
}

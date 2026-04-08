package io.shelldroid.feature.terminal.skin

/**
 * Built-in terminal skins. Two are enough to prove the infrastructure is
 * skin-agnostic; more can be added by defining new [TerminalSkin] constants
 * and appending them to [ALL].
 *
 * Color values lifted from well-known public palettes so users recognize
 * them. The default ("ShellDroid Dark") is a slight tweak of Termux's own
 * default scheme to guarantee high contrast on both OLED and LCD panels.
 */
object BuiltInSkins {

    val SHELLDROID_DARK = TerminalSkin(
        id = "shelldroid-dark",
        name = "ShellDroid Dark",
        textSizeSp = 12f,
        background = 0xff0a0e14.toInt(),
        foreground = 0xffe6e1cf.toInt(),
        cursor = 0xffffcc66.toInt(),
        ansi = intArrayOf(
            0xff0a0e14.toInt(), // 0 black
            0xffef5350.toInt(), // 1 red
            0xff91b362.toInt(), // 2 green
            0xffffcc66.toInt(), // 3 yellow
            0xff53bdfa.toInt(), // 4 blue
            0xffd4bfff.toInt(), // 5 magenta
            0xff90e1c6.toInt(), // 6 cyan
            0xffe6e1cf.toInt(), // 7 white
            0xff686868.toInt(), // 8 bright black
            0xfff07178.toInt(), // 9 bright red
            0xffc2d94c.toInt(), // 10 bright green
            0xffffb454.toInt(), // 11 bright yellow
            0xff59c2ff.toInt(), // 12 bright blue
            0xffe2bbff.toInt(), // 13 bright magenta
            0xff95e6cb.toInt(), // 14 bright cyan
            0xfff8f8f2.toInt(), // 15 bright white
        ),
    )

    val SOLARIZED_DARK = TerminalSkin(
        id = "solarized-dark",
        name = "Solarized Dark",
        textSizeSp = 12f,
        background = 0xff002b36.toInt(),
        foreground = 0xff839496.toInt(),
        cursor = 0xff93a1a1.toInt(),
        ansi = intArrayOf(
            0xff073642.toInt(), // base02
            0xffdc322f.toInt(), // red
            0xff859900.toInt(), // green
            0xffb58900.toInt(), // yellow
            0xff268bd2.toInt(), // blue
            0xffd33682.toInt(), // magenta
            0xff2aa198.toInt(), // cyan
            0xffeee8d5.toInt(), // base2
            0xff002b36.toInt(), // base03
            0xffcb4b16.toInt(), // orange
            0xff586e75.toInt(), // base01
            0xff657b83.toInt(), // base00
            0xff839496.toInt(), // base0
            0xff6c71c4.toInt(), // violet
            0xff93a1a1.toInt(), // base1
            0xfffdf6e3.toInt(), // base3
        ),
    )

    val DEFAULT: TerminalSkin = SHELLDROID_DARK

    val ALL: List<TerminalSkin> = listOf(SHELLDROID_DARK, SOLARIZED_DARK)
}

package plugin.logic

private val attemMatcher by lazy {
    """
        (ubind @?[^ ]+)                            # bind a unit
        sensor (\S+) @unit @flag                   # set _flag to unit flag
        op add (\S+) \3 1                          # increment _attem by 1
        jump \d+ greaterThanEq \3 \d+              # break if _attem >= 83
        jump \d+ (?:notEqual|always) ([^ ]+) \2    # loop if _flag != 0 (or always in some variants)
        set \3 0                                   # _attem = 0
        """.replace("\\s+#.+$".toRegex(RegexOption.MULTILINE), "").trimIndent().toRegex()
}

val attemText = """
        print "Please do not use this delivery logic."
        print "It is attem83 logic and is considered bad logic"
        print "as it breaks other delivery logic and even other attem logic."
        print "For more info please go to https://mindustry.dev/attem"
        printflush message1
    """.trimIndent()

fun isAttem(code: String) = attemMatcher.containsMatchIn(code)
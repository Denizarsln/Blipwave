package dev.blipwave.data

/** Testlerde sanal zaman kullanabilmek için saat soyutlaması. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

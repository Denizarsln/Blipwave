package dev.blipwave.util;

/** Testlerde sanal zaman kullanabilmek için saat soyutlaması. */
public interface Clock {
    long nowMillis();

    Clock SYSTEM = System::currentTimeMillis;
}

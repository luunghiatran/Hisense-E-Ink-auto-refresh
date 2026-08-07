package com.liziwa.hisense_autorefresh.util

/**
 * 线程安全的单例 Holder：通过传入构造器实现“带一个参数”的惰性单例。
 * 例：companion object : SingletonHolder<X, Context>(::X)
 */
open class SingletonHolder<out T, in A>(private val constructor: (A) -> T) {

    @Volatile
    private var instance: T? = null

    fun getInstance(arg: A): T =
        instance ?: synchronized(this) {
            instance ?: constructor(arg).also { instance = it }
        }
}
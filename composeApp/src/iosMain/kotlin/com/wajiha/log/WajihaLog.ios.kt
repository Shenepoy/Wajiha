package com.wajiha.log

actual object WajihaLog {
    actual fun d(tag: String, message: String) = Unit
    actual fun i(tag: String, message: String) = Unit
    actual fun w(tag: String, message: String) = Unit
}

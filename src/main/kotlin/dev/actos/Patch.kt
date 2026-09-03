package dev.actos

public sealed interface Patch<out T> {
    public object Unchanged : Patch<Nothing>

    public object Clear : Patch<Nothing>

    public data class Value<T>(public val value: T) : Patch<T>

    public companion object {
        public fun <T> of(value: T?): Patch<T> = if (value != null) Value(value) else Clear
    }
}

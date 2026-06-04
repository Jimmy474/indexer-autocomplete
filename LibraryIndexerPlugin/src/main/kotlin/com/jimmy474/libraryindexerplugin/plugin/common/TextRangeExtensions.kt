package com.jimmy474.libraryindexerplugin.plugin.common

import com.intellij.openapi.util.TextRange

fun IntRange.toTextRange() = TextRange(first, last + 1)

operator fun TextRange.plus(other: TextRange) = TextRange(startOffset + other.startOffset, endOffset + other.endOffset)
operator fun TextRange.plus(other: IntRange) = TextRange(startOffset + other.first, endOffset + other.last)
operator fun TextRange.plus(other: Int) = TextRange(startOffset + other, endOffset + other)

operator fun TextRange.minus(other: TextRange) = TextRange(startOffset - other.startOffset, endOffset - other.endOffset)
operator fun TextRange.minus(other: IntRange) = TextRange(startOffset - other.first, endOffset - other.last)
operator fun TextRange.minus(other: Int) = TextRange(startOffset - other, endOffset - other)

operator fun TextRange.times(other: TextRange) = TextRange(startOffset * other.startOffset, endOffset * other.endOffset)
operator fun TextRange.times(other: IntRange) = TextRange(startOffset * other.first, endOffset * other.last)
operator fun TextRange.times(other: Int) = TextRange(startOffset * other, endOffset * other)

operator fun TextRange.div(other: TextRange) = TextRange(startOffset / other.startOffset, endOffset / other.endOffset)
operator fun TextRange.div(other: IntRange) = TextRange(startOffset / other.first, endOffset / other.last)
operator fun TextRange.div(other: Int) = TextRange(startOffset / other, endOffset / other)

operator fun TextRange.rangeTo(other: TextRange): TextRange = TextRange(minOf(startOffset, other.startOffset), maxOf(endOffset, other.endOffset))
fun TextRange.addStart(other: Int) = TextRange(startOffset + other, endOffset)
fun TextRange.addEnd(other: Int) = TextRange(startOffset, endOffset + other)

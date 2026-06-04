package com.jimmy474.libraryindexerplugin.plugin.libraryindexer

import org.junit.Test

class SimpleTypeNameTest {
    @Test
    fun `simple type name`() {
        assert(simpleTypeName("java.lang.String") == "String")
    }

    @Test
    fun `simple type name with generics`() {
        assert(simpleTypeName("java.util.List<java.lang.String>") == "List<String>")
    }

    @Test
    fun `simple type name with generics and wildcards`() {
        assert(simpleTypeName("java.util.List<? extends java.lang.String>") == "List<? extends String>")
    }

    @Test
    fun `simple type name with generics and wildcards and multiple`() {
        assert(simpleTypeName("java.util.List<? extends java.lang.String, ? super java.lang.Integer>") == "List<? extends String, ? super Integer>")
    }

    @Test
    fun `actual index test`() {
        assert(simpleTypeName("net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block>") == "ResourceKey<Block>")
    }

}
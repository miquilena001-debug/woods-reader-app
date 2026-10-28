package com.example.woodsreader.core.translation.calibre.inplace

data class EpubPackage(
    val opfPath: String,
    val opfDir: String,
    val title: String,
    val creator: String,
    val language: String,
    val readingOrderZipPaths: List<String>,
    val ncxZipPath: String?,
    val navZipPath: String?
)

package com.example.data.statement

import android.content.Context
import android.net.Uri
import com.example.domain.statement.StatementFormatException
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException

/** Reads the text of a PDF the owner picked (e.g. a Google Pay statement), on the phone. */
class PdfTextExtractor(private val context: Context) {

    /** Must run off the main thread. Throws [StatementFormatException] with a message for the owner. */
    fun extract(uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw StatementFormatException("Couldn't open the file.")
            stream.use { input ->
                PDDocument.load(input).use { document ->
                    // Position order keeps each row's fields together whatever order the PDF
                    // stores them in; the parser does not rely on it either way.
                    return PDFTextStripper().apply { sortByPosition = true }.getText(document)
                }
            }
        } catch (e: InvalidPasswordException) {
            throw StatementFormatException("This PDF is password protected. Download it again without a password.")
        } catch (e: IOException) {
            throw StatementFormatException("Couldn't read this PDF: ${e.message ?: "unknown error"}")
        }
    }
}

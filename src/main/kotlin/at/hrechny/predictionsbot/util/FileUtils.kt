package at.hrechny.predictionsbot.util

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.tools.TextToPDF

object FileUtils {
    @JvmStatic
    fun getResourceFileAsString(fileName: String): String? {
        val classLoader = FileUtils::class.java.classLoader
        classLoader.getResourceAsStream(fileName).use { inputStream ->
            if (inputStream == null) {
                return null
            }

            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { inputStreamReader ->
                BufferedReader(inputStreamReader).use { reader ->
                    return reader.lines().collect(Collectors.joining(System.lineSeparator()))
                }
            }
        }
    }

    @JvmStatic
    fun buildPdfDocument(exception: Exception): ByteArray {
        val outputStream = ByteArrayOutputStream()
        PDDocument().use { document ->
            TextToPDF().createPDFFromText(
                document,
                StringReader(ExceptionUtils.getStackTrace(exception).replace("\t", "  ")),
            )
            document.save(outputStream)
        }
        return outputStream.toByteArray()
    }
}

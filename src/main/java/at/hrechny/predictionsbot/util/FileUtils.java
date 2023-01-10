package at.hrechny.predictionsbot.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.tools.TextToPDF;

@Slf4j
public class FileUtils {

  @SneakyThrows
  public static String getResourceFileAsString(String fileName) {
    ClassLoader classLoader = FileUtils.class.getClassLoader();
    try (InputStream is = classLoader.getResourceAsStream(fileName)) {
      if (is == null) {
        return null;
      }
      try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
          BufferedReader reader = new BufferedReader(isr)) {
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
    }
  }

  @SneakyThrows
  public static byte[] buildPdfDocument(Exception exception) {
    var outputStream = new ByteArrayOutputStream();
    try (PDDocument document = new PDDocument()) {
      TextToPDF textToPDF = new TextToPDF();
      textToPDF.createPDFFromText(document, new StringReader(ExceptionUtils.getStackTrace(exception).replaceAll("\t", "  ")));
      document.save(outputStream);
    }
    return outputStream.toByteArray();
  }
}

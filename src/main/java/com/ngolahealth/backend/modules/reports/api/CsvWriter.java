package com.ngolahealth.backend.modules.reports.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * CSV para abrir no Excel em português: separador ";", vírgula decimal e BOM UTF-8 (para os acentos
 * aparecerem bem). Os valores são escapados e protegidos contra injecção de fórmulas.
 */
final class CsvWriter {

  private CsvWriter() {}

  static ResponseEntity<byte[]> file(
      String filename, List<String> header, List<List<Object>> rows) {
    var sb = new StringBuilder("﻿");
    sb.append(line(header.stream().map(h -> (Object) h).toList()));
    rows.forEach(r -> sb.append(line(r)));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String line(List<Object> values) {
    return values.stream().map(CsvWriter::cell).collect(Collectors.joining(";")) + "\r\n";
  }

  private static String cell(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Number n) {
      return n.toString().replace('.', ',');
    }
    var s = value.toString();
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
      s = "'" + s; // impede que o Excel interprete o texto como fórmula
    }
    return s.contains(";") || s.contains("\"") || s.contains("\n")
        ? "\"" + s.replace("\"", "\"\"") + "\""
        : s;
  }
}

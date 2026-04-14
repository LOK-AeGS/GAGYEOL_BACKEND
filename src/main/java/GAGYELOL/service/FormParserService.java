package GAGYELOL.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Service
@Slf4j
public class FormParserService {

    public String extractText(File file) throws IOException {
        String filename = file.getName().toLowerCase();
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return extractFromExcel(file);
        }
        return extractFromDocx(file);
    }

    private String extractFromDocx(File file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            StringBuilder sb = new StringBuilder();

            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText().trim();
                if (!text.isEmpty()) {
                    sb.append(text).append("\n");
                }
            }

            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    row.getTableCells().forEach(cell -> {
                        String text = cell.getText().trim();
                        if (!text.isEmpty()) {
                            sb.append(text).append("\t");
                        }
                    });
                    sb.append("\n");
                }
            }

            return sb.toString();
        }
    }

    private String extractFromExcel(File file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(file))) {
            StringBuilder sb = new StringBuilder();
            DataFormatter formatter = new DataFormatter();

            for (Sheet sheet : workbook) {
                sb.append("[시트: ").append(sheet.getSheetName()).append("]\n");
                for (Row row : sheet) {
                    boolean hasContent = false;
                    StringBuilder rowSb = new StringBuilder();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            rowSb.append(value).append("\t");
                            hasContent = true;
                        }
                    }
                    if (hasContent) {
                        sb.append(rowSb).append("\n");
                    }
                }
            }

            return sb.toString();
        }
    }
}

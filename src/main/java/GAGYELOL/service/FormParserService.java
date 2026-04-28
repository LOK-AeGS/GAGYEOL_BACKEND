package GAGYELOL.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                // 컬럼별 마지막 텍스트 추적 — 세로 병합 셀 반복 출력용
                Map<Integer, String> lastCellText = new HashMap<>();

                for (XWPFTableRow row : table.getRows()) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int i = 0; i < cells.size(); i++) {
                        XWPFTableCell cell = cells.get(i);
                        String text = cell.getText().trim();

                        if (isVMergeContinuation(cell)) {
                            // 세로 병합 연속 셀 → 상위 행의 레이블 재사용
                            text = lastCellText.getOrDefault(i, "");
                        } else {
                            if (!text.isEmpty()) {
                                lastCellText.put(i, text);
                            } else {
                                lastCellText.remove(i);
                            }
                        }

                        if (!text.isEmpty()) {
                            sb.append(text).append("\t");
                        } else {
                            sb.append("\t");
                        }
                    }
                    sb.append("\n");
                }
            }

            return sb.toString();
        }
    }

    private boolean isVMergeContinuation(XWPFTableCell cell) {
        try {
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null || tcPr.getVMerge() == null) return false;
            // vMerge 속성이 있고 val=restart가 아니면 연속 셀
            STMerge.Enum val = tcPr.getVMerge().getVal();
            return val == null || val != STMerge.RESTART;
        } catch (Exception e) {
            return false;
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

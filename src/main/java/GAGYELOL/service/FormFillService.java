package GAGYELOL.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FormFillService {

    /**
     * 파일 확장자에 따라 DOCX 또는 XLSX 채우기를 호출합니다.
     */
    public byte[] fill(String filePath, Map<String, String> allFields) throws IOException {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".docx")) {
            return fillDocx(filePath, allFields);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return fillXlsx(filePath, allFields);
        } else {
            throw new IllegalArgumentException("지원하지 않는 양식 파일 형식: " + filePath);
        }
    }

    /**
     * DOCX 파일의 테이블 셀과 단락에서 필드명을 탐색하여 인접 빈 셀/다음 줄에 값을 채웁니다.
     */
    private byte[] fillDocx(String filePath, Map<String, String> allFields) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(fis)) {

            log.info("DOCX 채우기 시작 - 전달된 필드: {}", allFields.keySet());

            // 테이블에서 필드명 셀 탐색 → 오른쪽 빈 셀에 값 입력
            // 그룹 레이블(예: "지출인")을 컬럼별로 기억해 "지출인 소속" 형태로 매칭
            for (XWPFTable table : doc.getTables()) {
                Map<Integer, String> columnGroupLabels = new java.util.HashMap<>();
                for (XWPFTableRow row : table.getRows()) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int i = 0; i < cells.size(); i++) {
                        String cellText = cells.get(i).getText().trim();
                        log.info("셀 텍스트: [{}]", cellText);
                        if (cellText.isEmpty()) continue;

                        // 이 셀이 그룹 레이블인지 확인 (예: "지출인" → "지출인 소속" 같은 필드가 존재)
                        final String ct = cellText;
                        boolean isGroupLabel = allFields.keySet().stream()
                                .anyMatch(f -> f.startsWith(ct + " "));
                        if (isGroupLabel) {
                            columnGroupLabels.put(i, cellText);
                            continue;
                        }

                        // 왼쪽 컬럼의 그룹 레이블 조합해서 매칭 시도
                        String groupLabel = columnGroupLabels.getOrDefault(i - 1, "");
                        String compoundKey = groupLabel.isEmpty() ? cellText : groupLabel + " " + cellText;

                        for (Map.Entry<String, String> entry : allFields.entrySet()) {
                            String field = entry.getKey();
                            String value = entry.getValue();
                            String normalizedCell = normalize(cellText);
                            String normalizedField = normalize(field);
                            String normalizedCompound = normalize(compoundKey);
                            boolean matches = normalizedCell.contains(normalizedField)
                                    || normalizedCompound.contains(normalizedField);
                            if (!matches) continue;

                            // 1순위: 오른쪽 인접 빈 셀 채우기 (현금지출증빙서 등)
                            if (i + 1 < cells.size()) {
                                XWPFTableCell nextCell = cells.get(i + 1);
                                String nextText = nextCell.getText().trim();
                                if (nextText.isEmpty()) {
                                    setCellText(nextCell, value);
                                    log.info("DOCX 필드 채우기 완료: {} = {}", field, value);
                                    break;
                                } else if (nextText.length() <= 2) {
                                    setCellText(nextCell, value + nextText);
                                    log.info("DOCX 필드 채우기 완료(단위 포함): {} = {}{}", field, value, nextText);
                                    break;
                                }
                            }

                            // 2순위: 인라인 채우기 - 레이블 셀 자체에 값 삽입 (지출 기록부 병합 셀 구조)
                            if (fillInline(cells.get(i), cellText, normalizedField, value)) {
                                log.info("DOCX 필드 채우기 완료(인라인): {} = {}", field, value);
                                break;
                            }
                        }
                    }
                }
            }

            // 단락에서 {{필드명}} 플레이스홀더 교체
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                replacePlaceholders(paragraph, allFields);
            }
            // 테이블 셀 내부 단락도 교체
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph para : cell.getParagraphs()) {
                            replacePlaceholders(para, allFields);
                        }
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private void replacePlaceholders(XWPFParagraph paragraph, Map<String, String> allFields) {
        String text = paragraph.getText();
        boolean changed = false;
        for (Map.Entry<String, String> entry : allFields.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (text.contains(placeholder)) {
                text = text.replace(placeholder, entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            // 모든 run을 제거하고 첫 번째 run에 교체된 텍스트 설정
            for (int i = paragraph.getRuns().size() - 1; i > 0; i--) {
                paragraph.removeRun(i);
            }
            if (!paragraph.getRuns().isEmpty()) {
                paragraph.getRuns().get(0).setText(text, 0);
            }
        }
    }

    private String normalize(String text) {
        return text
                .replace(' ', ' ')  // non-breaking space
                .replace('＆', '&')       // full-width ampersand
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 레이블 셀 자체에 값을 삽입한다 (병합 셀 구조에서 별도 값 칸이 없는 경우).
     * 패턴 1: "레이블 :       (인)" → "레이블 : 값  (인)"
     * 패턴 2: "레이블 :"       → "레이블 : 값"
     * 패턴 3: 셀 텍스트 = 필드명  → 값으로 교체
     */
    private boolean fillInline(XWPFTableCell cell, String rawCellText, String normalizedField, String value) {
        String trimmed = rawCellText.trim();

        if (trimmed.contains(":") && trimmed.contains("(인)")) {
            int colonIdx = trimmed.indexOf(":");
            int inIdx = trimmed.indexOf("(인)");
            if (colonIdx < inIdx) {
                String beforeColon = trimmed.substring(0, colonIdx + 1);
                String inPart = trimmed.substring(inIdx);
                setCellText(cell, beforeColon + " " + value + "  " + inPart);
                return true;
            }
        }

        if (trimmed.endsWith(":")) {
            setCellText(cell, trimmed + " " + value);
            return true;
        }

        if (normalize(trimmed).equals(normalizedField)) {
            clearCellText(cell, value);
            return true;
        }

        return false;
    }

    // 셀의 모든 단락·run을 비우고 첫 run에만 값을 씀 (여러 단락으로 된 레이블 셀 교체용)
    private void clearCellText(XWPFTableCell cell, String value) {
        enableWordWrap(cell);
        boolean valueSet = false;
        for (XWPFParagraph para : cell.getParagraphs()) {
            for (int j = 0; j < para.getRuns().size(); j++) {
                if (!valueSet) {
                    para.getRuns().get(j).setText(value, 0);
                    valueSet = true;
                } else {
                    para.getRuns().get(j).setText("", 0);
                }
            }
        }
        if (!valueSet) {
            if (cell.getParagraphs().isEmpty()) {
                cell.addParagraph().createRun().setText(value);
            } else {
                cell.getParagraphs().get(0).createRun().setText(value);
            }
        }
    }

    private void setCellText(XWPFTableCell cell, String value) {
        enableWordWrap(cell);
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph().createRun().setText(value);
        } else {
            XWPFParagraph para = cell.getParagraphs().get(0);
            if (para.getRuns().isEmpty()) {
                para.createRun().setText(value);
            } else {
                para.getRuns().get(0).setText(value, 0);
            }
        }
    }

    private void enableWordWrap(XWPFTableCell cell) {
        CTTc ctTc = cell.getCTTc();
        CTTcPr tcPr = ctTc.isSetTcPr() ? ctTc.getTcPr() : ctTc.addNewTcPr();
        if (tcPr.isSetNoWrap()) {
            tcPr.unsetNoWrap();
        }
    }

    /**
     * XLSX 파일에서 필드명이 포함된 셀을 탐색하여 인접 빈 셀에 값을 채웁니다.
     */
    private byte[] fillXlsx(String filePath, Map<String, String> allFields) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                for (Row row : sheet) {
                    for (int ci = 0; ci < row.getLastCellNum(); ci++) {
                        Cell cell = row.getCell(ci);
                        if (cell == null || cell.getCellType() != CellType.STRING) continue;
                        String cellText = cell.getStringCellValue().trim();

                        for (Map.Entry<String, String> entry : allFields.entrySet()) {
                            String field = entry.getKey();
                            String value = entry.getValue();
                            if (cellText.contains(field)) {
                                // 오른쪽 빈 셀 우선 채우기, 이미 차 있으면 아래 셀 시도
                                Cell rightCell = row.getCell(ci + 1);
                                if (rightCell == null || rightCell.getCellType() == CellType.BLANK) {
                                    if (rightCell == null) rightCell = row.createCell(ci + 1);
                                    rightCell.setCellValue(value);
                                    log.debug("XLSX 필드 채우기(오른쪽): {} = {}", field, value);
                                } else {
                                    Row nextRow = sheet.getRow(row.getRowNum() + 1);
                                    if (nextRow != null) {
                                        Cell belowCell = nextRow.getCell(ci);
                                        if (belowCell == null || belowCell.getCellType() == CellType.BLANK) {
                                            if (belowCell == null) belowCell = nextRow.createCell(ci);
                                            belowCell.setCellValue(value);
                                            log.debug("XLSX 필드 채우기(아래): {} = {}", field, value);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}

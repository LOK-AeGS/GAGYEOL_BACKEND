package GAGYELOL.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
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
                            boolean matches = field.equals(cellText) || field.equals(compoundKey);
                            if (matches && i + 1 < cells.size()) {
                                XWPFTableCell nextCell = cells.get(i + 1);
                                String nextText = nextCell.getText().trim();
                                if (nextText.isEmpty()) {
                                    setCellText(nextCell, value);
                                    log.info("DOCX 필드 채우기 완료: {} = {}", field, value);
                                } else if (nextText.length() <= 2) {
                                    // "원", "명" 같은 단위 텍스트인 경우 값 앞에 붙여서 채움
                                    setCellText(nextCell, value + nextText);
                                    log.info("DOCX 필드 채우기 완료(단위 포함): {} = {}{}", field, value, nextText);
                                }
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

    private void setCellText(XWPFTableCell cell, String value) {
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
                                // 오른쪽 셀이 비어있으면 값 입력
                                Cell rightCell = row.getCell(ci + 1);
                                if (rightCell == null || rightCell.getCellType() == CellType.BLANK) {
                                    if (rightCell == null) rightCell = row.createCell(ci + 1);
                                    rightCell.setCellValue(value);
                                    log.debug("XLSX 필드 채우기(오른쪽): {} = {}", field, value);
                                }
                                // 아래 셀이 비어있으면 값 입력
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

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}

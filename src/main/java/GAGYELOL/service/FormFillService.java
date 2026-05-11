package GAGYELOL.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FormFillService {

    /**
     * 파일 확장자에 따라 DOCX 또는 XLSX 채우기를 호출합니다.
     * (backward compatibility - 이미지 없이 텍스트 필드만 채움)
     */
    public byte[] fill(String filePath, Map<String, String> allFields) throws IOException {
        return fill(filePath, allFields, Collections.emptyMap());
    }

    /**
     * 핵심 진입점 - 텍스트 필드와 이미지 필드를 함께 채웁니다.
     */
    public byte[] fill(String filePath, Map<String, String> allFields, Map<String, byte[]> imageFieldsBytes) throws IOException {
        if (imageFieldsBytes == null) {
            imageFieldsBytes = Collections.emptyMap();
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".docx")) {
            return fillDocx(filePath, allFields, imageFieldsBytes);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return fillXlsx(filePath, allFields, imageFieldsBytes);
        } else {
            throw new IllegalArgumentException("지원하지 않는 양식 파일 형식: " + filePath);
        }
    }

    /**
     * DOCX 파일의 테이블 셀과 단락에서 필드명을 탐색하여 인접 빈 셀/다음 줄에 값을 채웁니다.
     */
    private byte[] fillDocx(String filePath, Map<String, String> allFields, Map<String, byte[]> imageFieldsBytes) throws IOException {
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

            // 이미지 필드 삽입 - 텍스트 채우기 완료 후 별도 단계로 실행
            if (!imageFieldsBytes.isEmpty()) {
                insertDocxImages(doc, imageFieldsBytes);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    /**
     * DOCX 테이블에서 이미지 플레이스홀더 셀을 찾아 이미지를 삽입합니다.
     * 매칭 우선순위:
     *   1) 셀 텍스트가 필드명과 정확히 일치 → 오른쪽 인접 빈 셀에 삽입
     *   2) 셀 텍스트가 필드명을 포함하거나 그 반대(예: 셀 "학생증 부착(...)" ⊃ 필드 "학생증")
     *   3) 필드명이 이미지성 키워드(사진/이미지/학생증/영수증)이고 셀이 "부착"/"사진"/"이미지" 포함
     * 2)·3)의 경우 인접 빈 셀이 없으면 매칭된 셀 자체에 새 단락으로 이미지 추가(레이블 텍스트는 유지).
     * 같은 셀에 두 번 삽입하지 않도록 추적합니다.
     */
    private void insertDocxImages(XWPFDocument doc, Map<String, byte[]> imageFieldsBytes) {
        java.util.Set<XWPFTableCell> usedCells = new java.util.HashSet<>();

        for (Map.Entry<String, byte[]> imgEntry : imageFieldsBytes.entrySet()) {
            String fieldName = imgEntry.getKey();
            byte[] imageBytes = imgEntry.getValue();
            if (imageBytes == null || imageBytes.length == 0) continue;

            int pictureType = detectPictureType(imageBytes);
            int docxPicType = (pictureType == Workbook.PICTURE_TYPE_PNG)
                    ? XWPFDocument.PICTURE_TYPE_PNG
                    : XWPFDocument.PICTURE_TYPE_JPEG;

            boolean inserted = insertDocxImageInMatchingCell(doc, fieldName, imageBytes, docxPicType, usedCells);
            if (!inserted) {
                log.warn("DOCX 이미지 플레이스홀더를 찾지 못함: {}", fieldName);
            }
        }
    }

    private boolean insertDocxImageInMatchingCell(XWPFDocument doc, String fieldName, byte[] imageBytes,
                                                   int docxPicType, java.util.Set<XWPFTableCell> usedCells) {
        String normalizedField = normalize(fieldName);

        // Pass 1: 정확 일치 → 오른쪽 빈 셀에 삽입 (전통적 2열 레이블/값 구조)
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                List<XWPFTableCell> cells = row.getTableCells();
                for (int i = 0; i < cells.size(); i++) {
                    XWPFTableCell labelCell = cells.get(i);
                    if (usedCells.contains(labelCell)) continue;
                    String cellText = labelCell.getText().trim();
                    if (cellText.isEmpty()) continue;
                    if (!normalize(cellText).equals(normalizedField)) continue;

                    if (i + 1 < cells.size()) {
                        XWPFTableCell next = cells.get(i + 1);
                        if (next.getText().trim().isEmpty() && !usedCells.contains(next)) {
                            if (addPictureToCell(next, fieldName, imageBytes, docxPicType, true)) {
                                usedCells.add(next);
                                return true;
                            }
                        }
                    }
                }
            }
        }

        // Pass 2: 부분 일치 / 키워드 매칭 → 매칭된 셀에 직접 삽입
        boolean fieldIsImageish = isImageishLabel(normalizedField);
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                List<XWPFTableCell> cells = row.getTableCells();
                for (XWPFTableCell cell : cells) {
                    if (usedCells.contains(cell)) continue;
                    String cellTextRaw = cell.getText().trim();
                    if (cellTextRaw.isEmpty()) continue;
                    String cellText = normalize(cellTextRaw);

                    boolean contains = cellText.contains(normalizedField) || normalizedField.contains(cellText);
                    boolean keyword = fieldIsImageish && (cellText.contains("부착")
                            || cellText.contains("사진") || cellText.contains("이미지"));
                    if (!contains && !keyword) continue;

                    if (addPictureToCell(cell, fieldName, imageBytes, docxPicType, false)) {
                        usedCells.add(cell);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isImageishLabel(String normalizedField) {
        return normalizedField.contains("사진") || normalizedField.contains("이미지")
                || normalizedField.contains("학생증") || normalizedField.contains("영수증");
    }

    private boolean addPictureToCell(XWPFTableCell cell, String fieldName, byte[] imageBytes,
                                      int docxPicType, boolean reuseFirstParagraph) {
        try {
            XWPFParagraph para;
            if (reuseFirstParagraph) {
                para = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
            } else {
                // 레이블 텍스트 유지하고 새 단락에 이미지 추가
                para = cell.addParagraph();
            }
            XWPFRun run = para.createRun();
            try (ByteArrayInputStream imgStream = new ByteArrayInputStream(imageBytes)) {
                run.addPicture(imgStream, docxPicType, fieldName,
                        Units.toEMU(150), Units.toEMU(190));
            }
            log.info("DOCX 이미지 삽입 완료: {} ({} bytes)", fieldName, imageBytes.length);
            return true;
        } catch (InvalidFormatException | IOException e) {
            log.warn("DOCX 이미지 삽입 실패: {} - {}", fieldName, e.getMessage());
            return false;
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
                .replace(' ', ' ')  // non-breaking space
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
     * XLSX/XLS 파일에서 필드명이 포함된 셀을 탐색하여 인접 빈 셀에 값을 채웁니다.
     * WorkbookFactory를 사용하여 XLS(구버전 바이너리)도 지원합니다.
     */
    /**
     * IE가 행별 데이터를 한 셀에 ", "(콤마+공백)로 이어 반환하는 형태를 행 단위 리스트로 분리.
     * 값 내부의 천 단위 콤마(예: "9,000")는 공백이 없어 보존됨.
     * 단일 값(또는 분할 결과가 1개)이면 원본 그대로 단일 원소 리스트로 반환.
     */
    private java.util.List<String> splitMultiRowValue(String value) {
        if (value == null) return java.util.Collections.emptyList();
        // "9,000, 16,000, ..." 처럼 콤마 뒤에 공백이 있는 경우에만 분리
        String[] parts = value.split(",\\s+");
        java.util.List<String> result = new java.util.ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }

    private byte[] fillXlsx(String filePath, Map<String, String> allFields, Map<String, byte[]> imageFieldsBytes) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

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
                            if (!cellText.contains(field)) continue;

                            Cell rightCell = row.getCell(ci + 1);
                            boolean rightAvailable = rightCell == null || rightCell.getCellType() == CellType.BLANK;

                            if (rightAvailable) {
                                // 레이블-값 구조(예: "지출인 성명" | (빈 셀)): 오른쪽에 단일 값 기입
                                if (rightCell == null) rightCell = row.createCell(ci + 1);
                                rightCell.setCellValue(value);
                                log.debug("XLSX 필드 채우기(오른쪽): {} = {}", field, value);
                            } else {
                                // 테이블 헤더 구조(예: | 번호 | 날짜 | ...): 아래쪽으로 펼침
                                // IE가 행별 값을 ", "로 이어서 한 문자열로 반환하는 경우(3개 이상) 분할해서 행별로 기입
                                List<String> rowValues = splitMultiRowValue(value);
                                int startRow = row.getRowNum() + 1;
                                if (rowValues.size() >= 3) {
                                    for (int j = 0; j < rowValues.size(); j++) {
                                        Row targetRow = sheet.getRow(startRow + j);
                                        if (targetRow == null) targetRow = sheet.createRow(startRow + j);
                                        Cell target = targetRow.getCell(ci);
                                        if (target == null) target = targetRow.createCell(ci);
                                        else if (target.getCellType() != CellType.BLANK) continue; // 이미 다른 데이터 → 덮어쓰지 않음
                                        target.setCellValue(rowValues.get(j));
                                    }
                                    log.debug("XLSX 필드 채우기(다중행, {}개): {}", rowValues.size(), field);
                                } else {
                                    Row nextRow = sheet.getRow(startRow);
                                    if (nextRow == null) nextRow = sheet.createRow(startRow);
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

            // 이미지 필드 삽입 - 텍스트 채우기 완료 후 별도 단계로 실행
            if (!imageFieldsBytes.isEmpty()) {
                insertXlsxImages(workbook, imageFieldsBytes);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * XLSX/XLS 시트에서 이미지 플레이스홀더 셀을 찾아 인접 영역에 이미지를 삽입합니다.
     * DOCX와 동일한 매칭 정책:
     *   1) 정확 일치 → 오른쪽 인접 영역에 앵커
     *   2) 부분 일치(셀⊃필드 or 필드⊃셀)
     *   3) 필드가 이미지성이고 셀이 "부착/사진/이미지" 키워드 포함 → 같은 셀 위치에 앵커
     */
    private void insertXlsxImages(Workbook workbook, Map<String, byte[]> imageFieldsBytes) {
        CreationHelper helper = workbook.getCreationHelper();
        java.util.Set<String> usedCellKeys = new java.util.HashSet<>();

        for (Map.Entry<String, byte[]> imgEntry : imageFieldsBytes.entrySet()) {
            String fieldName = imgEntry.getKey();
            byte[] imageBytes = imgEntry.getValue();
            if (imageBytes == null || imageBytes.length == 0) continue;

            String normalizedField = normalize(fieldName);
            boolean fieldIsImageish = isImageishLabel(normalizedField);
            int pictureType = detectPictureType(imageBytes);
            int pictureIndex = workbook.addPicture(imageBytes, pictureType);

            boolean inserted = false;
            // Pass 1: 정확 일치
            for (int si = 0; si < workbook.getNumberOfSheets() && !inserted; si++) {
                Sheet sheet = workbook.getSheetAt(si);
                for (Row row : sheet) {
                    if (inserted) break;
                    short last = row.getLastCellNum();
                    for (int ci = 0; ci < last; ci++) {
                        Cell cell = row.getCell(ci);
                        if (cell == null || cell.getCellType() != CellType.STRING) continue;
                        String cellText = cell.getStringCellValue().trim();
                        if (cellText.isEmpty()) continue;
                        String key = si + ":" + row.getRowNum() + ":" + ci;
                        if (usedCellKeys.contains(key)) continue;
                        if (!normalize(cellText).equals(normalizedField)) continue;

                        if (anchorPicture(sheet, helper, pictureIndex, row.getRowNum(), ci, fieldName, imageBytes.length, true)) {
                            usedCellKeys.add(key);
                            inserted = true;
                            break;
                        }
                    }
                }
            }
            // Pass 2: 부분 일치 / 키워드 매칭
            for (int si = 0; si < workbook.getNumberOfSheets() && !inserted; si++) {
                Sheet sheet = workbook.getSheetAt(si);
                for (Row row : sheet) {
                    if (inserted) break;
                    short last = row.getLastCellNum();
                    for (int ci = 0; ci < last; ci++) {
                        Cell cell = row.getCell(ci);
                        if (cell == null || cell.getCellType() != CellType.STRING) continue;
                        String cellTextRaw = cell.getStringCellValue().trim();
                        if (cellTextRaw.isEmpty()) continue;
                        String key = si + ":" + row.getRowNum() + ":" + ci;
                        if (usedCellKeys.contains(key)) continue;
                        String cellText = normalize(cellTextRaw);

                        boolean contains = cellText.contains(normalizedField) || normalizedField.contains(cellText);
                        boolean keyword = fieldIsImageish && (cellText.contains("부착")
                                || cellText.contains("사진") || cellText.contains("이미지"));
                        if (!contains && !keyword) continue;

                        if (anchorPicture(sheet, helper, pictureIndex, row.getRowNum(), ci, fieldName, imageBytes.length, false)) {
                            usedCellKeys.add(key);
                            inserted = true;
                            break;
                        }
                    }
                }
            }

            if (!inserted) {
                log.warn("XLSX 이미지 플레이스홀더를 찾지 못함: {}", fieldName);
            }
        }
    }

    private boolean anchorPicture(Sheet sheet, CreationHelper helper, int pictureIndex,
                                   int labelRow, int labelCol, String fieldName, int byteLen,
                                   boolean anchorToRight) {
        try {
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            int col1 = anchorToRight ? labelCol + 1 : labelCol;
            anchor.setCol1(col1);
            anchor.setRow1(labelRow);
            anchor.setCol2(col1 + 2);
            anchor.setRow2(labelRow + 4);
            drawing.createPicture(anchor, pictureIndex);
            log.info("XLSX 이미지 삽입 완료: {} ({} bytes)", fieldName, byteLen);
            return true;
        } catch (Exception e) {
            log.warn("XLSX 이미지 삽입 실패: {} - {}", fieldName, e.getMessage());
            return false;
        }
    }

    /**
     * 매직 바이트로 이미지 타입을 감지합니다.
     * - JPEG: 0xFF 0xD8
     * - PNG : 0x89 0x50 0x4E 0x47
     * 기본값은 JPEG입니다. (DOCX/XLSX 공통 상수 - PICTURE_TYPE_JPEG=5, PICTURE_TYPE_PNG=6)
     */
    private int detectPictureType(byte[] imageBytes) {
        if (imageBytes != null && imageBytes.length >= 4) {
            int b0 = imageBytes[0] & 0xFF;
            int b1 = imageBytes[1] & 0xFF;
            int b2 = imageBytes[2] & 0xFF;
            int b3 = imageBytes[3] & 0xFF;
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
                return Workbook.PICTURE_TYPE_PNG;
            }
            if (b0 == 0xFF && b1 == 0xD8) {
                return Workbook.PICTURE_TYPE_JPEG;
            }
        }
        return Workbook.PICTURE_TYPE_JPEG;
    }
}

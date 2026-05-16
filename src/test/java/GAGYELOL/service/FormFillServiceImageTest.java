package GAGYELOL.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 플레이스홀더 매칭 우선순위 검증.
 * 현금지출증빙서.docx 같이 한 행에 "학생증 부착(...)"과 "영수증 부착(...)" 두 슬롯이 있는 경우,
 * 필드 "영수증"은 "영수증 부착(...)" 셀에 가야 하고 "학생증"은 "학생증 부착(...)" 셀에 가야 함.
 * 기존 Pass 2(키워드 일치 우선)는 첫 부착 셀로 무차별 떨어뜨려서 영수증 이미지가 학생증 칸에 잘못 들어가던 문제.
 */
class FormFillServiceImageTest {

    private final FormFillService service = new FormFillService();

    @TempDir Path tempDir;

    @Test
    void DOCX_영수증_필드는_영수증_부착_셀로_학생증은_학생증_부착_셀로_간다() throws IOException {
        Path tempFile = createDocxWithTwoAttachmentCells();

        Map<String, byte[]> images = new LinkedHashMap<>();
        images.put("영수증", pngBytes("R"));
        images.put("학생증", pngBytes("S"));

        byte[] result = service.fill(tempFile.toString(), Map.of(), images);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            XWPFTable table = doc.getTables().get(0);
            // 행 0: [증빙 서류, 학생증 부착(...), 영수증 부착(...)]
            XWPFTableCell studentCell = table.getRow(0).getCell(1);
            XWPFTableCell receiptCell = table.getRow(0).getCell(2);

            assertThat(getEmbeddedPictureCount(studentCell)).as("학생증 셀에 1개").isEqualTo(1);
            assertThat(getEmbeddedPictureCount(receiptCell)).as("영수증 셀에 1개").isEqualTo(1);
        }
    }

    @Test
    void DOCX_제너릭_사진_필드는_부착_키워드_있는_첫_셀로_간다() throws IOException {
        Path tempFile = createDocxWithTwoAttachmentCells();

        Map<String, byte[]> images = new LinkedHashMap<>();
        images.put("사진", pngBytes("P"));

        byte[] result = service.fill(tempFile.toString(), Map.of(), images);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            XWPFTable table = doc.getTables().get(0);
            XWPFTableCell studentCell = table.getRow(0).getCell(1);
            XWPFTableCell receiptCell = table.getRow(0).getCell(2);

            // Pass 2a(구체적 일치)는 없음 → Pass 2b(키워드)로 떨어져 첫 부착 셀에 들어감
            int studentPics = getEmbeddedPictureCount(studentCell);
            int receiptPics = getEmbeddedPictureCount(receiptCell);
            assertThat(studentPics + receiptPics).as("총 1개").isEqualTo(1);
        }
    }

    @Test
    void XLSX_영수증_필드는_영수증_부착_셀_위치에_앵커() throws IOException {
        Path tempFile = createXlsxWithTwoAttachmentLabels();

        Map<String, byte[]> images = new LinkedHashMap<>();
        images.put("영수증", pngBytes("R"));

        byte[] result = service.fill(tempFile.toString(), Map.of(), images);

        try (XSSFWorkbook out = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            Sheet sheet = out.getSheetAt(0);
            org.apache.poi.xssf.usermodel.XSSFDrawing drawing =
                    (org.apache.poi.xssf.usermodel.XSSFDrawing) sheet.getDrawingPatriarch();
            assertThat(drawing).isNotNull();
            List<org.apache.poi.xssf.usermodel.XSSFShape> shapes = drawing.getShapes();
            assertThat(shapes).hasSize(1);
            // 영수증 셀은 col 2 → anchorPicture는 col1=ci로 앵커 (Pass 2a 진입)
            org.apache.poi.xssf.usermodel.XSSFPicture picture =
                    (org.apache.poi.xssf.usermodel.XSSFPicture) shapes.get(0);
            assertThat((int) picture.getClientAnchor().getCol1())
                    .as("영수증 셀(col 2)에 앵커").isEqualTo(2);
        }
    }

    // --- helpers ---

    private Path createDocxWithTwoAttachmentCells() throws IOException {
        Path tempFile = tempDir.resolve("attachments.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(1, 3);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("증빙 서류");
            row.getCell(1).setText("학생증 부착(금액을 받은 학생의 학생증)");
            row.getCell(2).setText("영수증 부착 (금액을 수령 받을 학생이 지출한 금액에 대한 영수증)");
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                doc.write(fos);
            }
        }
        return tempFile;
    }

    private Path createXlsxWithTwoAttachmentLabels() throws IOException {
        Path tempFile = tempDir.resolve("attachments.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("증빙 서류");
            row.createCell(1).setCellValue("학생증 부착(학생의 학생증)");
            row.createCell(2).setCellValue("영수증 부착(영수증 첨부)");
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                wb.write(fos);
            }
        }
        return tempFile;
    }

    /** 최소 유효 PNG (1x1 흰색)를 만들어 ImageIO/POI가 받아들이게 함. */
    private byte[] pngBytes(String tag) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFFFFFF);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private int getEmbeddedPictureCount(XWPFTableCell cell) {
        int count = 0;
        for (XWPFParagraph para : cell.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                count += run.getEmbeddedPictures().size();
            }
        }
        return count;
    }
}

package GAGYELOL.controller;

import GAGYELOL.config.JwtUtil;
import GAGYELOL.dto.CompleteFormRequest;
import GAGYELOL.dto.EvidenceAnalysisResponse;
import GAGYELOL.dto.FillFieldsRequest;
import GAGYELOL.dto.FillFieldsResponse;
import GAGYELOL.service.EvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final JwtUtil jwtUtil;

    @PostMapping("/analyze")
    public ResponseEntity<EvidenceAnalysisResponse> analyze(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "groupId", required = false) Long groupId
    ) {
        Long userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(evidenceService.analyze(file, userId, groupId));
    }

    @PostMapping("/{evidenceId}/fill")
    public ResponseEntity<FillFieldsResponse> fillFields(
            @PathVariable Long evidenceId,
            @RequestBody FillFieldsRequest request
    ) {
        return ResponseEntity.ok(evidenceService.fillFields(evidenceId, request));
    }

    @PostMapping("/{evidenceId}/complete")
    public ResponseEntity<byte[]> completeForm(
            @PathVariable Long evidenceId,
            @RequestBody CompleteFormRequest request
    ) {
        byte[] fileBytes = evidenceService.completeForm(evidenceId, request);
        boolean isZip = request.getForms().size() > 1;

        HttpHeaders headers = new HttpHeaders();
        String fileName = isZip ? "completed_forms.zip" : "completed_form.docx";
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build());
        headers.setContentType(isZip
                ? MediaType.parseMediaType("application/zip")
                : MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok().headers(headers).body(fileBytes);
    }
}

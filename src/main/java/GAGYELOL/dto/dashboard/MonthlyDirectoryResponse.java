package GAGYELOL.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MonthlyDirectoryResponse {
    private int year;
    private int month;
    private long count;
    private List<FileEntry> files;

    @Getter
    @Builder
    public static class FileEntry {
        private Long requestId;
        private String fileName;
        private String date;
        private String status;
    }
}

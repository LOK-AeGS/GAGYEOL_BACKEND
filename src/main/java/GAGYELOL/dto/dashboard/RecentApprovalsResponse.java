package GAGYELOL.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecentApprovalsResponse {
    private long reviewingCount;
    private long approvedCount;
    private long rejectedCount;
    private List<ApprovalItem> items;

    @Getter
    @Builder
    public static class ApprovalItem {
        private Long requestId;
        private String title;
        private String expCode;
        private String date;
        private String requesterName;
        private String status;
    }
}

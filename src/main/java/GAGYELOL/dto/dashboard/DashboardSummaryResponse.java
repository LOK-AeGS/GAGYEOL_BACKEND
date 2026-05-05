package GAGYELOL.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardSummaryResponse {
    private long monthlySubmitCount;
    private long prevMonthSubmitCount;
    private long monthlyTotalAmount;
    private long prevMonthTotalAmount;
    private long inProgressCount;
    private double avgWaitingDays;
    private int complianceRate;
}

package GAGYELOL.service;

import GAGYELOL.dto.approval.ApprovalResponse;
import GAGYELOL.dto.approval.ApproveRequest;
import GAGYELOL.dto.approval.CreateApprovalRequest;
import GAGYELOL.dto.approval.EditFieldsRequest;
import GAGYELOL.entity.*;
import GAGYELOL.repository.*;
import GAGYELOL.entity.Form;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ApprovalService {

    private final UserRepository userRepository;
    private final UserGroupRepository groupRepository;
    private final GroupRoleRepository roleRepository;
    private final GroupMemberRepository memberRepository;
    private final ApprovalRequestRepository requestRepository;
    private final ApprovalStepRepository stepRepository;
    private final ApprovalEditHistoryRepository editHistoryRepository;
    private final EvidenceRepository evidenceRepository;
    private final FormRepository formRepository;
    private final ObjectMapper objectMapper;

    public ApprovalResponse createRequest(Long requesterId, CreateApprovalRequest req) {
        User requester = findUser(requesterId);
        UserGroup group = findGroup(req.getGroupId());

        GroupMember requesterMember = memberRepository.findByGroupAndUser(group, requester)
                .orElseThrow(() -> new IllegalArgumentException("해당 그룹의 멤버가 아닙니다."));
        int requesterOrder = requesterMember.getRole().getApprovalOrder();

        // 요청자보다 높은 결재 단계 역할 목록 (오름차순 정렬 보장)
        List<GroupRole> higherRoles = roleRepository
                .findByGroupAndApprovalOrderGreaterThanOrderByApprovalOrderAsc(group, requesterOrder);

        // DB 저장 전 사전 검증: 모든 결재 단계에 멤버가 있는지 확인
        validateApprovalChain(group, higherRoles);

        // evidenceId / formId 필수 검증
        if (req.getEvidenceId() == null) {
            throw new IllegalArgumentException("증빙서류(evidenceId)는 필수입니다.");
        }
        if (req.getFormId() == null) {
            throw new IllegalArgumentException("양식지(formId)는 필수입니다.");
        }

        Evidence evidence = evidenceRepository.findById(req.getEvidenceId())
                .orElseThrow(() -> new IllegalArgumentException("증빙서류를 찾을 수 없습니다: " + req.getEvidenceId()));

        Form form = formRepository.findById(req.getFormId())
                .orElseThrow(() -> new IllegalArgumentException("양식지를 찾을 수 없습니다: " + req.getFormId()));

        // evidence와 form이 같은 그룹 소속인지 검증
        if (evidence.getGroup() != null && !evidence.getGroup().getId().equals(group.getId())) {
            throw new IllegalArgumentException("해당 증빙서류는 이 그룹의 것이 아닙니다.");
        }
        if (form.getGroup() != null && !form.getGroup().getId().equals(group.getId())) {
            throw new IllegalArgumentException("해당 양식지는 이 그룹의 것이 아닙니다.");
        }

        // 재결재: parentRequestId가 있으면 이전 filled_fields 복사 (요청 값이 있으면 덮어씀)
        ApprovalRequest parentRequest = null;
        if (req.getParentRequestId() != null) {
            parentRequest = requestRepository.findById(req.getParentRequestId())
                    .orElseThrow(() -> new IllegalArgumentException("원본 결재요청을 찾을 수 없습니다: " + req.getParentRequestId()));
            if (!"REJECTED".equals(parentRequest.getStatus())) {
                throw new IllegalArgumentException("반려된 결재요청만 재결재할 수 있습니다.");
            }
        }

        // filledFields: 요청 값 우선, 없으면 부모 요청 값 복사
        String filledFieldsJson = (req.getFilledFields() != null && !req.getFilledFields().isEmpty())
                ? toJson(req.getFilledFields())
                : (parentRequest != null ? parentRequest.getFilledFields() : "{}");

        boolean hasNextStep = !higherRoles.isEmpty();
        int nextOrder = hasNextStep ? higherRoles.get(0).getApprovalOrder() : requesterOrder;

        String status = hasNextStep ? "IN_PROGRESS" : "APPROVED";
        ApprovalRequest approvalRequest = requestRepository.save(ApprovalRequest.builder()
                .group(group)
                .requester(requester)
                .evidence(evidence)
                .form(form)
                .parentRequest(parentRequest)
                .filledFields(filledFieldsJson)
                .currentApprovalOrder(nextOrder)
                .status(status)
                .build());

        if (hasNextStep) {
            createPendingSteps(approvalRequest, group, nextOrder);
        }

        log.info("결재요청 생성 - requestId={}, status={}, nextOrder={}", approvalRequest.getId(), status, nextOrder);
        return toResponse(approvalRequest);
    }

    private void validateApprovalChain(UserGroup group, List<GroupRole> roles) {
        for (GroupRole role : roles) {
            List<GroupMember> members = memberRepository.findByGroupAndRole(group, role);
            if (members.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("결재 단계 '%s'(순서: %d)에 배정된 멤버가 없습니다. 결재 라인을 확인해주세요.",
                                role.getRoleName(), role.getApprovalOrder()));
            }
        }
    }

    public ApprovalResponse approve(Long approverId, Long requestId, ApproveRequest req) {
        User approver = findUser(approverId);
        // Bug 3 fix: 비관적 락으로 동시 승인 시 중복 스텝 생성 방지
        ApprovalRequest approvalRequest = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new IllegalArgumentException("결재요청을 찾을 수 없습니다."));

        validateApprover(approver, approvalRequest);

        // Bug 2 fix: approval_order까지 함께 필터링하여 겸임 결재자 오작동 방지
        ApprovalStep step = stepRepository.findByRequestAndApproverAndApprovalOrder(
                        approvalRequest, approver, approvalRequest.getCurrentApprovalOrder())
                .orElseThrow(() -> new IllegalArgumentException("결재 권한이 없습니다."));

        if (!"PENDING".equals(step.getAction())) {
            throw new IllegalArgumentException("이미 처리된 결재입니다.");
        }

        step.approve(req.getComment());
        stepRepository.save(step);

        // 현재 단계 전원 승인 여부 확인
        List<ApprovalStep> currentSteps = stepRepository.findByRequestAndApprovalOrder(
                approvalRequest, approvalRequest.getCurrentApprovalOrder());

        // Bug 1 fix: CANCELED 스텝 제외 후 나머지가 모두 APPROVED인지 체크
        boolean allApproved = currentSteps.stream()
                .filter(s -> !"CANCELED".equals(s.getAction()))
                .allMatch(s -> "APPROVED".equals(s.getAction()));

        if (allApproved) {
            // 다음 결재 단계 탐색
            List<GroupRole> nextRoles = roleRepository
                    .findByGroupAndApprovalOrderGreaterThanOrderByApprovalOrderAsc(
                            approvalRequest.getGroup(), approvalRequest.getCurrentApprovalOrder());

            if (nextRoles.isEmpty()) {
                // 최종 승인
                approvalRequest.updateStatus("APPROVED");
                log.info("결재 최종 승인 - requestId={}", requestId);
            } else {
                // 다음 단계로 진행
                int nextOrder = nextRoles.get(0).getApprovalOrder();
                approvalRequest.updateApprovalOrder(nextOrder);
                createPendingSteps(approvalRequest, approvalRequest.getGroup(), nextOrder);
                log.info("결재 다음 단계 진행 - requestId={}, nextOrder={}", requestId, nextOrder);
            }
        }

        requestRepository.save(approvalRequest);
        return toResponse(approvalRequest);
    }

    public ApprovalResponse reject(Long approverId, Long requestId, ApproveRequest req) {
        User approver = findUser(approverId);
        ApprovalRequest approvalRequest = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new IllegalArgumentException("결재요청을 찾을 수 없습니다."));

        validateApprover(approver, approvalRequest);

        ApprovalStep step = stepRepository.findByRequestAndApproverAndApprovalOrder(
                        approvalRequest, approver, approvalRequest.getCurrentApprovalOrder())
                .orElseThrow(() -> new IllegalArgumentException("결재 권한이 없습니다."));

        if (!"PENDING".equals(step.getAction())) {
            throw new IllegalArgumentException("이미 처리된 결재입니다.");
        }

        step.reject(req.getComment());
        stepRepository.save(step);

        // 반려 시 해당 단계 나머지 스텝도 REJECTED 처리
        List<ApprovalStep> currentSteps = stepRepository.findByRequestAndApprovalOrder(
                approvalRequest, approvalRequest.getCurrentApprovalOrder());
        currentSteps.stream()
                .filter(s -> "PENDING".equals(s.getAction()))
                .forEach(s -> { s.reject("상위 결재자 반려로 자동 처리"); stepRepository.save(s); });

        approvalRequest.updateStatus("REJECTED");
        requestRepository.save(approvalRequest);

        log.info("결재 반려 - requestId={}, approverId={}", requestId, approverId);
        return toResponse(approvalRequest);
    }

    public ApprovalResponse editFields(Long editorId, Long requestId, EditFieldsRequest req) {
        User editor = findUser(editorId);
        ApprovalRequest approvalRequest = findRequest(requestId);

        // 작성자 또는 현재 결재 단계 승인자만 수정 가능
        validateEditor(editor, approvalRequest);

        String beforeFields = approvalRequest.getFilledFields();
        String afterFields = toJson(req.getFilledFields());

        // 수정 이력 저장
        editHistoryRepository.save(ApprovalEditHistory.builder()
                .request(approvalRequest)
                .editor(editor)
                .beforeFields(beforeFields)
                .afterFields(afterFields)
                .build());

        approvalRequest.updateFilledFields(afterFields);
        requestRepository.save(approvalRequest);

        log.info("결재 양식지 수정 - requestId={}, editorId={}", requestId, editorId);
        return toResponse(approvalRequest);
    }

    @Transactional(readOnly = true)
    public ApprovalResponse getRequest(Long requestId) {
        return toResponse(findRequest(requestId));
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> getGroupRequests(Long groupId) {
        UserGroup group = findGroup(groupId);
        return requestRepository.findByGroupOrderByCreatedAtDesc(group).stream()
                .map(this::toResponse)
                .toList();
    }

    // ────────────── 내부 헬퍼 ──────────────

    private void createPendingSteps(ApprovalRequest request, UserGroup group, int approvalOrder) {
        GroupRole role = roleRepository.findByGroupAndApprovalOrder(group, approvalOrder)
                .orElseThrow(() -> new IllegalArgumentException("결재 단계 역할을 찾을 수 없습니다."));

        List<GroupMember> approvers = memberRepository.findByGroupAndRole(group, role);
        if (approvers.isEmpty()) {
            throw new IllegalArgumentException("해당 결재 단계에 승인자가 없습니다.");
        }

        for (GroupMember member : approvers) {
            stepRepository.save(ApprovalStep.builder()
                    .request(request)
                    .approver(member.getUser())
                    .approvalOrder(approvalOrder)
                    .action("PENDING")
                    .build());
        }
    }

    private void validateApprover(User approver, ApprovalRequest request) {
        if (!"IN_PROGRESS".equals(request.getStatus())) {
            throw new IllegalArgumentException("진행 중인 결재가 아닙니다. (현재 상태: " + request.getStatus() + ")");
        }
        boolean isApprover = stepRepository
                .findByRequestAndApprovalOrder(request, request.getCurrentApprovalOrder())
                .stream().anyMatch(s -> s.getApprover().getId().equals(approver.getId()));
        if (!isApprover) {
            throw new IllegalArgumentException("현재 결재 단계의 승인자가 아닙니다.");
        }
    }

    private void validateEditor(User editor, ApprovalRequest request) {
        if ("APPROVED".equals(request.getStatus())) {
            throw new IllegalArgumentException("이미 최종 승인된 결재는 수정할 수 없습니다.");
        }
        boolean isRequester = request.getRequester().getId().equals(editor.getId());
        boolean isCurrentApprover = stepRepository
                .findByRequestAndApprovalOrder(request, request.getCurrentApprovalOrder())
                .stream().anyMatch(s -> s.getApprover().getId().equals(editor.getId()));
        if (!isRequester && !isCurrentApprover) {
            throw new IllegalArgumentException("수정 권한이 없습니다.");
        }
    }

    private ApprovalResponse toResponse(ApprovalRequest request) {
        List<ApprovalStep> steps = stepRepository.findByRequest(request);
        Map<String, String> fields = fromJson(request.getFilledFields());

        return ApprovalResponse.builder()
                .requestId(request.getId())
                .status(request.getStatus())
                .currentApprovalOrder(request.getCurrentApprovalOrder())
                .filledFields(fields)
                .steps(steps.stream()
                        .map(s -> {
                            GroupMember member = memberRepository
                                    .findByGroupAndUser(request.getGroup(), s.getApprover())
                                    .orElse(null);
                            return ApprovalResponse.StepSummary.builder()
                                    .approverName(s.getApprover().getName())
                                    .approvalOrder(s.getApprovalOrder())
                                    .roleName(member != null ? member.getRole().getRoleName() : "-")
                                    .action(s.getAction())
                                    .comment(s.getComment())
                                    .actedAt(s.getActedAt())
                                    .build();
                        })
                        .toList())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    private String toJson(Map<String, String> map) {
        if (map == null) return "{}";
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private UserGroup findGroup(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("그룹을 찾을 수 없습니다."));
    }

    private ApprovalRequest findRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("결재요청을 찾을 수 없습니다."));
    }
}

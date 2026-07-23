package kr.co.dh.globelog.admin;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventCategory;
import kr.co.dh.globelog.domain.SecurityEventLog;
import kr.co.dh.globelog.domain.SecurityEventLogRepository;
import kr.co.dh.globelog.domain.SecurityEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 관리자 "보안 로그" 화면 — 로그인/로그아웃(관리자·일반 사용자 구분), 게시글 CRUD/조회,
 * 채팅 이벤트를 한 곳에서 조회한다. SecurityConfig에서 /admin/security-logs/**를
 * SUPER_ADMIN 전용으로 막아둔다(모더레이터는 접근 불가).
 */
@Controller
@RequestMapping("/admin/security-logs")
public class AdminSecurityLogController {

    // 엑셀 다운로드 시 한 번에 뽑는 최대 행 수 — 무한정 쿼리/파일이 커지는 걸 막는
    // 안전장치(사이드 프로젝트 규모에서는 사실상 도달할 일이 없는 여유 있는 상한).
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final DateTimeFormatter EXPORT_FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SecurityEventLogRepository securityEventLogRepository;

    public AdminSecurityLogController(SecurityEventLogRepository securityEventLogRepository) {
        this.securityEventLogRepository = securityEventLogRepository;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) SecurityEventCategory category,
            @RequestParam(required = false) SecurityActorType actorType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 30, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        Specification<SecurityEventLog> spec = buildSpecification(category, actorType, from, to, trimmedKeyword);
        Page<SecurityEventLog> logs = securityEventLogRepository.findAll(spec, pageable);

        model.addAttribute("logs", logs);
        model.addAttribute("category", category);
        model.addAttribute("actorType", actorType);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("keyword", trimmedKeyword);
        model.addAttribute("categories", SecurityEventCategory.values());
        model.addAttribute("actorTypes", SecurityActorType.values());
        model.addAttribute("activeMenu", "securityLogs");
        return "admin/security-logs/list";
    }

    // 목록 화면과 같은 필터(category/actorType/from/to/keyword)를 그대로 받아 "지금 보고
    // 있는 조건 그대로" 다운로드되게 한다. 페이지네이션 없이 전체(최대 EXPORT_MAX_ROWS건)를
    // 내려받는다는 점만 list()와 다르다.
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) SecurityEventCategory category,
            @RequestParam(required = false) SecurityActorType actorType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String keyword) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        Specification<SecurityEventLog> spec = buildSpecification(category, actorType, from, to, trimmedKeyword);
        Pageable exportLimit = PageRequest.of(0, EXPORT_MAX_ROWS, Sort.by(Sort.Direction.DESC, "occurredAt"));
        List<SecurityEventLog> logs = securityEventLogRepository.findAll(spec, exportLimit).getContent();

        byte[] excelBytes = SecurityLogExcelWriter.write(logs);
        String filename = "security-logs-" + LocalDateTime.now().format(EXPORT_FILENAME_FORMAT) + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(excelBytes);
    }

    private Specification<SecurityEventLog> buildSpecification(
            SecurityEventCategory category, SecurityActorType actorType, LocalDate from, LocalDate to,
            String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null) {
                List<SecurityEventType> typesInCategory = Arrays.stream(SecurityEventType.values())
                        .filter(type -> type.getCategory() == category)
                        .toList();
                predicates.add(root.get("eventType").in(typesInCategory));
            }
            if (actorType != null) {
                predicates.add(cb.equal(root.get("actorType"), actorType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay()));
            }
            if (to != null) {
                LocalDateTime exclusiveEnd = to.plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("occurredAt"), exclusiveEnd));
            }
            if (!keyword.isEmpty()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actorLabel")), pattern),
                        cb.like(cb.lower(root.get("detail")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

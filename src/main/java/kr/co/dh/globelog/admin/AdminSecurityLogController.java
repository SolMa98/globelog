package kr.co.dh.globelog.admin;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventCategory;
import kr.co.dh.globelog.domain.SecurityEventLog;
import kr.co.dh.globelog.domain.SecurityEventLogRepository;
import kr.co.dh.globelog.domain.SecurityEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 "보안 로그" 화면 — 로그인/로그아웃(관리자·일반 사용자 구분), 게시글 CRUD/조회,
 * 채팅 이벤트를 한 곳에서 조회한다. SecurityConfig에서 /admin/security-logs/**를
 * SUPER_ADMIN 전용으로 막아둔다(모더레이터는 접근 불가).
 */
@Controller
@RequestMapping("/admin/security-logs")
public class AdminSecurityLogController {

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

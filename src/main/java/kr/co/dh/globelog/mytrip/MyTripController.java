package kr.co.dh.globelog.mytrip;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.co.dh.globelog.admin.AdminTripImageResponse;
import kr.co.dh.globelog.api.RegionResponse;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.Region;
import kr.co.dh.globelog.domain.RegionRepository;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImage;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.TripVisibility;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.file.FileStorageService;
import kr.co.dh.globelog.security.CurrentUserResolver;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 일반 가입 사용자(User)용 셀프서비스 여행 CRUD. /admin/trips(AdminTripController, 전체
 * 사용자 데이터를 다루는 백오피스)와는 완전히 분리 — 이 컨트롤러는 항상 "본인 소유
 * 여행"으로만 범위를 제한한다(project_multiuser_design_discussion 메모리 참고).
 */
@Controller
@RequestMapping("/my/trips")
public class MyTripController {

    private final TripRepository tripRepository;
    private final TripImageRepository tripImageRepository;
    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;
    private final FileStorageService fileStorageService;
    private final CurrentUserResolver currentUserResolver;
    private final SecurityAuditService securityAuditService;

    public MyTripController(TripRepository tripRepository, TripImageRepository tripImageRepository,
            RegionRepository regionRepository, CountryRepository countryRepository,
            FileStorageService fileStorageService, CurrentUserResolver currentUserResolver,
            SecurityAuditService securityAuditService) {
        this.tripRepository = tripRepository;
        this.tripImageRepository = tripImageRepository;
        this.regionRepository = regionRepository;
        this.countryRepository = countryRepository;
        this.fileStorageService = fileStorageService;
        this.currentUserResolver = currentUserResolver;
        this.securityAuditService = securityAuditService;
    }

    @GetMapping
    public String list(Model model, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        List<Trip> trips = tripRepository.findByUserIdOrderByVisitedDateDesc(viewer.getId());
        List<Country> countries = countryRepository.findByEnabledTrueOrderByNameKoAsc();
        model.addAttribute("trips", trips);
        model.addAttribute("countries", countries);
        return "mytrip/list";
    }

    // 여행 등록 모달에서 국가 선택 시 지역 select를 채우는 데 씀 — RegionApiController의
    // /api/countries/{iso}/regions는 "owner가 이미 방문 기록을 남긴 지역"만 보여주는
    // 완전히 다른 용도라 여기 재사용할 수 없다(신규 등록 시점엔 아직 방문 기록이 없음).
    @GetMapping("/regions")
    @ResponseBody
    public List<RegionResponse> regions(@RequestParam Long countryId, Authentication authentication) {
        requireLoggedIn(authentication);
        return regionRepository.findByCountryIdAndEnabledTrueOrderByNameKoAsc(countryId).stream()
                .map(RegionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @ResponseBody
    public MyTripDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        Trip trip = findOwnedTripOrThrow(id, authentication);
        List<AdminTripImageResponse> images = tripImageRepository.findByTripIdOrderBySortOrderAsc(id)
                .stream()
                .map(AdminTripImageResponse::from)
                .toList();
        return MyTripDetailResponse.from(trip, images);
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam Long countryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam String title,
            @RequestParam String visitedDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "PUBLIC") TripVisibility visibility,
            Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        Country country = resolveCountry(countryId);
        Region region = resolveRegion(regionId);
        LocalDate parsedEndDate = (endDate == null || endDate.isBlank()) ? null : LocalDate.parse(endDate);
        Trip trip = new Trip(viewer, region, country, title,
                LocalDate.parse(visitedDate), parsedEndDate, emptyToNull(description));
        trip.setVisibility(visibility);
        tripRepository.save(trip);
        securityAuditService.record(SecurityEventType.TRIP_CREATE, SecurityActorType.USER,
                viewer.getId(), viewer.getNickname(), "TRIP", trip.getId(), title);
        return ResponseEntity.ok(Map.of("success", true, "id", trip.getId()));
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestParam Long countryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam String title,
            @RequestParam String visitedDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "PUBLIC") TripVisibility visibility,
            Authentication authentication) {
        Trip trip = findOwnedTripOrThrow(id, authentication);
        Country country = resolveCountry(countryId);
        Region region = resolveRegion(regionId);
        // 지역은 반드시 그 국가에 속해야 함 — 국가만 바꾸고 지역 select를 못 갈아탄
        // 클라이언트가 이전 국가의 지역 id를 그대로 보내는 실수를 서버에서 막는다.
        if (region != null && !region.getCountry().getId().equals(country.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택한 지역이 해당 국가에 속하지 않습니다.");
        }
        trip.setCountry(country);
        trip.setRegion(region);
        trip.setTitle(title);
        trip.setVisitedDate(LocalDate.parse(visitedDate));
        trip.setEndDate((endDate == null || endDate.isBlank()) ? null : LocalDate.parse(endDate));
        trip.setDescription(emptyToNull(description));
        trip.setVisibility(visibility);
        tripRepository.save(trip);
        securityAuditService.record(SecurityEventType.TRIP_UPDATE, SecurityActorType.USER,
                trip.getUser().getId(), trip.getUser().getNickname(), "TRIP", trip.getId(), title);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, Authentication authentication) {
        Trip trip = findOwnedTripOrThrow(id, authentication);
        List<TripImage> images = tripImageRepository.findByTripOrderBySortOrderAsc(trip);
        images.forEach(img -> fileStorageService.delete(img.getFilePath()));
        tripImageRepository.deleteAll(images);
        securityAuditService.record(SecurityEventType.TRIP_DELETE, SecurityActorType.USER,
                trip.getUser().getId(), trip.getUser().getNickname(), "TRIP", trip.getId(), trip.getTitle());
        tripRepository.delete(trip);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/images")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Trip trip = findOwnedTripOrThrow(id, authentication);
        try {
            String storedFilename = fileStorageService.store(file);
            int nextOrder = tripImageRepository.findByTripIdOrderBySortOrderAsc(id).size();
            TripImage image = tripImageRepository.save(
                    new TripImage(trip, storedFilename, file.getOriginalFilename(), nextOrder));
            return ResponseEntity.ok(Map.of("success", true, "image", AdminTripImageResponse.from(image)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/images/{imageId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteImage(
            @PathVariable Long id, @PathVariable Long imageId, Authentication authentication) {
        Trip trip = findOwnedTripOrThrow(id, authentication);
        TripImage image = tripImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."));
        if (!image.getTrip().getId().equals(trip.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 소유 여행의 사진만 삭제할 수 있습니다.");
        }
        fileStorageService.delete(image.getFilePath());
        tripImageRepository.delete(image);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }

    // 소유권 체크: 클라이언트가 보낸 id로 아무 trip이나 조회 못 하게, 항상 로그인한
    // 사용자 본인 소유인지 확인한 뒤에만 반환한다.
    private Trip findOwnedTripOrThrow(Long id, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다: " + id));
        if (trip.getUser() == null || !trip.getUser().getId().equals(viewer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 소유 여행만 관리할 수 있습니다.");
        }
        return trip;
    }

    private Country resolveCountry(Long countryId) {
        Country country = countryRepository.findById(countryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "국가를 찾을 수 없습니다."));
        if (!country.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 여행을 등록할 수 없는 국가입니다.");
        }
        return country;
    }

    private Region resolveRegion(Long regionId) {
        if (regionId == null) {
            return null;
        }
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지역을 찾을 수 없습니다."));
        if (!region.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 여행을 등록할 수 없는 지역입니다.");
        }
        return region;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

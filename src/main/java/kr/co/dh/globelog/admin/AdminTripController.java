package kr.co.dh.globelog.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.Region;
import kr.co.dh.globelog.domain.RegionRepository;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImage;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.file.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@Controller
@RequestMapping("/admin/trips")
public class AdminTripController {

    private final TripRepository tripRepository;
    private final TripImageRepository tripImageRepository;
    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;
    private final FileStorageService fileStorageService;

    public AdminTripController(TripRepository tripRepository, TripImageRepository tripImageRepository,
            RegionRepository regionRepository, CountryRepository countryRepository,
            FileStorageService fileStorageService) {
        this.tripRepository = tripRepository;
        this.tripImageRepository = tripImageRepository;
        this.regionRepository = regionRepository;
        this.countryRepository = countryRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long countryId,
            @PageableDefault(size = 30, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        Page<Trip> trips;
        if (regionId != null) {
            trips = tripRepository.findByRegionId(regionId, pageable);
        } else if (countryId != null) {
            trips = tripRepository.findByCountryId(countryId, pageable);
        } else {
            trips = tripRepository.findAll(pageable);
        }
        List<Country> countries = countryRepository.findAll(Sort.by("nameKo"));
        List<Region> regions = countryId != null
                ? regionRepository.findByCountryIdOrderByNameKoAsc(countryId)
                : regionRepository.findAll(Sort.by("nameKo"));
        model.addAttribute("trips", trips);
        model.addAttribute("countries", countries);
        model.addAttribute("regions", regions);
        model.addAttribute("selectedRegionId", regionId);
        model.addAttribute("selectedCountryId", countryId);
        model.addAttribute("activeMenu", "trips");
        return "admin/trips/list";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public AdminTripDetailResponse detail(@PathVariable Long id) {
        Trip trip = findTripOrThrow(id);
        List<AdminTripImageResponse> images = tripImageRepository.findByTripIdOrderBySortOrderAsc(id)
                .stream()
                .map(AdminTripImageResponse::from)
                .toList();
        return AdminTripDetailResponse.from(trip, images);
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam Long countryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam String title,
            @RequestParam String visitedDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String description) {
        Country country = countryRepository.findById(countryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "국가를 찾을 수 없습니다."));
        Region region = null;
        if (regionId != null) {
            region = regionRepository.findById(regionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지역을 찾을 수 없습니다."));
        }
        LocalDate parsedEndDate = (endDate == null || endDate.isBlank()) ? null : LocalDate.parse(endDate);
        // 관리자 백오피스로 등록하는 여행은 아직 소유자(User) 개념이 없어 null로 둠
        Trip trip = tripRepository.save(new Trip(null, region, country, title,
                LocalDate.parse(visitedDate), parsedEndDate, emptyToNull(description)));
        return ResponseEntity.ok(Map.of("success", true, "id", trip.getId()));
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String visitedDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "0") int priority) {
        Trip trip = findTripOrThrow(id);
        trip.setTitle(title);
        trip.setVisitedDate(LocalDate.parse(visitedDate));
        trip.setEndDate((endDate == null || endDate.isBlank()) ? null : LocalDate.parse(endDate));
        trip.setDescription(emptyToNull(description));
        // 피드 노출 우선순위는 관리자 전용 설정(project_multiuser_design_discussion
        // 메모리 참고) — 0~5로 제한해 남용으로 피드가 "진짜 랜덤"이 아니게 되는 걸 막음.
        trip.setPriority(Math.max(0, Math.min(5, priority)));
        tripRepository.save(trip);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Trip trip = findTripOrThrow(id);
        List<TripImage> images = tripImageRepository.findByTripOrderBySortOrderAsc(trip);
        images.forEach(img -> fileStorageService.delete(img.getFilePath()));
        tripImageRepository.deleteAll(images);
        tripRepository.delete(trip);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/images")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        Trip trip = findTripOrThrow(id);
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
    public ResponseEntity<Map<String, Object>> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        TripImage image = tripImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."));
        fileStorageService.delete(image.getFilePath());
        tripImageRepository.delete(image);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Trip findTripOrThrow(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다: " + id));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
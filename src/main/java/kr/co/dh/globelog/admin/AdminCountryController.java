package kr.co.dh.globelog.admin;

import java.util.Locale;
import java.util.Map;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
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
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/countries")
public class AdminCountryController {

    private final CountryRepository countryRepository;
    private final CountryRefService countryRefService;

    public AdminCountryController(CountryRepository countryRepository, CountryRefService countryRefService) {
        this.countryRepository = countryRepository;
        this.countryRefService = countryRefService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 30, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        Page<Country> countries = trimmedKeyword.isEmpty()
                ? countryRepository.findAll(pageable)
                : countryRepository.findByNameKoContainingIgnoreCaseOrNameEnContainingIgnoreCase(
                        trimmedKeyword, trimmedKeyword, pageable);
        model.addAttribute("countries", countries);
        model.addAttribute("keyword", trimmedKeyword);
        model.addAttribute("activeMenu", "countries");
        return "admin/countries/list";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public AdminCountryDetailResponse detail(@PathVariable Long id) {
        return AdminCountryDetailResponse.from(findCountryOrThrow(id));
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String isoA3,
            @RequestParam(required = false) String isoA2,
            @RequestParam String nameKo,
            @RequestParam String nameEn,
            @RequestParam(required = false) String description) {
        if (isoA3 == null || isoA3.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "국가 코드를 입력해주세요."));
        }
        String normalizedIsoA3 = isoA3.trim().toUpperCase(Locale.ROOT);
        if (!countryRefService.isValidIsoA3(normalizedIsoA3)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "유효하지 않은 국가 코드입니다. 목록에서 국가를 선택해주세요."));
        }
        if (countryRepository.existsByIsoA3(normalizedIsoA3)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "이미 등록된 국가 코드입니다: " + normalizedIsoA3));
        }
        String normalizedIsoA2 = (isoA2 == null || isoA2.isBlank()) ? null : isoA2.trim().toUpperCase(Locale.ROOT);
        Country country = countryRepository.save(
                new Country(normalizedIsoA3, normalizedIsoA2, nameKo, nameEn, description));
        return ResponseEntity.ok(Map.of("success", true, "id", country.getId()));
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestParam(required = false) String isoA2,
            @RequestParam String nameKo,
            @RequestParam String nameEn,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean enabled) {
        Country country = findCountryOrThrow(id);
        country.setIsoA2((isoA2 == null || isoA2.isBlank()) ? null : isoA2.trim().toUpperCase(Locale.ROOT));
        country.setNameKo(nameKo);
        country.setNameEn(nameEn);
        country.setDescription(description);
        country.setEnabled(enabled);
        countryRepository.save(country);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Country country = findCountryOrThrow(id);
        countryRepository.delete(country);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Country findCountryOrThrow(Long id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "국가를 찾을 수 없습니다: " + id));
    }
}
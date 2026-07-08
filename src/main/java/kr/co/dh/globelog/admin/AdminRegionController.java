package kr.co.dh.globelog.admin;

import java.util.List;
import java.util.Map;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.Region;
import kr.co.dh.globelog.domain.RegionRepository;
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
@RequestMapping("/admin/regions")
public class AdminRegionController {

    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;

    public AdminRegionController(RegionRepository regionRepository, CountryRepository countryRepository) {
        this.regionRepository = regionRepository;
        this.countryRepository = countryRepository;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) Long countryId,
            @PageableDefault(size = 30, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        Page<Region> regions = countryId != null
                ? regionRepository.findByCountryId(countryId, pageable)
                : regionRepository.findAll(pageable);
        List<Country> countries = countryRepository.findAll(Sort.by("nameKo"));
        model.addAttribute("regions", regions);
        model.addAttribute("countries", countries);
        model.addAttribute("selectedCountryId", countryId);
        model.addAttribute("activeMenu", "regions");
        return "admin/regions/list";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public AdminRegionDetailResponse detail(@PathVariable Long id) {
        return AdminRegionDetailResponse.from(findRegionOrThrow(id));
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam Long countryId,
            @RequestParam String nameKo,
            @RequestParam(required = false) String nameEn,
            @RequestParam(required = false) String geojsonFeatureId,
            @RequestParam(required = false) Double centerLat,
            @RequestParam(required = false) Double centerLng) {
        Country country = countryRepository.findById(countryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "국가를 찾을 수 없습니다: " + countryId));
        Region region = regionRepository.save(new Region(country, nameKo,
                emptyToNull(nameEn), emptyToNull(geojsonFeatureId), centerLat, centerLng));
        return ResponseEntity.ok(Map.of("success", true, "id", region.getId()));
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestParam String nameKo,
            @RequestParam(required = false) String nameEn,
            @RequestParam(required = false) String geojsonFeatureId,
            @RequestParam(required = false) Double centerLat,
            @RequestParam(required = false) Double centerLng,
            @RequestParam(defaultValue = "false") boolean enabled) {
        Region region = findRegionOrThrow(id);
        region.setNameKo(nameKo);
        region.setNameEn(emptyToNull(nameEn));
        region.setGeojsonFeatureId(emptyToNull(geojsonFeatureId));
        region.setCenterLat(centerLat);
        region.setCenterLng(centerLng);
        region.setEnabled(enabled);
        regionRepository.save(region);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        regionRepository.delete(findRegionOrThrow(id));
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Region findRegionOrThrow(Long id) {
        return regionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지역을 찾을 수 없습니다: " + id));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
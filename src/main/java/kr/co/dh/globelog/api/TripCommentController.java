package kr.co.dh.globelog.api;

import java.util.List;
import java.util.Optional;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripComment;
import kr.co.dh.globelog.domain.TripCommentRepository;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/trips/{tripId}/comments")
public class TripCommentController {

    private static final int MAX_CONTENT_LENGTH = 500;

    private final TripRepository tripRepository;
    private final TripCommentRepository tripCommentRepository;
    private final TripVisibilityService tripVisibilityService;
    private final CurrentUserResolver currentUserResolver;

    public TripCommentController(TripRepository tripRepository, TripCommentRepository tripCommentRepository,
            TripVisibilityService tripVisibilityService, CurrentUserResolver currentUserResolver) {
        this.tripRepository = tripRepository;
        this.tripCommentRepository = tripCommentRepository;
        this.tripVisibilityService = tripVisibilityService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<TripCommentResponse> list(@PathVariable Long tripId, Authentication authentication) {
        findViewableTripOrThrow(tripId, authentication);
        Optional<User> viewer = currentUserResolver.resolve(authentication);
        return tripCommentRepository.findByTripIdOrderByCreatedAtAsc(tripId).stream()
                .map(comment -> TripCommentResponse.from(comment,
                        viewer.map(v -> v.getId().equals(comment.getUser().getId())).orElse(false)))
                .toList();
    }

    @PostMapping
    public TripCommentResponse create(@PathVariable Long tripId, @RequestBody TripCommentRequest request,
            Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        Trip trip = findViewableTripOrThrow(tripId, authentication);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글은 " + MAX_CONTENT_LENGTH + "자 이내로 작성해주세요.");
        }
        TripComment saved = tripCommentRepository.save(new TripComment(trip, viewer, content));
        return TripCommentResponse.from(saved, true);
    }

    // 댓글 작성자 본인 또는 게시글(여행) 주인이 삭제할 수 있음 — 자기 글에 달린 부적절한
    // 댓글을 글쓴이가 정리할 수 있어야 해서 소유권을 둘 중 하나로 판정한다.
    @DeleteMapping("/{commentId}")
    public void delete(@PathVariable Long tripId, @PathVariable Long commentId, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        TripComment comment = tripCommentRepository.findById(commentId)
                .filter(c -> c.getTrip().getId().equals(tripId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다: " + commentId));
        boolean isAuthor = comment.getUser().getId().equals(viewer.getId());
        boolean isTripOwner = comment.getTrip().getUser() != null
                && comment.getTrip().getUser().getId().equals(viewer.getId());
        if (!isAuthor && !isTripOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "삭제 권한이 없습니다.");
        }
        tripCommentRepository.delete(comment);
    }

    private Trip findViewableTripOrThrow(Long tripId, Authentication authentication) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다: " + tripId));
        if (!tripVisibilityService.canView(trip, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "볼 수 없는 여행입니다.");
        }
        return trip;
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}

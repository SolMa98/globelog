package kr.co.dh.globelog.chat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * "지금 이 방 화면을 실제로 보고 있는 사용자" 추적 — 하드 보안 경계가 아니라 순수 UX
 * 최적화용(이미 화면을 보고 있는 사람에게 굳이 브라우저 알림까지 중복으로 안 보내려는
 * 목적, ChatMessageService 참고). 서버 재시작하면 날아가도 무방한 휘발성 상태라 DB가
 * 아니라 메모리에 둔다. 같은 방을 여러 탭/기기로 동시에 열어둘 수 있어 참조 카운트로
 * 관리한다(하나만 닫아도 "안 보고 있음"으로 잘못 처리되지 않게).
 */
@Component
public class ChatPresenceService {

    private final Map<Long, Map<Long, Integer>> viewerCounts = new ConcurrentHashMap<>();
    private final Map<String, RoomUser> bySubscription = new ConcurrentHashMap<>();

    public synchronized void onSubscribe(String sessionId, String subscriptionId, Long roomId, Long userId) {
        bySubscription.put(key(sessionId, subscriptionId), new RoomUser(roomId, userId));
        viewerCounts.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).merge(userId, 1, Integer::sum);
    }

    public synchronized void onUnsubscribe(String sessionId, String subscriptionId) {
        remove(key(sessionId, subscriptionId));
    }

    // STOMP DISCONNECT(탭 닫기, 네트워크 끊김 등)는 개별 UNSUBSCRIBE 없이 바로 끊길 수
    // 있어서, 세션 전체가 끊겼을 때도 그 세션이 갖고 있던 구독을 전부 정리해야 한다.
    public synchronized void onSessionDisconnect(String sessionId) {
        String prefix = sessionId + ":";
        List<String> keys = bySubscription.keySet().stream().filter(k -> k.startsWith(prefix)).toList();
        keys.forEach(this::remove);
    }

    public boolean isViewing(Long roomId, Long userId) {
        Map<Long, Integer> counts = viewerCounts.get(roomId);
        return counts != null && counts.containsKey(userId);
    }

    private void remove(String subscriptionKey) {
        RoomUser roomUser = bySubscription.remove(subscriptionKey);
        if (roomUser == null) {
            return;
        }
        Map<Long, Integer> counts = viewerCounts.get(roomUser.roomId());
        if (counts == null) {
            return;
        }
        counts.computeIfPresent(roomUser.userId(), (userId, count) -> count > 1 ? count - 1 : null);
        if (counts.isEmpty()) {
            viewerCounts.remove(roomUser.roomId());
        }
    }

    private String key(String sessionId, String subscriptionId) {
        return sessionId + ":" + subscriptionId;
    }

    private record RoomUser(Long roomId, Long userId) {
    }
}

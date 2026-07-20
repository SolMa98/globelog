package kr.co.dh.globelog.push;

// 브라우저 PushSubscription.toJSON()과 동일한 모양: { endpoint, keys: { p256dh, auth } }
public record PushSubscribeRequest(String endpoint, Keys keys) {
    public record Keys(String p256dh, String auth) {
    }
}

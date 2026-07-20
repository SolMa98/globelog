package kr.co.dh.globelog.chat;

import java.util.List;

public record ChatGroupCreateRequest(String name, List<String> memberNicknames) {
}

package kr.co.dh.globelog.domain;

/**
 * DIRECT: 1:1 대화방(정확히 2명, 같은 두 사람 사이엔 항상 하나만 존재하도록 find-or-create).
 * GROUP: 여러 명이 참여하는 방(이름 필수, 멤버라면 누구나 초대 가능).
 * SELF: 본인만 있는 개인 채팅방("나와의 채팅", 사용자당 하나만 존재).
 */
public enum ChatRoomType {
    DIRECT, GROUP, SELF
}

package com.library.api.mapper;

import com.library.api.dto.MemberDTO;
import com.library.api.entity.Member;
import org.springframework.stereotype.Component;

@Component
public class MemberMapper {
    public MemberDTO toDTO(Member member) {
        return new MemberDTO(member.getId(), member.getName(), member.getEmail(),
                member.getMembershipDate(), member.isActive());
    }

    public Member toEntity(MemberDTO dto) {
        Member member = new Member();
        member.setId(dto.id());
        member.setName(dto.name());
        member.setEmail(dto.email());
        member.setMembershipDate(dto.membershipDate());
        if (dto.active() != null) {
            member.setActive(dto.active());
        }
        return member;
    }
}

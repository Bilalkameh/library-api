package com.library.api.service;

import com.library.api.dto.MemberDTO;
import com.library.api.entity.Member;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.mapper.MemberMapper;
import com.library.api.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final MemberRepository members;
    private final MemberMapper mapper;
    private final Clock clock;

    public MemberService(MemberRepository members, MemberMapper mapper, Clock clock) {
        this.members = members;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Member> getAll() {
        return members.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Member getById(Long id) {
        return members.findById(id).orElseThrow(() -> new ResourceNotFoundException("Member", id));
    }

    @Transactional
    public Member create(MemberDTO dto) {
        Member member = mapper.toEntity(dto);
        member.setId(null);
        member.setMembershipDate(dto.membershipDate() == null ? LocalDate.now(clock) : dto.membershipDate());
        member.setActive(dto.active() == null || dto.active());
        return members.saveAndFlush(member);
    }
}

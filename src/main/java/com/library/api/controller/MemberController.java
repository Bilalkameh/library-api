package com.library.api.controller;

import com.library.api.dto.MemberRequest;
import com.library.api.entity.Member;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.MemberRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
public class MemberController {
    private final MemberRepository members;

    public MemberController(MemberRepository members) {
        this.members = members;
    }

    @GetMapping
    public List<Member> getAll() {
        return members.findAll(Sort.by("id"));
    }

    @GetMapping("/{id}")
    public Member getById(@PathVariable Long id) {
        return members.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", id));
    }

    @PostMapping
    public ResponseEntity<Member> create(@Valid @RequestBody MemberRequest request) {
        Member member = new Member();
        member.setName(request.name());
        member.setEmail(request.email());
        member.setMembershipDate(request.membershipDate() == null
                ? LocalDate.now() : request.membershipDate());
        member.setActive(request.active() == null || request.active());
        Member saved = members.saveAndFlush(member);
        return ResponseEntity.created(URI.create("/members/" + saved.getId())).body(saved);
    }
}

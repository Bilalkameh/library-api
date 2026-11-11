package com.library.api.controller;

import com.library.api.dto.ApiError;
import com.library.api.dto.MemberDTO;
import com.library.api.mapper.MemberMapper;
import com.library.api.service.MemberService;
import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@Tag(name = "Members", description = "Member registration and directory")
@ApiResponse(responseCode = "500", description = "Unexpected server error",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
public class MemberController {
    private final MemberService members;
    private final MemberMapper mapper;

    public MemberController(MemberService members, MemberMapper mapper) {
        this.members = members;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List members in ascending ID order")
    @ApiResponse(responseCode = "200", description = "Members, including an empty list")
    public List<MemberDTO> getAll() {
        return members.getAll().stream().map(mapper::toDTO).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a member by ID")
    @ApiResponse(responseCode = "200", description = "Member details")
    @ApiResponse(responseCode = "400", description = "Malformed member ID",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Member not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public MemberDTO getById(@PathVariable Long id) {
        return mapper.toDTO(members.getById(id));
    }

    @PostMapping
    @Operation(summary = "Register a member", description = "Returns a generated ID and a Location header. Omitted membershipDate uses the server's local date; active defaults to true.")
    @ApiResponse(responseCode = "201", description = "Registered member; Location identifies /members/{id}")
    @ApiResponse(responseCode = "400", description = "Invalid member fields or malformed JSON",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already exists",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Request media type is not supported",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<MemberDTO> create(@Valid @RequestBody MemberDTO request) {
        MemberDTO saved = mapper.toDTO(members.create(request));
        return ResponseEntity.created(URI.create("/members/" + saved.id())).body(saved);
    }
}

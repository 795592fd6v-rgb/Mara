package com.mariageplus.service;

import com.mariageplus.dto.guest.CreateGuestRequest;
import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Wedding;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.GuestMapper;
import com.mariageplus.repository.GuestCategoryRepository;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock private GuestRepository guestRepository;
    @Mock private GuestCategoryRepository guestCategoryRepository;
    @Mock private GuestMapper guestMapper;
    @Mock private WeddingService weddingService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuditService auditService;

    @InjectMocks private GuestService guestService;

    private Wedding wedding;

    @BeforeEach
    void setUp() {
        wedding = Wedding.builder().organizationId(100L).build();
        wedding.setId(1L);
        lenient().when(guestMapper.toResponse(any(Guest.class))).thenReturn(GuestResponse.builder().build());
    }

    private CreateGuestRequest validRequest() {
        CreateGuestRequest req = new CreateGuestRequest();
        req.setFirstName("Jean");
        req.setLastName("Kabongo");
        return req;
    }

    @Test
    void create_AcceptsCategoryFromSameWedding() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.existsByIdAndWeddingId(7L, 1L)).thenReturn(true);
        when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateGuestRequest req = validRequest();
        req.setCategoryId(7L);
        guestService.create(1L, req);

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getCategoryId());
        assertEquals(0, captor.getValue().getAllowedCompanions());
    }

    @Test
    void create_RejectsCategoryFromAnotherWedding() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestCategoryRepository.existsByIdAndWeddingId(7L, 1L)).thenReturn(false);

        CreateGuestRequest req = validRequest();
        req.setCategoryId(7L);
        assertThrows(IllegalArgumentException.class, () -> guestService.create(1L, req));
        verify(guestRepository, never()).save(any(Guest.class));
    }

    @Test
    void create_RejectsDuplicateEmailWithinWedding() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestRepository.existsByEmailAndWeddingId("dup@example.com", 1L)).thenReturn(true);

        CreateGuestRequest req = validRequest();
        req.setEmail("dup@example.com");
        assertThrows(ConflictException.class, () -> guestService.create(1L, req));
    }

    @Test
    void getById_NotFound_Throws() {
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestRepository.findByIdAndWeddingId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> guestService.getById(1L, 99L));
    }

    @Test
    void delete_SoftDeletesGuest() {
        Guest guest = Guest.builder().weddingId(1L).build();
        guest.setId(5L);
        when(weddingService.loadInOrgScope(1L)).thenReturn(wedding);
        when(guestRepository.findByIdAndWeddingId(5L, 1L)).thenReturn(Optional.of(guest));
        when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

        guestService.delete(1L, 5L);

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
    }
}
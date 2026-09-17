package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateIncidentResourceRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentResourceBoardResponse;
import com.terrascope.platform.api.model.IncidentResourceRequest;
import com.terrascope.platform.api.model.UpdateIncidentResourceAllocationRequest;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentResourceCategory;
import com.terrascope.platform.domain.IncidentResourceStatus;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentResourceRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentResourceServiceTest {

    private IncidentResourceRepository resourceRepository;
    private IncidentResponseRepository incidentRepository;
    private IncidentResourceService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        resourceRepository =
                mock(
                        IncidentResourceRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new IncidentResourceService(
                        resourceRepository,
                        incidentRepository
                );

        incidentId =
                UUID.randomUUID();

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident(
                                IncidentStatus.RESPONDING
                        )
                )
        );
    }

    @Test
    void boardSummarizesShortagesAndFulfillment() {
        when(
                resourceRepository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        resource(
                                IncidentResourceStatus.REQUESTED,
                                20,
                                0
                        ),
                        resource(
                                IncidentResourceStatus.PARTIALLY_ALLOCATED,
                                10,
                                5
                        ),
                        resource(
                                IncidentResourceStatus.FULFILLED,
                                4,
                                4
                        ),
                        resource(
                                IncidentResourceStatus.CANCELLED,
                                8,
                                0
                        )
                )
        );

        IncidentResourceBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.totalCount()
        ).isEqualTo(4);

        assertThat(
                board.requestedCount()
        ).isEqualTo(1);

        assertThat(
                board.partiallyAllocatedCount()
        ).isEqualTo(1);

        assertThat(
                board.fulfilledCount()
        ).isEqualTo(1);

        assertThat(
                board.cancelledCount()
        ).isEqualTo(1);

        assertThat(
                board.shortageCount()
        ).isEqualTo(2);
    }

    @Test
    void createWritesResourceTimelineEvidence() {
        CreateIncidentResourceRequest request =
                new CreateIncidentResourceRequest(
                        "MEDICAL",
                        "Trauma kits",
                        "Field medical supply.",
                        "HIGH",
                        20,
                        "kits",
                        "Medical Alpha"
                );

        IncidentResourceRequest created =
                resource(
                        IncidentResourceStatus.REQUESTED,
                        20,
                        0
                );

        when(
                resourceRepository.create(
                        any(),
                        eq(incidentId),
                        eq(request),
                        eq(
                                IncidentResourceCategory.MEDICAL
                        ),
                        eq(
                                IncidentActionPriority.HIGH
                        ),
                        eq("Medical Alpha")
                )
        ).thenReturn(
                created
        );

        when(
                resourceRepository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        created
                )
        );

        service.create(
                incidentId,
                request
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("RESOURCE_REQUESTED"),
                any()
        );
    }

    @Test
    void allocationCannotExceedRequest() {
        IncidentResourceRequest resource =
                resource(
                        IncidentResourceStatus.REQUESTED,
                        10,
                        0
                );

        when(
                resourceRepository.findById(
                        incidentId,
                        resource.id()
                )
        ).thenReturn(
                Optional.of(
                        resource
                )
        );

        assertThatThrownBy(() ->
                service.updateAllocation(
                        incidentId,
                        resource.id(),
                        new UpdateIncidentResourceAllocationRequest(
                                11
                        )
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void partialAllocationWritesTimelineEvidence() {
        IncidentResourceRequest before =
                resource(
                        IncidentResourceStatus.REQUESTED,
                        20,
                        0
                );

        IncidentResourceRequest after =
                copyAllocation(
                        before,
                        IncidentResourceStatus.PARTIALLY_ALLOCATED,
                        8
                );

        when(
                resourceRepository.findById(
                        incidentId,
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                resourceRepository.updateAllocation(
                        incidentId,
                        before.id(),
                        8
                )
        ).thenReturn(
                after
        );

        when(
                resourceRepository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        after
                )
        );

        service.updateAllocation(
                incidentId,
                before.id(),
                new UpdateIncidentResourceAllocationRequest(
                        8
                )
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("RESOURCE_ALLOCATION_CHANGED"),
                any()
        );
    }

    private IncidentCase incident(
            IncidentStatus status
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentCase(
                incidentId,
                null,
                "source-1",
                "earthquakes",
                "Test incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.HIGH,
                status,
                "Command",
                0,
                now,
                now,
                null
        );
    }

    private IncidentResourceRequest resource(
            IncidentResourceStatus status,
            int requested,
            int allocated
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentResourceRequest(
                UUID.randomUUID(),
                incidentId,
                IncidentResourceCategory.MEDICAL,
                "Trauma kits",
                "",
                IncidentActionPriority.HIGH,
                status,
                requested,
                allocated,
                "kits",
                "Medical Alpha",
                now,
                now,
                status
                        == IncidentResourceStatus.FULFILLED
                        ? now
                        : null,
                status
                        == IncidentResourceStatus.CANCELLED
                        ? now
                        : null
        );
    }

    private IncidentResourceRequest copyAllocation(
            IncidentResourceRequest resource,
            IncidentResourceStatus status,
            int allocation
    ) {
        return new IncidentResourceRequest(
                resource.id(),
                resource.incidentId(),
                resource.category(),
                resource.name(),
                resource.description(),
                resource.priority(),
                status,
                resource.requestedQuantity(),
                allocation,
                resource.unit(),
                resource.owner(),
                resource.createdAt(),
                resource.updatedAt(),
                status
                        == IncidentResourceStatus.FULFILLED
                        ? resource.updatedAt()
                        : null,
                null
        );
    }
}

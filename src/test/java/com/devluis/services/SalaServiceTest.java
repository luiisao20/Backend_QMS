package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.WaitingRoomScreenDTO;
import com.devluis.entity.Consultorio;
import com.devluis.entity.Schedule;
import com.devluis.entity.Servicio;
import com.devluis.entity.Stablishment;
import com.devluis.entity.Turn;
import com.devluis.repository.BrandingRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.TurnStatus;

/**
 * The waiting-room board answers exactly one question for a patient sitting in
 * the room: "am I still in this?".
 *
 * That is why the state filter is asserted here, in the service, and not left
 * implicit in the JPQL: this project has no database in the test environment,
 * so a predicate written inside a {@code @Query} is unverifiable. Passing the
 * statuses in as a parameter moves the RULE to the only layer that can prove
 * it, and leaves the query as plain data access.
 */
@ExtendWith(MockitoExtension.class)
class SalaServiceTest {

  private static final Long SEDE_ID = 10L;

  @Mock
  private TurnRepository turnRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private BrandingRepository brandingRepository;

  @InjectMocks
  private SalaService salaService;

  // -- the rule: who belongs on the board -----------------------------------
  //
  // A turn already TREATED has left the room, and a CANCELLED one has no turn
  // left to wait for. Neither can be filtered out downstream if the query
  // brings them in, so the assertion that matters is which states are ASKED
  // for.

  @Test
  void getScreen_asksOnlyForTheStatesThatPutSomebodyInTheRoom() {
    when(stablishmentRepository.findById(SEDE_ID)).thenReturn(Optional.of(sede()));
    when(turnRepository.findBoardTurns(eq(SEDE_ID), any(LocalDate.class), any()))
        .thenReturn(List.of());

    salaService.getScreen("10");

    verify(turnRepository).findBoardTurns(
        eq(SEDE_ID),
        any(LocalDate.class),
        eq(Set.of(TurnStatus.TURN_IN_TREATMENT, TurnStatus.TURN_WAITNG)));
  }

  // -- the order the patient reads ------------------------------------------

  @Test
  void getScreen_listsTheCalledOnesFirst_thenTheOnesStillWaitingByTheirHour() {
    // Shuffled on purpose: the repository no longer orders, the service does.
    Turn calledEarlier = turn(1L, "I", 7, TurnStatus.TURN_IN_TREATMENT,
        LocalTime.of(14, 30), at(9, 0), "02");
    Turn waitingLate = turn(2L, "I", 9, TurnStatus.TURN_WAITNG,
        LocalTime.of(16, 30), null, null);
    Turn calledLast = turn(3L, "H", 9, TurnStatus.TURN_IN_TREATMENT,
        LocalTime.of(11, 0), at(10, 0), "03");
    Turn waitingSoon = turn(4L, "H", 4, TurnStatus.TURN_WAITNG,
        LocalTime.of(15, 0), null, null);

    WaitingRoomScreenDTO screen = screenWith(calledEarlier, waitingLate, calledLast, waitingSoon);

    // The big panel takes the most recent call.
    assertThat(screen.getCurrent().getTicket()).isEqualTo("H-009");
    assertThat(screen.getCurrent().getRoom()).isEqualTo("03");

    // And the column below it: the other call first, then the queue by hour.
    assertThat(screen.getHistory())
        .extracting(WaitingRoomScreenDTO.Call::getTicket)
        .containsExactly("I-007", "H-004", "I-009");
  }

  @Test
  void getScreen_keepsTheCurrentTurnOutOfTheColumn() {
    Turn onlyOneCalled = turn(1L, "I", 7, TurnStatus.TURN_IN_TREATMENT,
        LocalTime.of(14, 30), at(9, 0), "02");

    WaitingRoomScreenDTO screen = screenWith(onlyOneCalled);

    assertThat(screen.getCurrent().getTicket()).isEqualTo("I-007");
    assertThat(screen.getHistory()).isEmpty();
  }

  // -- the waiting row's shape ----------------------------------------------
  //
  // A null calledAt IS the contract the screen reads to tell a waiting turn
  // from a called one: with no consultorio to walk to, the room column stays
  // empty. Asserted here because the display styles those two rows apart.

  @Test
  void getScreen_shipsAWaitingTurnWithNoRoomAndNoCallTime() {
    Turn waiting = turn(1L, "I", 9, TurnStatus.TURN_WAITNG,
        LocalTime.of(16, 30), null, null);

    WaitingRoomScreenDTO screen = screenWith(waiting);

    // Nobody in a consultorio: the hero panel has nothing to show.
    assertThat(screen.getCurrent()).isNull();
    assertThat(screen.getHistory()).singleElement().satisfies(call -> {
      assertThat(call.getTicket()).isEqualTo("I-009");
      assertThat(call.getRoom()).isNull();
      assertThat(call.getCalledAt()).isNull();
    });
  }

  // -- helpers ---------------------------------------------------------------

  private WaitingRoomScreenDTO screenWith(Turn... turns) {
    when(stablishmentRepository.findById(SEDE_ID)).thenReturn(Optional.of(sede()));
    when(turnRepository.findBoardTurns(eq(SEDE_ID), any(LocalDate.class), any()))
        .thenReturn(List.of(turns));

    return salaService.getScreen("10");
  }

  private Stablishment sede() {
    return Stablishment.builder()
        .id(SEDE_ID)
        .name("DEMO Clinica Central")
        .address("Av. Amazonas N34-451")
        .build();
  }

  private Turn turn(long id, String prefix, int order, TurnStatus status,
      LocalTime hour, OffsetDateTime calledAt, String room) {
    Servicio service = Servicio.builder()
        .prefix(prefix)
        .name("DEMO - Limpieza Dental")
        .build();
    Schedule schedule = Schedule.builder()
        .hour(hour)
        .service(service)
        .build();

    return Turn.builder()
        .id(id)
        .order(order)
        .status(status)
        .calledAt(calledAt)
        .schedule(schedule)
        .consultorio(room == null
            ? null
            : Consultorio.builder().code(room).label("Consultorio " + room).build())
        .build();
  }

  private OffsetDateTime at(int hour, int minute) {
    return OffsetDateTime.of(LocalDate.now(), LocalTime.of(hour, minute), ZoneOffset.UTC);
  }
}

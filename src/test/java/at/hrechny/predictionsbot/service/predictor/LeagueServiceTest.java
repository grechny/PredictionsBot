package at.hrechny.predictionsbot.service.predictor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.LeagueEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.repository.CompetitionRepository;
import at.hrechny.predictionsbot.database.repository.LeagueRepository;
import at.hrechny.predictionsbot.database.repository.UserRepository;
import at.hrechny.predictionsbot.exception.InputValidationException;
import at.hrechny.predictionsbot.exception.LimitExceededException;
import at.hrechny.predictionsbot.model.LeagueRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

  private static final Long USER_ID = 42L;

  @Mock
  private LeagueRepository leagueRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private CompetitionRepository competitionRepository;

  private LeagueService leagueService;

  @BeforeEach
  void setUp() {
    leagueService = new LeagueService(leagueRepository, userRepository, competitionRepository);
  }

  @Test
  void createStoresAdminAsOnlyInitialMemberAndFiltersRequestedCompetitions() {
    var adminUser = user(USER_ID);
    var requestedCompetition = competition("Premier League");
    var ignoredCompetition = competition("Bundesliga");
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(adminUser));
    when(competitionRepository.findAll()).thenReturn(List.of(requestedCompetition, ignoredCompetition));
    when(leagueRepository.save(any(LeagueEntity.class))).thenAnswer(invocation -> {
      var league = invocation.getArgument(0, LeagueEntity.class);
      league.setId(UUID.randomUUID());
      return league;
    });

    var response = leagueService.create(USER_ID, leagueRequest("Office League", requestedCompetition.getId()));

    assertThat(response.getId()).isNotNull();

    var captor = ArgumentCaptor.forClass(LeagueEntity.class);
    verify(leagueRepository).save(captor.capture());
    var savedLeague = captor.getValue();
    assertThat(savedLeague.getName()).isEqualTo("Office League");
    assertThat(savedLeague.getAdminUser()).isEqualTo(adminUser);
    assertThat(savedLeague.getUsers()).containsExactly(adminUser);
    assertThat(savedLeague.getCompetitions()).containsExactly(requestedCompetition);
  }

  @Test
  void createRejectsBlankLeagueNameBeforeRepositorySave() {
    assertThatThrownBy(() -> leagueService.create(USER_ID, leagueRequest(" ", UUID.randomUUID())))
        .isInstanceOf(InputValidationException.class)
        .hasMessage("League name cannot be empty");

    verify(leagueRepository, never()).save(any());
  }

  @Test
  void createRejectsUserWhoAlreadyExceedsLeagueLimit() {
    var user = user(USER_ID);
    user.setLeagues(leagues(11));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> leagueService.create(USER_ID, leagueRequest("Office League", UUID.randomUUID())))
        .isInstanceOf(LimitExceededException.class)
        .hasMessage("User is already a member of another league");

    verify(leagueRepository, never()).save(any());
  }

  @Test
  void updateRejectsNonAdminUser() {
    var leagueId = UUID.randomUUID();
    var user = user(USER_ID);
    var admin = user(100L);
    var league = league("Office League", admin);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

    assertThatThrownBy(() -> leagueService.update(USER_ID, leagueId, leagueRequest("Updated League", UUID.randomUUID())))
        .isInstanceOf(InputValidationException.class)
        .hasMessage("User is not the admin of this league and cannot update it");
  }

  @Test
  void joinLeagueRejectsInvalidUuidString() {
    assertThatThrownBy(() -> leagueService.joinLeague("not-a-uuid", USER_ID))
        .isInstanceOf(InputValidationException.class)
        .hasMessage("Invalid league id");
  }

  @Test
  void joinLeagueRejectsUnknownLeagueId() {
    var leagueId = UUID.randomUUID();
    when(leagueRepository.findById(leagueId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> leagueService.joinLeague(leagueId.toString(), USER_ID))
        .isInstanceOf(InputValidationException.class)
        .hasMessage("Invalid league id");
  }

  @Test
  void joinLeagueRejectsExistingMember() {
    var leagueId = UUID.randomUUID();
    var user = user(USER_ID);
    var league = league("Office League", user);
    league.setUsers(new HashSet<>(Set.of(user)));
    when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

    assertThatThrownBy(() -> leagueService.joinLeague(leagueId.toString(), USER_ID))
        .isInstanceOf(InputValidationException.class)
        .hasMessage("User is already a member of this league");

    verify(userRepository, never()).findById(USER_ID);
    verify(leagueRepository, never()).save(any());
  }

  @Test
  void joinLeagueAddsUserAndSavesLeague() {
    var leagueId = UUID.randomUUID();
    var admin = user(100L);
    var joiningUser = user(USER_ID);
    var league = league("Office League", admin);
    league.setUsers(new HashSet<>(Set.of(admin)));
    when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(joiningUser));

    var response = leagueService.joinLeague(leagueId.toString(), USER_ID);

    assertThat(response).isEqualTo(league);
    assertThat(league.getUsers()).containsExactlyInAnyOrder(admin, joiningUser);
    verify(leagueRepository).save(league);
  }

  @Test
  void joinLeagueRejectsUserWhoAlreadyExceedsLeagueLimit() {
    var leagueId = UUID.randomUUID();
    var admin = user(100L);
    var joiningUser = user(USER_ID);
    joiningUser.setLeagues(leagues(11));
    var league = league("Office League", admin);
    when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(joiningUser));

    assertThatThrownBy(() -> leagueService.joinLeague(leagueId.toString(), USER_ID))
        .isInstanceOf(LimitExceededException.class)
        .hasMessage("User is already a member of another league");

    verify(leagueRepository, never()).save(any());
  }

  private LeagueRequest leagueRequest(String name, UUID competitionId) {
    var request = new LeagueRequest();
    request.setName(name);
    request.setCompetitions(List.of(competitionId));
    return request;
  }

  private UserEntity user(Long id) {
    var user = new UserEntity();
    user.setId(id);
    user.setLeagues(List.of());
    return user;
  }

  private CompetitionEntity competition(String name) {
    var competition = new CompetitionEntity();
    competition.setId(UUID.randomUUID());
    competition.setName(name);
    return competition;
  }

  private LeagueEntity league(String name, UserEntity admin) {
    var league = new LeagueEntity();
    league.setId(UUID.randomUUID());
    league.setName(name);
    league.setAdminUser(admin);
    league.setUsers(new HashSet<>(Set.of(admin)));
    return league;
  }

  private List<LeagueEntity> leagues(int count) {
    var leagues = new ArrayList<LeagueEntity>();
    for (int i = 0; i < count; i++) {
      leagues.add(league("League " + i, user(100L + i)));
    }
    return leagues;
  }
}

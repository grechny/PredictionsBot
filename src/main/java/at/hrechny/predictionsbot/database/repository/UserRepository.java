package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<UserEntity, Long> {

  Optional<UserEntity> findByIdAndActiveIsTrue(Long id);

  List<UserEntity> findAllByActiveIsTrue();

  @Query("select u from UserEntity u JOIN u.competitions c WHERE u.active = true and c.id = :competitionId")
  List<UserEntity> findAllActiveByCompetitionsId(UUID competitionId);

}

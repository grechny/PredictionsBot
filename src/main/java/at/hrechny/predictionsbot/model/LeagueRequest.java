package at.hrechny.predictionsbot.model;

import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class LeagueRequest {

  private Long userId;
  private String name;
  private List<UUID> competitions;

}

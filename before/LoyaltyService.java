

@Service
public class LoyaltyService {
  @Autowired JdbcTemplate jdbc;

  public int addPoints(Long customerId, int delta) {
    Integer points = jdbc.queryForObject(
      "select points from loyalty where customer_id = ?", Integer.class, customerId);
    int newValue = (points == null ? 0 : points) + delta;
    jdbc.update("merge into loyalty key(customer_id) values(?,?)", customerId, newValue);
    return newValue;
  }
}

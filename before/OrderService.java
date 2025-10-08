// package com.acme.shop;  // flat & tangled
@Service
public class OrderService {
  private final JdbcTemplate jdbc;
  private final RestTemplate rest;
  private final Environment env;

  public OrderService(JdbcTemplate jdbc, RestTemplate rest, Environment env) {
    this.jdbc = jdbc; this.rest = rest; this.env = env;
  }

  @Transactional
  public Long placeOrder(Long customerId, Long productId, int qty) {
    // Customer credit check (hard dependency)
    Integer credit = jdbc.queryForObject("select credit from customer where id = ?", Integer.class, customerId);
    if (credit == null || credit < 0) throw new IllegalStateException("No credit");

    // Inventory check (again, direct SQL)
    Integer stock = jdbc.queryForObject("select stock from inventory where product_id = ?", Integer.class, productId);
    if (stock == null || stock < qty) throw new IllegalStateException("OOS");

    // Business rule: launch discount behind env flag
    boolean launchDiscount = Boolean.parseBoolean(env.getProperty("flags.launchDiscount","false"));
    BigDecimal price = jdbc.queryForObject("select price from product where id = ?", BigDecimal.class, productId);
    BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
    if (launchDiscount && qty >= 3) total = total.multiply(new BigDecimal("0.9"));

    // Persist order (mixing domain + persistence)
    KeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(con -> {
      PreparedStatement ps = con.prepareStatement("insert into orders(customer_id, product_id, qty, total) values (?,?,?,?)",
        Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, customerId); ps.setLong(2, productId);
      ps.setInt(3, qty); ps.setBigDecimal(4, total);
      return ps;
    }, kh);
    Long orderId = kh.getKey().longValue();

    // Fire-and-forget “event” as HTTP call (tight coupling)
    rest.postForLocation("http://notify/api/order-created", Map.of("orderId", orderId));

    return orderId;
  }
}
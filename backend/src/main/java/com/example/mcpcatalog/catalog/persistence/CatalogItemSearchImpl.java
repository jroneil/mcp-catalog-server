package com.example.mcpcatalog.catalog.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class CatalogItemSearchImpl implements CatalogItemSearch {
    private final NamedParameterJdbcTemplate jdbc;

    public CatalogItemSearchImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SearchRows search(CatalogItemType type, Boolean active, BigDecimal maxPrice,
                             String text, int limit, long offset) {
        var parameters = new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
        var where = new StringBuilder(" FROM public.catalog_item WHERE TRUE");
        if (type != null) {
            where.append(" AND type = :type");
            parameters.addValue("type", type.name());
        }
        if (active != null) {
            where.append(" AND active = :active");
            parameters.addValue("active", active);
        }
        if (maxPrice != null) {
            where.append(" AND price <= :maxPrice");
            parameters.addValue("maxPrice", maxPrice);
        }
        if (text != null) {
            where.append(" AND (sku ILIKE :text ESCAPE '!' OR name ILIKE :text ESCAPE '!'"
                    + " OR description ILIKE :text ESCAPE '!')");
            parameters.addValue("text", "%" + text.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%");
        }
        long total = jdbc.queryForObject("SELECT count(*)" + where, parameters, Long.class);
        var items = jdbc.query("SELECT id, sku, name, type, description, price, active, created_at, updated_at"
                        + where + " ORDER BY id ASC LIMIT :limit OFFSET :offset", parameters,
                (row, index) -> new CatalogItem(row.getLong("id"), row.getString("sku"),
                        row.getString("name"), CatalogItemType.valueOf(row.getString("type")),
                        row.getString("description"), row.getBigDecimal("price"), row.getBoolean("active"),
                        row.getObject("created_at", OffsetDateTime.class), row.getObject("updated_at", OffsetDateTime.class)));
        return new SearchRows(items, total);
    }
}
